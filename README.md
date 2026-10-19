# evo-memory

Projeto experimental para estudar memória de longo prazo em assistentes de IA,
com foco na consistência quando o usuário muda de preferência, corrige um fato
ou se contradiz ao longo das conversas.

O sistema registra experiências como **memória episódica**, extrai delas um
estado de **memória semântica** e preserva as revisões desse estado. A hipótese
de pesquisa é que atualizações explícitas e versionamento possam reduzir o uso
de informações obsoletas sob um orçamento de contexto controlado. Essa hipótese
ainda não foi demonstrada pelos experimentos atuais.

## Funcionamento

1. Uma conversa gera um episódio com um resumo das mensagens.
2. Uma LLM extrai candidatos a fatos, interesses, preferências, projetos,
   decisões, informações de saúde e planos.
3. Para um assunto já registrado, um juiz classifica a observação como
   confirmação, complemento ou contradição. Cada mudança gera uma revisão.
4. Para responder, o assistente combina busca vetorial com sobreposição lexical
   e sinônimos, filtrando memórias inativas e planos vencidos.
5. Correções explícitas atualizam o estado; o esquecimento o retira do contexto,
   preservando episódios e revisões.

As regras de evolução ficam em `domain/`, os casos de uso e contratos em
`application/` e os detalhes de HTTP, modelos e banco em `infrastructure/`.

## Atenção OLS na evolução de memória

A evolução de confirmação usa o mecanismo de atenção de mínimos quadrados
ordinários (OLS) de Goulet Coulombe: toda predição OLS é equivalente a um
módulo de atenção com pesos em forma fechada. Três vistas do mesmo resultado:

| Vista do paper | Fórmula | Papel no Evo Memory |
|---|---|---|
| Coeficientes | `ŷ_j = x_j β̂`, `β̂ = (X'X)⁻¹ X'y` | β̂ mapeia atributos da evidência à confiança esperada |
| Similaridade | `ŷ_j = Σᵢ ⟨F_j, F_i⟩ y_i` com `F = X U Λ^(-1/2)` | o quanto a evidência nova "olha" para cada memória armazenada, tomando suas confianças |
| Atenção | `ŷ_j = g(Q_j K' V)` com `g` = identidade | consulta = evidência nova; chaves = memórias ativas; valores = confianças; sem softmax |

O embedding espectral de `X'X = U Λ U'` é o **embedding ótimo**: é a única
métrica de similaridade por produto interno que minimiza o erro quadrático de
predição para todo vetor de resultados `y`.

Onde cada peça vive no código:

| Código | Responsabilidade |
|---|---|
| `domain/memory/OlsAttention.java` | mecanismo completo, Java puro: decomposição espectral por rotações de Jacobi, codificação ótima `F = X U Λ^(-1/2)`, ridge nos autovalores nulos, e as três vistas equivalentes (a classe valida internamente a identidade `x_j β̂ ≡ Σᵢ ⟨F_j, F_i⟩ y_i`) |
| `domain/memory/MemoryEvolution.java` | sobrecarga `evolve(..., attention)`: a leitura de atenção (limitada a [0, 1]) escala a taxa de reforço da confirmação de 0,1 para 0,1 + 0,1·Atenção; sem atenção, comportamento legado |
| `application/memory/MemoryService.java` | em observações de confirmação, `attentionReading` calcula `ŷ = Σᵢ wᵢ·yᵢ` com similaridades do `VectorStorePort.nearest` (pesos) e confianças das memórias ativas do usuário (valores); falha do índice devolve 0 |
| `application/MemoryEvolutionService.java` | ponte da aplicação para o domínio |

Desvios honestos do paper, documentados para experimentação futura: a porta
vetorial atual expõe apenas similaridades, então `MemoryService` usa o kernel
de cosseno como aproximação de `⟨F_j, F_i⟩`; a codificação espectral completa
(`OlsAttention`) está pronta e testada para quando houver acesso aos vetores
brutos `X`. O piso de ridge regulariza o `X'X` singular de poucas evidências,
o que diverge do OLS exato do paper. A influência observável está limitada: a
atenção só muda a taxa de reforço de confirmações (0,1 a 0,2 por evidência) e
nunca altera conteúdo nem classificação do juiz.

Verificação offline (benchmark determinístico, `OlsAttentionBenchmarkTest`,
executado no `mvn test` do CI, semente fixa):

