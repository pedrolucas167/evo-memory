# Dossiê: sonda de fechamento explícito — frente 1.1

Status: execução concluída; consolidação numérica verificada. Identificação
dos braços parcialmente reconstruída; adoção operacional pendente de auditoria.
Não foram feitas novas chamadas ao provedor para produzir este documento.

Pré-registro: [frente 1.1](../prereg/2026-10-24-sonda-fechamento-explícito.md).

## Proveniência e ressalva de montagem

| Campo | Execução A presumida | Execução B presumida |
|---|---|---|
| Run | `bb05317f81864e8eb1822f722189f214` | `33ca8d87dded4f69be34080c9bc0d1b2` |
| Revisão registrada | `839b040eda4ec8938d9d02df895f5c0931df496d` | `7a59d56d2d07cfbc714618d19d37ecf9cacd99db` |
| Checkout sujo | false | false |
| Repetições | 5 | 5 |
| Observações por sistema | 30 | 30 |
| Duração total | 3343,672 s | 7212,750 s |
| SHA256 do relatório | `cc7fe379af00c66ed274ce1bb3b6238c874324d20f263660ca5eab879b96fa47` | `602d719d8fa42fce32d0de1ef3e9ba911def00223d77fc31617f8d6d4d4083d9` |

Arquivos locais: `evals/results/obsolescence_v2_<run>.json`, ignorados pelo Git.
Ambos registram o mesmo dataset SHA256
`ba6b2ece243b0d24525ca535becb302c56a13d24ca7a57192fc51dbd339089e9`
e o mesmo hash de fontes
`c8dba4ef4ea27fc18954e11fe23b029b85990a66610f20ce2b0fa168a1fa734c`.
As revisões diferem por documentação, sem diferença nesse conjunto de fontes.
Configurações registradas: Boot 3.5.16, chat/extração/juiz
`openai/gpt-6-luna`, embeddings `baai/bge-m3`, top-k 10, similaridade 0,38
e margem relativa 0,05.

**Lacuna de rastreabilidade:** o endpoint usado pelo medidor não incluiu
`MEMORY_SUBJECT_CLOSING`, a configuração de OLS ou todas as outras chaves.
A ordem dos arquivos é compatível com controle antes de tratamento, mas não
prova a montagem. Na inspeção posterior foi encontrado um processo PowerShell
com `MEMORY_SUBJECT_CLOSING=true`; isso corrobora o tratamento, mas não
certifica sozinho a configuração histórica de cada servidor. Não foi
recuperado nesta conferência um registro independente da chave false no
controle. Assim, os números abaixo são verificáveis, mas a atribuição causal
e a adoção ficam condicionadas à auditoria dos comandos/logs originais.
Não inferir configuração a partir do desempenho obtido.

## Contagens congeladas

Evo Memory, 30 observações por execução:

| Indicador | A presumida | B presumida |
|---|---|---|
| Estado correto (`state_correct`) | 11/30 | 27/30 |
| Resposta correta | 17 | 27 |
| Ambígua | 13 | 2 |
| Obsoleta intermediária | 0 | 0 |
| Obsoleta antiga | 0 | 0 |
| Falha | 0 | 1 |
| Consulta média | 6,61 s | 8,65 s |
| Cenário médio, incluindo ingestão | 109,26 s | 238,38 s |
| Chamadas LLM estimadas | 1860 | 1798 |

Estado correto por caso:

| Caso | A presumida | B presumida |
|---|---|---|
| cidade-abc | 5/5 | 5/5 |
| cafe-abc | 5/5 | 5/5 |
| cargo-abc | 1/5 | 5/5 |
| futebol-abc | 0/5 | 2/5 |
| gato-abc | 0/5 | 5/5 |
| instrumento-abc | 0/5 | 5/5 |

A única falha é `TimeoutError`, em futebol. Não há evidência neste relatório
para atribuí-la a falta de crédito. Ela permanece no denominador; não houve
repetição seletiva para substituí-la. A estimativa menor de chamadas em B
decorre da fórmula do medidor e da observação que falhou, não demonstra economia.

Baseline de histórico completo, mantido separado por execução:

| Indicador | Baseline de A | Baseline de B |
|---|---|---|
| Resposta correta | 24/30 | 24/30 |
| Ambígua | 6/30 | 6/30 |
| Obsoleta ou falha | 0 | 0 |
| Cenário médio | 2,18 s | 2,04 s |
| Tokens de entrada médios | 432 | 432 |
| Tokens de saída médios | 50 | 45 |

## Critérios pré-registrados e decisão

Sob a identificação A=controle/B=tratamento, o ganho primário é **+16/30**
(+53,3 pontos percentuais) e o aumento da soma de obsolescência e falhas é
**+1/30**. Numericamente, satisfaz os limites de ganho de pelo menos +3/30
e regressão de no máximo +2/30 definidos no pré-registro.

**Decisão desta consolidação: critérios numéricos satisfeitos condicionalmente;
adoção operacional pendente da verificação de montagem.** Nenhuma chave padrão
foi alterada. A exigência de rastreabilidade não é substituída pelo tamanho do
ganho. Não iniciar uma rodada extra para reparar retrospectivamente essa lacuna.

No braço B presumido, 27 respostas corretas contra 24 do histórico completo
são uma diferença descritiva, não superioridade estatística estabelecida.
Ambos mantêm zero respostas obsoletas: a hipótese original de redução de
obsolescência continua não demonstrada nesse regime. O ganho de estado não
é uma medida aplicável ao baseline sem grafo persistido.

## Limitações e fechamento

Seis histórias sintéticas repetidas, avaliador por marcadores, ordem sequencial,
oscilação de provedor e ausência de todas as chaves no snapshot impedem uma
conclusão causal forte. O pacote não isola extrator, juiz e fechamento nem
mede o efeito do OLS. As chamadas estimadas e os tokens do baseline não
permitem comparação monetária completa ou custo amortizado.

A execução terminou e o relatório final foi salvo às 19h45 locais. Na
conferência posterior não havia processo de benchmark ativo nem conexão
externa nos processos Java inspecionados. Não se consultou saldo ou faturamento
do OpenRouter. A próxima ação é auditar a montagem com evidência existente,
não consumir novos créditos. Instrumentação incremental e snapshot completo
de configuração são melhorias futuras, fora desta rodada congelada.
