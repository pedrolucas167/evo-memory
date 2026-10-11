# Evolução do benchmark mini (R@5)

## Rodada 1 (calibração 0.42 / margin 0.08 / top-k 8)
- Resultado: 3/6
- Arquivo: benchmark_mini_1791688996.json

## Rodada 2 (calibração 0.38 / margin 0.05 / top-k 10)
- Resultado: 3/6 (sem mudança nos casos que falharam)
- Arquivo: benchmark_mini_1791689359.json
- Observação: os 3 casos falhos (profissão indireta, preferência indireta, plano indireto) não passam do limiar mesmo com limite reduzido. A memória esperada não está entre as 5 primeiras porque a pergunta não compartilha termos suficientes com o conteúdo da memória.

## Próxima evolução sugerida
- Adicionar expansão de sinônimos ao filtro léxico (ex.: "Revolução Francesa" → "história", "bairros" → "Lisboa").
- Ou usar um segundo embedding (cross-encoder) só para perguntas indiretas.
- Seguir registrando a cada mudança de limiar/modelo.
