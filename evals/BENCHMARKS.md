# Benchmark — evo-memory

Método: avaliação própria com cenários estruturados, não benchmark acadêmico padronizado (LongMemEval/LocoMo ainda não replicado).

## O que foi medido

- **Extração** (`run_evals.py`): memórias criadas/proibidas por cena.
- **Relevância do contexto** (`run_retrieval_eval.py`): memórias certas entram no contexto usado pelo assistente; proibidas não entram.
- **Evolução** (`LlmChangeClassifier`): confirmado/complementado/contradito.

## Metodologia

1. Calibração: `evals/scenarios.json` + `retrieval_heldout.json` usado para ajustar `min-similarity=0.42`, `relative-margin=0.05`, `top-k=8`.
2. Held-out: `retrieval_heldout.json` (6 casos, temas não usados na calibração) — validou ajuste.
3. Independente (`v2`): `retrieval_v2.json` (6 casos, temas novos: nadar/professor/romance/Lisboa) — mede generalização.
4. Extração: `scenarios_health_plans.json` (alergia, plano, hipótese, cancelamento, CPF) — valida novos tipos `HEALTH`/`PLAN`.

## Resultados (última rodagem)

| Conjunto | Tamanho | Acerto | Observação |
|---|---|---|---|
| Extração (original) | 4 cenários | 4/4 | Sem regressão |
| Extração (saúde/planos) | 6 cenários | 6/6 | 0 violações, CPF proibido respeitado |
| Recuperação — calibração | 5 casos | 5/5 | Ajuste de limiar |
| Recuperação — held-out | 6 casos | 6/6 | Confirma limiar |
| Recuperação — v2 (indep.) | 6 casos | 6/6 | Generaliza |

Limites conhecidos:
- Nenhum baseline externo (Mem0, Zep, Supermemory) comparado.
- Nenhum estatístico de significância (n pequeno por caso).
- Não replicado com LongMemEval/LocoMo; resultados são internos.

## Reproduzir

```powershell
# Aplicação rodando (porta 8080)
python evals/run_retrieval_eval.py
python evals/run_retrieval_eval.py --cases evals/retrieval_heldout.json
python evals/run_retrieval_eval.py --cases evals/retrieval_v2.json
python evals/run_evals.py --model openai/gpt-6-luna --scenarios evals/scenarios_health_plans.json
```

## Critica honesta

- A extração depende do LLM (`gpt-6-luna`) e pode variar entre rodadas; os casos foram escritos junto com o ajuste do prompt, não são independentes.
- A recuperação usa embeddings (`baai/bge-m3`) com limiar calibrado em conjunto pequeno; similaridades variam por frase e podem falhar em domínios muito diferentes.
- Não há prova de que a evolução (judge LLM) é mais confiável que uma heurística simples; foi verificada apenas contra cenários escritos.

Próximo passo para ser acadêmico: replicar LongMemEval com `evals/benchmark/` adaptado ao formato de memórias do projeto.
