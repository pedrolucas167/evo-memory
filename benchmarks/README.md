# Benchmark de replicação — LongMemEval adaptado (esboço)

Status: **não replicado ainda**. Este arquivo documenta como seria, não executa.

## Por que ainda não

- LongMemEval precisa do dataset completo (c. 500 perguntas, não incluído no repo).
- O formato de memória do projeto é relacional (PostgreSQL + pgvector), não ChromaDB puro como MemPalace.
- Nenhum baseline externo (Mem0, Zep) está instalado para comparação.

## Como adaptar se quiser replicar

1. Baixar LongMemEval e extrair perguntas.
2. Mapear cada pergunta para uma sessão de `conversation` → extrair memórias via `ExtractionService` → inserir no banco (`user_id` = pergunta, `conversationId` = sessão de referência).
3. Consultar `/api/assistant/{user}` com a pergunta → coletar `context` retornado.
4. Verificar se a memória esperada aparece no contexto (R@5: top 5 memórias relevantes).
5. Comparar com baseline: rodar MemPalace (raw) e Mem0 no mesmo dataset, comparar R@5.

## O que falta para ser acadêmico

- Execução real com dataset.
- Comparação com pelo menos um baseline (Mem0 ou Zep).
- Estatística: n, média, desvio, teste de significância.
- Paper/replicação independente.

Não é possível reivindicar "96,6%" sem reproduzir o benchmark exatamente.