| Verificação | Escala | Resultado |
|---|---|---|
| Estabilidade e consistência das vistas em escala | 300 evidências × 8 atributos | predição finita; coeficiente ≡ similaridade; ordem das linhas não altera predição |
| Custo computacional | a mesma consulta | ~2 ms por leitura (limite de segurança no CI: 10 s) |
| Colinearidade (colunas idênticas, `X'X` singular) | 50 evidências × 3 atributos recolhidos | piso de ridge mantém tudo finito |
| Varredura do reforço pela atenção | 201 leituras de atenção | confiança monótona na atenção, teto em 1,0 |

Esses números são verificação de engenharia do mecanismo, não resultados de
qualidade de memória. Os benchmarks HTTP (recuperação R@5, temporal e
baseline) seguem exigindo o servidor em execução e chave do OpenRouter e não
foram rerotulados nesta rodada offline.

## Resultados preliminares

Rodada local de **13 de outubro de 2026**, com dados sintéticos:

| Experimento | Amostra | Resultado observado |
|---|---|---|
| Recuperação independente, `retrieval_v2.json` | 6 casos | 5/6 aprovados |
| Evo Memory temporal | 2 cenários | Ambos passaram; 4/4 verificações de resposta |
| Baseline de histórico completo | Os mesmos 2 cenários | 4/4 verificações de resposta |

Nos cenários temporais, o Evo Memory também registrou a contradição, aceitou a
correção explícita e manteve o esquecimento lógico após uma nova observação com
a mesma chave de assunto. Na rodada temporal final, o servidor usou Spring
Boot 3.5.16, chat e extração `openai/gpt-6-luna` e embeddings `baai/bge-m3`.
O baseline utilizou o mesmo modelo de chat, sem extração nem busca vetorial.

O caso de recuperação que falhou trouxe informações de natação e filhos junto
da memória correta sobre profissão. O resultado atual de 5/6 não deve ser
substituído pelos resultados históricos de 6/6 de outras rodadas.

**Conclusão até aqui:** o fluxo funciona nos casos curtos testados, mas ainda
não mostrou vantagem sobre o histórico completo. Não há demonstração de ganho
de processamento, consistência em históricos longos ou superioridade
estatística. LongMemEval e LoCoMo ainda não foram replicados.

Como verificação de engenharia, a implementação passou em **78 testes locais**
na compilação limpa (incluindo a verificação de consistência das três vistas
e um benchmark offline determinístico do mecanismo de atenção OLS). Esse
número não mede a qualidade da memória.

## Limites da avaliação

- Os dois cenários temporais foram criados junto da implementação; são testes
  de funcionamento, não um conjunto independente de pesquisa.
- O baseline usa o mesmo modelo de resposta, mas prompts diferentes. Apenas
  as verificações de resposta atual e corrigida são comparáveis entre sistemas;
  o baseline não tem estado persistido para comparar auditoria ou esquecimento.
- A temperatura de resposta é 0,7. Checadores usam presença de termos, não
  avaliação semântica independente; mencionar a resposta correta pode contar
  como acerto mesmo em uma resposta imperfeita.
- As amostras são pequenas. Não foram produzidos intervalos de confiança,
  análise de significância ou comparação com sistemas externos de memória.
- Repetir os mesmos dois cenários não substitui variedade de usuários,
  históricos, distratores e mudanças sucessivas.

## Executar localmente

Requisitos: JDK 21 ou superior, Maven, Docker e Python 3 para os scripts de
avaliação. As chamadas ao OpenRouter exigem uma chave e podem gerar custos.

```powershell
docker compose up -d
$env:OPENROUTER_API_KEY = "sua-chave"
mvn spring-boot:run
```

O perfil padrão `dev` atende em `127.0.0.1:8080`. O PostgreSQL é publicado
somente no loopback, na porta 5433. O Spring Boot não carrega `.env`
automaticamente: exporte as variáveis para o processo Java ou configure-as na
IDE. O Docker Compose usa seu próprio arquivo de ambiente.

A API cria uma chave de teste em `.local/auth-secret`, ignorada pelo Git.
Os scripts assinam tokens locais de curta duração para usuários sintéticos
isolados e só enviam essas credenciais a endereços loopback. Quem possui essa
chave pode emitir identidades de teste; esse modo não é gestão de contas.

O perfil `prod` não aceita o provedor local e exige `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `AUTH_JWKS_URI`, `AUTH_ISSUER` e `AUTH_AUDIENCE`, além da
chave do modelo. Essa integração institucional não foi implantada nem avaliada
nos experimentos locais.

## Reproduzir avaliações

Com a aplicação em execução:

```powershell
# Recuperação: calibração e conjuntos separados
python evals/run_retrieval_eval.py
python evals/run_retrieval_eval.py --cases evals/retrieval_heldout.json
python evals/run_retrieval_eval.py --cases evals/retrieval_v2.json

