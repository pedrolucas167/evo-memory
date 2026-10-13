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

Como verificação de engenharia, a implementação passou em **68 testes locais**
na compilação limpa. Esse número não mede a qualidade da memória.

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
- A consulta final à OSV apontou avisos no Spring MVC relacionados a recursos
  de streaming e XSLT não encontrados nas rotas atuais. Isso não certifica
  ausência de vulnerabilidades; reexecute a consulta ao atualizar dependências.
