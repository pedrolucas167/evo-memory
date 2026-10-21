# Diagnóstico offline do recall — a morte da linha correta (2026-10-21)

**Natureza:** exploratório, sem uso de decisão, antecede o pré-registro da
frente A.2 (resolução de conflito no recall).
**Ferramental:** `evals/diagnose_recall_conflict.py` e
`evals/diagnose_recall_conflict_2.py` — replicação offline dos portões de
recall sem servidor: semântico (`baai/bge-m3` via OpenRouter, mesmas
chamadas do `OpenRouterEmbeddingClient`), léxico (regra de `words()` +
`SynonymExpander`) e juiz (mesmo SYSTEM_PROMPT, temperatura 0).
**Caso:** `gato-abc` do dossiê vivo 2026-10-21 — resposta "Tobi" quando o
esperado é "Nina"; pergunta "Qual o nome do meu gato?"; as duas linhas reais
do grafo com os 4 fillers da semeadura.

## Decomposição (números reais)

Portão semântico (cortes: `minSimilarity` 0,38 e margem relativa 0,05 contra
o melhor):

| candidato | cosine | destino |
|---|---|---|
| gato-tobi ("Adotou um gato chamado Tobi, que fugiu.") | 0,5854 | PASSA (melhor) |
| gata-nina ("A gata Mel morreu, e adotou uma gata chamada Nina.") | 0,5340 | **CAI por 0,0014** (corte 0,5354) |
| pai-bola | 0,4278 | CAI (margem) |
| plan-dormir | 0,4230 | CAI (margem) |
| projeto | 0,4192 | CAI (margem) |
| serie | 0,3585 | CAI (corte e margem) |

Portão léxico: overlap 1 ("gato") para a linha Tobi; **0 para a linha Nina**
(`gata` ≠ `gato`; não existe família de sinônimos pet no `SynonymExpander`).

Consequência mecânica (código): `semanticMatches` devolve só sobreviventes
da margem; com semântico ausente e léxico 0, o filtro de elegibilidade
(`semantic > 0 || lexical > 0`) exclui a linha Nina do `ranked` — **o juiz
nunca a vê** e o contexto fica com a linha Tobi. Conflito de estado não é
resolvido porque o estado correto não entra na disputa.

## Medidas que decidem o desenho

1. **O juiz, com os 6 candidatos (margem suspensa, só corte 0,38), escolhe
   `[gato-tobi, gata-nina]`** — mantém exatamente as duas linhas do caso,
   descartando os 4 fillers, com temperatura 0. A seleção correta já existe
   no pipeline: a margem relativa está cortando o candidato correto antes
   do juiz.
2. **Detetor de conflito por vizinhança de cosseno é inviável**: cos(Tobi,
   Nina) = 0,5642 fica ABAIXO de pares de fillers (cos(pai-bola, plan-dormir)
   = 0,5697; cos(plan-dormir, projeto) = 0,6078). Nenhum limiar separa
   "mesmo tópico" de "fillers próximos" nesse espaço — a ideia morreu na
   medição e não entra no pré-registro.

## Implicação de desenho (candidata a pré-registro)

Quando o juiz de re-ranking decide, a margem relativa deixa de proteger o
contexto final (o juiz protege) e passa a **reduzir cobertura** — neste caso,
eliminando o estado atual por 0,0014. Proposta operacional para o
experimento de decisão: geração de candidatos para o juiz com corte único de
`minSimilarity` (margem aplicada só ao caminho legado sem juiz), chave de
configuração para a ablação, `state_correct` do benchmark v2 como métrica.
Custo esperado igual: continua sendo 1 chamada de juiz por consulta.