# Memória temporal e baseline
python evals/run_temporal_benchmark.py --repetitions 3
python evals/run_temporal_benchmark.py --system full-history --repetitions 3

# Extração diretamente pelo modelo
python evals/run_evals.py --model openai/gpt-6-luna

# Testes de engenharia e consulta de dependências à OSV
mvn test
python evals/audit_dependencies.py --details
```

Nos scripts HTTP de recuperação e memória temporal, use
`--base http://127.0.0.1:8081` se a aplicação estiver em outra porta.
As três repetições acima são uma sugestão para novas rodadas, não a quantidade
executada nos resultados apresentados.

Os relatórios temporais registram dataset, hashes, revisão Git, estado do
checkout, configuração efetiva, verificações e latência por caso. Resultados
locais ficam em `evals/results/`, fora do Git. O cliente não repete escritas
que retornaram erro do servidor, pois elas podem já ter sido persistidas.

Use calibração para ajustar parâmetros e conjuntos separados para validar.
Não ajuste o algoritmo com os casos de validação e depois apresente o mesmo
conjunto como teste independente. Para publicar comparações, congele uma revisão
e controle modelo, prompt, orçamento de contexto e dados disponíveis.

Os modelos podem ser configurados por `CHAT_MODEL`, `EXTRACTION_MODEL`,
`JUDGE_MODEL` e `EMBEDDING_MODEL`. Os valores padrão de recuperação são
similaridade mínima 0,38, margem relativa 0,05 e top-k 10. O embedding persistido
tem 1.024 dimensões; trocar de modelo exige conferir essa dimensão e reindexar.

## API e persistência

Todas as rotas exigem um token Bearer válido, cujo usuário corresponda ao
usuário da URL. A API retorna 401 para credenciais inválidas e 403 para acesso
a outro usuário.

| Método | Caminho | Função |
|---|---|---|
| POST | `/api/conversations/{userId}` | Registrar episódio e extrair memórias |
| POST | `/api/assistant/{userId}` | Aprender e responder |
| GET | `/api/memories/{userId}/graph` | Consultar memórias ativas |
| PATCH | `/api/memories/{userId}/{memoryId}` | Corrigir com `{"content":"..."}` |
| DELETE | `/api/memories/{userId}/{memoryId}` | Esquecer logicamente |
| GET | `/api/memories/{userId}/{memoryId}/history` | Consultar revisões |
| POST | `/api/memories/{userId}/reindex` | Reindexar memórias ativas |
| GET | `/api/experiments/{userId}/configuration` | Consultar configuração sem segredos |

Conversa: `{"conversationId":"conv1","messages":["Moro em Curitiba."]}`.
Assistente: `{"conversationId":"conv1","message":"Qual é minha cidade atual?"}`.
A resposta traz `answer`, `learned` e `context`.

PostgreSQL com pgvector armazena `episode`, `semantic_memory` e
`memory_revision`; Flyway aplica as migrações em
`src/main/resources/db/migration/`.

## CI/CD

O projeto usa GitHub Actions para CI/CD. O workflow em `.github/workflows/ci.yml`
declara permissão mínima (`contents: read`, `security-events: write`) e executa
dois jobs:

- **build-and-test**: compila, roda os 78 testes unitários (incluindo o
  benchmark offline do mecanismo de atenção OLS) e, sobre o classpath testado,
  consulta a base pública da OSV (`python evals/audit_dependencies.py`).
  A auditoria cobre dependências do build, não certifica ausência de
  vulnerabilidades nem analisa alcance.
- **security-scan**: análise de sistema de arquivos com Trivy
  (versão fixada em `0.28.0`), severidade HIGH/CRITICAL e `ignore-unfixed`
  ativado. O relatório SARIF vai para o GitHub Security; por política do
  projeto (`exit-code: '0'`), avisos conhecidos com correção pendente no
  upstream (ex.: spring-webmvc) não derrubam o pipeline.

O pipeline é acionado em push para branches `main` e `develop`, e em pull
requests. Não há segredos no repositório nem no workflow: os jobs de CI não
acessam provedores de modelo.

## Limites operacionais e privacidade

O projeto é um ambiente de pesquisa, não uma solução de produção certificada.

- Esquecimento é lógico: episódios e revisões conservam conteúdo. Uma nova
  chave de assunto pode recriar um fato semelhante; não há garantia de
  apagamento físico ou esquecimento semântico completo.
- Mensagens e memórias são enviadas ao provedor externo. Use dados sintéticos;
  não coloque chaves, tokens ou informações pessoais nos arquivos públicos.
