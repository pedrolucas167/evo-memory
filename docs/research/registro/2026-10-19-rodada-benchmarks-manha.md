# Dossiê: rodada de benchmarks da manhã de 19/10/2026

**Status: exploratório** (executado antes do protocolo; princípios aplicados
retroativamente na documentação). Referência no README em "Resultados
preliminares".

## Pergunta

A recuperação e o assistente do evo-memory mantêm acerto nos conjuntos de
benchmark existentes após as mudanças da semana (DTOs, sessões, testes)?

## Protocolo executado

Datasets versionados: `retrieval_v2.json` (mini, 6 casos), calibração (5 casos),
held-out (6 casos), `temporal_cases.json` (2 cenários × 3 repetições) e o
baseline de histórico completo com OpenRouter direto. Montagem descrita em
`setup-ambiente.md`; `RETRIEVAL_ATTENTION_WEIGHT` padrão (0.0),
`RETRIEVAL_MIN_SIMILARITY` padrão (0.38). Cada relatório com `run_id`,
`dataset_sha256` e estado do código em `evals/results/`.

## Dados (quatro rodadas do mini na manhã)

| Conjunto | Resultado |
|---|---|
| Mini R@5 (`profissao-por-situacao`) | 5/6 nas quatro rodadas |
| Calibração | 5/5 |
| Held-out | 6/6 |
| Temporal | 6/6 verificações |
| Baseline histórico completo | 6/6 verificações |

## Leitura honesta

O caso alvo falhou em todas as rodadas da manhã com corte padrão: a memória
correta entra no topo-2, mas distratores pegos pela margem entraram no
contexto e a resposta puxada do distrator. Estabilidade de 5/6 em quatro
rodadas elimina "ruído de rede" como causa: é sistemático.

## Limitações

Marcadores por substring; conversas sintéticas semeadas no mesmo segundo
(sinal temporal neutro); sem teste de significância (n pequeno);
o custo por consulta não foi medido nesta rodada.
