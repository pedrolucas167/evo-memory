# Dossiê: re-ranking espectral pela atenção OLS — resultado negativo

**Pergunta base do paper (arXiv:2504.09663)**: a vista de atenção de mínimos
quadrados, `ŷ = K(K + λI)·y` com Gram cosseno dos vetores das evidências,
separa a memória correta de distratores que a recuperação densa empata?

## Status

**Confirmatório-adversarial, refutado por medição** (executado antes do
protocolo; documentado sob os princípios atuais).

## Protocolo executado

`OlsRerank` (Java puro, Cholesky) sobre o topo dos matches de
`VectorStorePort.nearest`, com `vectors(ids)` portados do pgvector. Peso
`RETRIEVAL_ATTENTION_WEIGHT` variado entre 0 e 1 no servidor local,
`RETRIEVAL_Judge_MAX_CANDIDATES` não aplicável (experimento pré-juiz).
Benchmarks: mini ×3, calibração, held-out, temporal, baseline completo.

## Dados decisorios

1. **A/B do peso (weight=1 no servidor)**: caso alvo não melhora; demais
   casos estáveis. Implementação correta (6 testes de unidade verdes,
   equivalência tripla validada internamente pela classe).
2. **Geometria real das evidências**: cosseno da memória correta contra a
   pergunta = 0.4127; distratores = 0.4003 (paráfrase longa), 0.4169
   (reformulação curta), 0.3889 (variante "filhos"), 0.4267 (romance).
   Cosseno médio de linha do Gram ≈ 0.6 — evidências densas e correlatas.
3. **Varredura de ridge λ de 0.05 a 3.0**: nenhuma abertura de intervalo
   entre correta e distratores; λ grande faz "filhos" ultrapassar a correta.

## Leitura honesta

O defeito do caso não é de *ranking*: a memória correta já vem entre as duas
primeiras. É de *decisão de corte* — distratores ficam a ±0.04 da resposta
certa e o Gram denso não dá sinal discriminante para a forma dual explorar.
O teste unitário com distrator ortogonal não representa a geometria real do
modelo de embeddings (`baai/bge-m3`).

## Consequência

`RETRIEVAL_ATTENTION_WEIGHT` permanece **0.0 por padrão**, disponível para
experimentação. Duas consequências diretas: o segundo experimento (corte
absoluto) e a abertura para o modelo juiz.

## Limitações

Uma varredura λ em grade grosseira (10 pontos); sem busca de kernel além do
cosseno; sem teste de significância; custo computacional do re-ranqueamento
medido indiretamente (nenhuma alteração de latência observada, n pequeno).