- Confiança é uma heurística, não uma probabilidade calibrada. O contador de
  versão semântica não implementa controle de concorrência de banco.
- A API limita corpo a 64 KiB, concorrência a 4 e taxa a 120 requisições por
  minuto por identidade. Aceita até 50 mensagens, com 10.000 caracteres por
  mensagem e 20.000 por conversa. Esses controles são por processo e não
  constituem quota financeira ou defesa distribuída.
- A reindexação ainda percorre todas as memórias ativas e precisa de paginação
  para uso em grande escala.
- A consulta à OSV em 19/10/2026 confirmou avisos no Spring MVC 6.2.19
  (`GHSA-j9f9-w8pj-32f8`, streaming de recursos, e `GHSA-pc63-qcmh-9cmg`,
  XSLT), sem rotas ativas afetadas nas auditorias anteriores. Isso não
  certifica ausência de vulnerabilidades; reexecute a consulta ao atualizar
  dependências.

## Referências de estudo

Materiais que fundamentam o mecanismo de atenção OLS e o contexto do projeto.

Atenção OLS (mecanismo implementado):

- Goulet Coulombe, P. (2026). **Ordinary Least Squares as an Attention
  Mechanism**. Aceito no NeurIPS 2026, trilha principal. Versão livre em
  arXiv: <https://arxiv.org/abs/2504.09663> (DOI:
  <https://doi.org/10.48550/arXiv.2504.09663>). A visão de similaridade
  (Σᵢ ⟨F_j, F_i⟩ y_i com F = XUΛ^(-1/2)) e a equivalência com o módulo de
  atenção Q-K-V orientam a integração descrita na seção acima.
- Vaswani, A. et al. (2017). **Attention Is All You Need**. NeurIPS 2017.
  <https://arxiv.org/abs/1706.03762>. Referência original de atenção Q-K-V e
  do softmax que o paper do OLS substitui pela identidade.

Memória de longo prazo e avaliação:

- Maharana, A., Lee, D.-H., Tulyakov, S., Bansal, M., Barbieri, F., Fang, Y.
  (2024). **Evaluating Very Long-Term Conversational Memory of LLM Agents**
  (dataset LoCoMo). ACL 2024. <https://arxiv.org/abs/2402.17753>. Benchmark
  de conversas longas citado nos limites da avaliação.
- Wu, D., Wang, H., Yu, W., Zhang, Y., Chang, K.-W., Yu, D. (2025).
  **LongMemEval: Benchmarking Chat Assistants on Long-Term Interactive
  Memory**. ICLR 2025. <https://arxiv.org/abs/2410.10813>. Benchmark de
  memória interativa de longo prazo citado nos limites da avaliação.

Estatística e métodos dos experimentos:

- Hastie, T., Tibshirani, R., Friedman, J. (2009). **The Elements of
  Statistical Learning**, 2.ª ed., Springer. Base para regressão, regularização
  tipo ridge e decomposição de matrizes usadas no `OlsAttention`.

Vídeos de estudo — atenção Q-K-V e projeções do OLS:

- 3Blue1Brown (2024). **Attention in transformers, step-by-step**
  (Deep Learning, capítulo 6). <https://www.youtube.com/watch?v=eMlx5fFNoYc>.
  Intuição geométrica de Queries, Keys e Values operando por produtos internos
  (similaridade) para ponderar valores — é exatamente a leitura de
  `⟨F_j, F_i⟩ y_i` da vista de similaridade implementada em `OlsAttention`.
- Umar Jamil. **Attention is all you need (Transformer) — Model explanation**.
  <https://www.youtube.com/watch?v=sznZ78HquPc>. Equações matriciais do módulo
  de atenção e projeções de vetores — útil para ver por que `g(Q_j K' V)` com
  `g` identidade (sem softmax) é o caso do paper.
- StatQuest with Josh Starmer. **Linear Regression, Clearly Explained!!!**
  <https://www.youtube.com/watch?v=7ArmBVF2dCs>. Intuição sobre OLS e ajuste
  por mínimos quadrados — o ponto de partida da vista de coeficientes.
- Gilbert Strang (MIT OpenCourseWare, MIT 18.06). **Projections onto
  Subspaces / Least Squares Approximation**.
  <https://www.youtube.com/watch?v=Y_Ac6KiQ1t0>. Demonstração geométrica
  rigorosa da matriz de projeção `X(X'X)⁻¹X'` — a fundação da equivalência
  entre as vistas de coeficiente e de similaridade, e do papel de
  `F = XUΛ^(-1/2)` como espaço ortogonal comparado por produto interno.
