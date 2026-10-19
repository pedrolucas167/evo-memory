# Ficha da montagem (setup)

Fotografia do ambiente experimental vigente, no formato do princípio 7 do
[protocolo](protocolo.md). Toda mudança desta tabela invalida comparações
entre rodadas anteriores e entra no histórico abaixo.

## Montagem vigente (desde 19/10/2026)

| Componente | Valor |
|---|---|
| Plataforma | Windows 10+, Java 25 (25.0.4.1), Maven 3.10 |
| Aplicação | Spring Boot 3.5.16, Flyway, JPA |
| Banco | PostgreSQL 16.15 com pgvector, porta local 5433, Docker Compose |
| Autenticação local dos medidores | JWT HS256 assinado com `.local/auth-secret` (256 bits, git-ignored) |
| Provedor LLM | OpenRouter (`https://openrouter.ai/api/v1`), chave em variável de **Máquina** do Windows, nunca em arquivo |
| Modelo de chat / extração / juiz | `openai/gpt-6-luna` (padrões do `application.yml`, sobreponíveis por env) |
| Modelo de embeddings | `baai/bge-m3` (norma cosseno, dimensão servida pelo pgvector) |
| Configuração de recuperação | `min-similarity 0.38`, `relative-margin 0.05`, `top-k 10`, `judge-rerank true` (máx. 16 candidatos), `attention-rerank-weight 0.0`, `attention-rerank-ridge 0.1` |
| Rate limit da API | 120 req/min, 4 concorrentes, corpo máx. 64 KiB |
| Servidor reservado aos benchmarks | porta 8090/8091 (`SERVER_PORT`); a 8080 é de outra aplicação do usuário |

Cada relatório confirma a montagem lida do endpoint
`GET /api/experiments/{id}/configuration` no momento do run — a ficha desta
página descreve o padrão, o relatório registra o real.

## Histórico de mudanças de montagem

- **19/10/2026** — ficha criada. Rodadas anteriores a esta data usavam a
  mesma montagem descrita acima exceto pelos experimentos com
  `RETRIEVAL_ATTENTION_WEIGHT=1.0` e `RETRIEVAL_MIN_SIMILARITY=0.41`, que
  rodaram com esses overrides e estão citados nos respectivos dossiês.

## Costumes operacionais

- Semear memórias em usuários sintéticos (`temporal-*`), nunca reutilizar
  usuário entre rodadas.
- Derrubar apenas processos iniciados pelos benchmarks; Docker/WSL e a porta
  8080 pertencem ao usuário.
- Comparações só entre relatórios íntegros: nunca reexecutar partes de uma
  rodada e somar com a anterior — cada run produz um `run_id` e um relatório
  próprio; o dossiê cita os dois `run_id`.
