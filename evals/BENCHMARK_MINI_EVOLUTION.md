# Evolução do benchmark mini — sinônimo lexical testado

## Rodada 3 (sinônimo lexical + limiar 0.38 / top-k 10)
- Resultado: 3/6 (sem ganho)
- Arquivo: benchmark_mini_1791689943.json
- Observação: sinônimos adicionados (violao→violino, professor→historia, etc.) não alteraram R@5 porque o problema dos 3 casos falhos é a **semântica** (similaridade < 0.42), não só a sobreposição lexical. O filtro léxico só desempata entre candidatos que já passaram do limiar.
- Próximo passo real: melhorar o embedding (ex.: usar `openai/text-embedding-3-small` para português) ou baixar o limiar específicamente para perguntas com palavras-função (não recomendado, pois aumenta falsos positivos).
