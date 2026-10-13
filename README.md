# evo-memory

Assistente com memória evolutiva. Registra experiências (episódios), transforma-as em conhecimento reutilizável sobre o usuário (memória semântica) e versiona cada mudança, para que a compreensão sobre o usuário evolua sem apagar o histórico.

## Como funciona

1. **Extração:** cada mensagem gera memórias candidatas (interesse, preferência, projeto, decisão ou fato).
2. **Evolução:** se já existe memória com o mesmo assunto, um juiz via LLM decide entre confirmar, complementar ou contradizer. Cada decisão gera uma revisão com o motivo.
3. **Recuperação:** para responder, o sistema busca as memórias relevantes por significado (embeddings no pgvector) e por palavras, em vez de enviar todo o histórico.
4. **Resposta:** a LLM responde usando o contexto recuperado, sem listar as memórias.

## Stack

- Java 21, Spring Boot 3.5.16
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

O PostgreSQL do Docker usa a porta **5433** no host, limitada ao loopback. Se a porta estiver ocupada, ajuste `docker-compose.yml` e o perfil de datasource correspondente.

A chave do OpenRouter é lida da variável `OPENROUTER_API_KEY`. Não coloque a chave no `application.yml`.

## Endpoints

Todas as rotas `/api/**` exigem `Authorization: Bearer <access-token>`.
No perfil padrao `dev`, a API atende em `127.0.0.1` e gera uma chave local em
`.local/auth-secret`, ignorada pelo Git. Os scripts de benchmark usam essa chave
para assinar tokens HS256 de curta duracao, um por usuario sintetico. Nao ha
login nem dependencia de um provedor externo para pesquisa local. Quem possui
a chave pode emitir identidades de teste; esse modo nao e uma gestao de contas.

No perfil `prod`, o modo local e desabilitado e sao obrigatorias as variaveis
`AUTH_JWKS_URI`, `AUTH_ISSUER` e `AUTH_AUDIENCE`. Tokens RS256 sao verificados
com as chaves publicas do JWKS via HTTPS. Em ambos os modos, `sub` e `exp` sao
obrigatorios e o usuario da URL deve corresponder ao `sub`. Credenciais ausentes
ou invalidas retornam 401; acesso a outro usuario retorna 403. A tolerancia de
relogio e 60 segundos.

| Método | Caminho | Função |
|---|---|---|
| `POST` | `/api/conversations/{userId}` | Registra a conversa e extrai memórias, sem responder |
| `POST` | `/api/assistant/{userId}` | Aprende com a mensagem e responde usando o contexto |
| `POST` | `/api/memories/{userId}/reindex` | Gera embeddings das memórias ativas do usuário |
| `GET` | `/api/memories/{userId}/graph` | Consulta as memorias ativas |
| `PATCH` | `/api/memories/{userId}/{memoryId}` | Corrige explicitamente (`{"content":"..."}`) |
| `DELETE` | `/api/memories/{userId}/{memoryId}` | Esquece logicamente, preservando historico |
| `GET` | `/api/memories/{userId}/{memoryId}/history` | Consulta revisoes |
| `GET` | `/api/experiments/{userId}/configuration` | Consulta modelos e configuracao de retrieval, sem segredos |

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

Os scripts HTTP de retrieval, mini-benchmark e experimento temporal geram tokens
locais automaticamente apos a aplicacao criar a chave. Eles so enviam essas
credenciais para enderecos loopback. O modo institucional requer um cliente de
avaliacao com credenciais do provedor. Resultados locais ficam em `evals/results/`.

```powershell
# Extração: o modelo cria as memórias esperadas e não cria as proibidas
python evals/run_evals.py --model openai/gpt-6-luna

# Relevância do contexto: as memórias certas entram na resposta
python evals/run_retrieval_eval.py
python evals/run_retrieval_eval.py --cases evals/retrieval_heldout.json
python evals/run_retrieval_eval.py --cases evals/retrieval_v2.json

# Contradicao, correcao, esquecimento e baseline de historico completo
python evals/run_temporal_benchmark.py --repetitions 3
python evals/run_temporal_benchmark.py --system full-history --repetitions 3

# Auditoria OSV das dependencias usadas na ultima execucao de testes
python evals/audit_dependencies.py --details
```

Os resultados de recuperação foram validados assim:
- **Calibração** (`run_retrieval_eval.py` sem `--cases`): 5/5 — usado para ajustar `min-similarity` (0,42) e `relative-margin` (0,05).
- **Held-out** (`retrieval_heldout.json`): 6/6 — usado para confirmar que a margem 0,05 exclui falsos positivos sem perder relevantes.
- **v2** (`retrieval_v2.json`): 6/6 — tema novo (nadar/professor/filhos/romance/Lisboa), independente dos outros dois; mede se a recuperação generaliza.

Se você publicar resultados, use a mesma separação: calibração ajusta, held-out confirma, v2 valida independente.

Os valores acima sao resultados historicos. Na rodada de 2026-10-13, `retrieval_v2`
obteve **5/6**, com contexto irrelevante no caso de profissao. Os dois casos
temporais sinteticos passaram, e o baseline de historico completo tambem acertou
as respostas. Isso valida funcionamento, nao superioridade ou capacidade de
longo prazo. Consulte `evals/RESEARCH_PROTOCOL.md` antes de interpretar os numeros.

## Limites locais

A API limita o corpo a 64 KiB, a concorrencia a 4 e a taxa a 120 requisicoes por
minuto por identidade. Uma mensagem tem ate 10.000 caracteres; uma conversa
recebe ate 50 mensagens e 20.000 caracteres totais. Os limites operacionais sao
configuraveis por `API_MAX_BODY_BYTES`, `API_MAX_CONCURRENT` e
`API_REQUESTS_PER_MINUTE`. O limitador e por processo, nao uma quota distribuida
nem um teto financeiro de consumo. A reindexacao ainda percorre todas as
memorias ativas e precisa de paginacao antes de uso em grande escala.

## Modelo de dados

- `episode`: o que aconteceu (memória episódica).
- `semantic_memory`: o estado atual de cada memória, por `subject_key`, com confiança, status e embedding.
- `memory_revision`: histórico de cada mudança (criada, confirmada, complementada, contradita, corrigida, esquecida), com conteúdo e confiança antes e depois, origem e motivo.

Migrações em `src/main/resources/db/migration/`.
