# evo-memory

Assistente com memória evolutiva. Registra experiências (episódios), transforma-as em conhecimento reutilizável sobre o usuário (memória semântica) e versiona cada mudança, para que a compreensão sobre o usuário evolua sem apagar o histórico.

## Como funciona

1. **Extração:** cada mensagem gera memórias candidatas (interesse, preferência, projeto, decisão ou fato).
2. **Evolução:** se já existe memória com o mesmo assunto, um juiz via LLM decide entre confirmar, complementar ou contradizer. Cada decisão gera uma revisão com o motivo.
3. **Recuperação:** para responder, o sistema busca as memórias relevantes por significado (embeddings no pgvector) e por palavras, em vez de enviar todo o histórico.
4. **Resposta:** a LLM responde usando o contexto recuperado, sem listar as memórias.

## Stack

- Java 21, Spring Boot 3.3
- PostgreSQL 16 com pgvector (via Docker)
- Flyway (migrações)
- OpenRouter: modelos de chat (`openai/gpt-6-luna`) e embeddings (`baai/bge-m3`)

## Rodar

Pré-requisitos: Docker, JDK 21 ou superior e Maven.

```powershell
docker compose up -d
$env:OPENROUTER_API_KEY = "sua-chave"
mvn spring-boot:run
```

O PostgreSQL do Docker usa a porta **5433** no host, para não conflitar com um PostgreSQL local na 5432. Se a porta 5433 estiver ocupada, ajuste `docker-compose.yml` e `spring.datasource.url` no `application.yml`.

A chave do OpenRouter é lida da variável `OPENROUTER_API_KEY`. Não coloque a chave no `application.yml`.

## Endpoints

| Método | Caminho | Função |
|---|---|---|
| `POST` | `/api/conversations/{userId}` | Registra a conversa e extrai memórias, sem responder |
| `POST` | `/api/assistant/{userId}` | Aprende com a mensagem e responde usando o contexto |
| `POST` | `/api/memories/{userId}/reindex` | Gera embeddings das memórias ativas do usuário |

Corpo de `/api/conversations`:

```json
{"conversationId": "conv1", "messages": ["Estou estudando arquitetura de sistemas distribuídos."]}
```

Corpo de `/api/assistant`:

```json
{"conversationId": "conv1", "message": "Quero revisar consistência."}
```

A resposta do assistente traz `answer`, as memórias aprendidas com a mensagem (`learned`) e as usadas como contexto (`context`).

No PowerShell, use `curl.exe` (não o alias `curl`) e envie o corpo com UTF-8 para preservar acentos.

## Testes

```powershell
mvn test
```

Os testes unitários e de controller não exigem banco nem chave de API.

## Avaliações

Os scripts em `evals/` exigem a aplicação rodando (para os de relevância) ou a variável `OPENROUTER_API_KEY` (para os de extração).

```powershell
# Extração: o modelo cria as memórias esperadas e não cria as proibidas
python evals/run_evals.py --model openai/gpt-6-luna

# Relevância do contexto: as memórias certas entram na resposta
python evals/run_retrieval_eval.py
python evals/run_retrieval_eval.py --cases evals/retrieval_heldout.json
python evals/run_retrieval_eval.py --cases evals/retrieval_v2.json
```

Os resultados de recuperação foram validados assim:
- **Calibração** (`run_retrieval_eval.py` sem `--cases`): 5/5 — usado para ajustar `min-similarity` (0,42) e `relative-margin` (0,05).
- **Held-out** (`retrieval_heldout.json`): 6/6 — usado para confirmar que a margem 0,05 exclui falsos positivos sem perder relevantes.
- **v2** (`retrieval_v2.json`): 6/6 — tema novo (nadar/professor/filhos/romance/Lisboa), independente dos outros dois; mede se a recuperação generaliza.

Se você publicar resultados, use a mesma separação: calibração ajusta, held-out confirma, v2 valida independente.

## Modelo de dados

- `episode`: o que aconteceu (memória episódica).
- `semantic_memory`: o estado atual de cada memória, por `subject_key`, com confiança, status e embedding.
- `memory_revision`: histórico de cada mudança (criada, confirmada, complementada, contradita, corrigida, esquecida), com conteúdo e confiança antes e depois, origem e motivo.

Migrações em `src/main/resources/db/migration/`.
