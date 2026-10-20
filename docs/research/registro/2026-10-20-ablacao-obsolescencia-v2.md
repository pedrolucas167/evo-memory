# Dossiê: ablação v2 — regime de orçamento (A→B→C)

**Status: confirmatório — hipótese NÃO demonstrada; operacionalização
ENCERRADA por dois empates 0×0 consecutivos.** Registro pré-execução:
[`../prereg/2026-10-19-ablacao-obsolescencia-v2.md`](../prereg/2026-10-19-ablacao-obsolescencia-v2.md)
(commit `8fba82c`, anterior a qualquer observação).

| Metadado | Valor |
|---|---|
| `run_id` | `93dfcdd1b82b4f758882032c975a8eff` |
| `dataset_sha256` | `9385892ccc2fcd90a46b17b0700917c33756104bcdf3ad5fb90aa018efcc9d69` |
| Revision do código | `eb40e4d869b791f183347da6c5603cb87da87edc` |
| Repetições | 5 × 6 cenários × 2 braços (30 consultas por braço) |
| Duração | 3584,6 s (~1 h) |
| Modelos | chat/extração/juiz `gpt-6-luna`; embeddings `bge-m3` |
| Relatório bruto | `evals/results/obsolescence_v2_93dfcdd1...json` (git-ignored) |

**Nota de execução**: a primeira tentativa (19/10 ~18:57) foi interrompida
pela suspensão da máquina com até 18/60 consultas e nenhum dado parcial
restou (o medidor grava o relatório só no fim). Relançado do zero em
20/10 pela manhã, conforme a proibição de somar runs parciais. Nenhuma
mudança de montagem entre as tentativas (ficha intacta).

## Métrica de decisão (critério a priori)

| | evo-memory | histórico completo |
|---|---|---|
| Respostas obsoletas (intermediária + antiga) | **0/30** | **0/30** |
| Corretas | 19 | 25 |
| Ambíguas (estado atual + antecessor explicado) | 11 | 5 |
| Falhas | 0 | 0 |

**Decisão: empate 0×0 ⇒ segunda tentativa encerrada.** Com 30 turnos,
dupla correção encadeada e consulta sem pista de recência, o modelo de
chat forte (`gpt-6-luna`) não produziu resposta obsoleta em nenhum dos
desenhos testados. A premissa "assistentes usam informação obsoleta e o
versionamento corrige" não se sustenta como defeito dominante neste
regime — e lembrando o viés conservador declarado a priori: o system
prompt do baseline já pede explicitamente o fato mais recente.

## Custo (métrica secundária, reportada sem decisão)

| | evo-memory | histórico completo |
|---|---|---|
| Latência por caso (semeadura + consulta) | 117,1 s | 2,35 s |
| Latência só da consulta (evo) | 7,2 s | — |
| Chamadas LLM | ~1860 (estimada, 62/caso) | 30 exatas (1/caso) |
| Tokens (baseline) | — | 432 prompt / 42 completion (média) |

## Atribuição de mecanismo (diagnóstico pré-registrado)

`state_correct_rate` do braço evo: **11/30 (36,7%)** — em 19/30 o grafo
continha marcador A ou B. Decomposição por veredito:

| Grafo | correta | ambígua |
|---|---|---|
| ok (11) | 11 | 0 |
| residual (19) | 8 | 11 |

O gradiente é claro: **ambiguidade ⟺ resíduo no grafo**. O juiz de
relevância exclui o resíduo em 8 de 19 (resposta limpa mesmo com ruído
no grafo) e, quando o puxa (11/19), a resposta explica a transição.
Por caso: `cidade` e `cafe` limpos 5/5; `cargo` 1/5; `futebol`, `gato`,
`instrumento` 0/5.

## Diagnóstico exploratório (amostra única, sem decisão) — o flag contava certo como errado

Um caso (`gato-abc`) foi semeado do zero e o grafo revelou **quatro
memórias FACT coexistindo**:

1. `[A]` "A gata Mel morreu." — evento verdadeiro, extraído em separado;
2. `[B]` "Adotou um gato chamado Tobi, que fugiu." — evento passado;
3. `[-]` "Pretende dormir cedo hoje." — ruído legítimo de enchimento;
4. `[C,B]` "Adotou Nina para substituir Tobi." — estado atual que cita o
   antecessor no próprio texto.

Leitura honesta do flag `state=WRONG`: não é ausência de estado
obsoleto no sentido da hipótese — os marcadores residuais são
**eventos** (continuam verdadeiros independentemente do estado) e
**referências de transição** ("para substituir X"). Mas o diagnóstico
expõe um defeito novo real: o extrator produz **chaves de assunto por
nome de entidade** (assunto "Mel" ≠ assunto "Tobi" ≠ assunto "Nina"),
então o mecanismo de contradição por sujeito nunca encadeia a
descontinuidade real de estado — as trocas vivem como memórias
paralelas em vez de revisões sobre o mesmo sujeito.

## Limitações

Mesmas do pré-registro, acrescidas de: o flag de mecanismo varre todo o
grafo (inclui eventos e resumos episódicos) e não pode ser decomposto
posteriormente porque o medidor grava apenas o booleano — o diagnóstico
da amostra compensou, mas por única vez. `n` pequeno; sem teste de
significância; sem truncamento real de janela (estresse por densidade
de distratores e mudança encadeada, não por janela).

## O que fecha hoje

1. Hipótese central: não demonstrada em duas operacionalizações
   (conversa curta de 7 turnos e regime de 30 turnos com mudanças
   encadeadas); a próxima iteração exige desenho radicalmente diferente
   — truncamento real de janela ou regime de erro dominante distinto.
2. Custo real da camada evolutiva: ~18× de latência no regime de
   consulta única; registrado como barreira de produção.
3. Defeito mapeado e reprodutível: chaves de assunto paralelas sob
   troca de entidade nomeada — o próximo alvo de engenharia, com
   potencial de caso de reprodução determinístico (sem LLM
   probabilístico no caminho crítico).

**Ferramentas de diagnóstico usadas**: `evals/diagnostic_state_flag.py`
(semeadura e grafo) e `evals/analyze_v2_matrix.py` (decomposição do
relatório bruto).
