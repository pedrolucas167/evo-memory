# Dossiê: filtro de relevância pelo modelo juiz — resultado positivo

**Pergunta**: um juiz LLM (1 chamada por consulta) selecionando candidatos
antes do corte de contexto fecha o caso `profissao-por-situacao` sem
regredir os demais conjuntos?

## Status

**Exploratório-adversarial, confirmado por medição** (executado antes do
protocolo; posteriormente formalizado no § "terceiro experimento" do README).

## Protocolo executado

`JudgeReranker` (temperatura 0, JSON `{"relevant": [números]}`, parser
estrito a números do intervalo, degradação para a ordenação por score em
resposta ilegível). Servidor local `RETRIEVAL_JUDGE_RERANK=true`, demais
chaves de montagem padrão (ficha). Mini ×3, calibração, held-out, temporal
×1. Suíte de testes 100/100 antes do run HTTP.

## Dados

| Experimento | legado (manhã, 4 rodadas) | juiz ligado |
|---|---|---|
| Mini R@5 (`profissao-por-situacao`) | 5/6 em todas | **6/6, 6/6, 6/6** |
| Calibração | 5/5 | 5/5 |
| Held-out | 6/6 | 6/6 |
| Temporal (sanidade, 1 repetição) | 6/6 verificações | 2/2 verificações |

## Leitura honesta

Primeira configuração a fechar o caso alvo com zero regressão, em três
rodadas consecutivas — as anteriores oscilavam 5/6↔6/6 pela redação da
extração, o juiz consome a variabilidade porque decide por composição de
significado, não por similaridade calibrada. Mecanismos de segurança
validados por testes: degradação em falha, teto `topK` preservado, parsing
imune a injeção (só números do intervalo).

## Consequência

Padrão do projeto **ligado** (`RETRIEVAL_JUDGE_RERANK=true`) por medição
(decisão registrada no README; a pergunta ao usuário ficou sem resposta e o
critério "medir ≥ decidir" decidiu). Custo declarado: 1 chamada LLM por
consulta quando ≥2 candidatos — a medição formal de custo/latência deste
comportamento entra na ablação da hipótese central.

## Limitações

Sem teste de significância; temporal executado 1 repetição como sanidade
(2 cenários); variabilidade do juiz entre runs não quantificada além das
três rodadas do mini; injeção via memória só pior-caso analisado
qualitativamente.
