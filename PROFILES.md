# Configuração de Profiles - Spring Boot

Este documento explica como funcionam os profiles do Spring Boot no projeto Evo Memory.

## Estrutura de Arquivos

```
src/main/resources/
├── application.yml              # Configurações base (comuns a todos os profiles)
├── application-dev.yml          # Configurações específicas de desenvolvimento
└── application-prod.yml         # Configurações específicas de produção
```

## Como Funciona

### 1. **application.yml (Base)**
Contém configurações que são compartilhadas entre todos os ambientes:
- Nome da aplicação
- Profile padrão (dev)
- Configurações JPA comuns
- Configurações Flyway
- Configurações de negócio (LLM, retrieval)

### 2. **application-dev.yml (Desenvolvimento)**
Sobrescreve configurações para ambiente local:
- Banco de dados local (localhost:5433)
- Logs em nivel INFO/WARN, sem corpos HTTP nem SQL detalhado
- API restrita ao loopback, com autenticacao local para benchmarks
- Chave de teste gerada em `.local/auth-secret`, ignorada pelo Git
- Credenciais padrão para desenvolvimento

### 3. **application-prod.yml (Produção)**
Sobrescreve configurações para ambiente de produção:
- Banco de dados via variáveis de ambiente
- Logs em nível INFO/WARN (menos verboso)
- Sem SQL em log (segurança e performance)
- Credenciais obrigatórias via variáveis de ambiente
- Autenticacao RS256 via JWKS; modo local indisponivel

## Como Ativar um Profile

### Via variável de ambiente (recomendado para produção):
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar evo-memory.jar
```

### Via argumento de linha de comando:
```bash
java -jar evo-memory.jar --spring.profiles.active=prod
```

### Arquivo .env e ambiente local:

O Spring Boot nao carrega `.env` automaticamente. Exporte as variaveis para o
processo Java ou configure-as na IDE. O Docker Compose usa `.env` para seu
proprio ambiente; isso nao exporta automaticamente a chave OpenRouter para Java.
O arquivo abaixo e apenas um exemplo de valores a configurar, sem credenciais reais:
```env
SPRING_PROFILES_ACTIVE=dev
DB_USER=evomemory
DB_PASSWORD=sua_senha
OPENROUTER_API_KEY=sua_chave
```

### No IDE (IntelliJ IDEA):
1. Run → Edit Configurations
2. Adicionar em "VM options": `-Dspring.profiles.active=dev`
3. Ou em "Environment variables": `SPRING_PROFILES_ACTIVE=dev`

## Hierarquia de Configuração

O Spring Boot mergeia as configurações nesta ordem:
1. `application.yml` (base)
2. `application-{profile}.yml` (sobrescreve base)
3. Variáveis de ambiente (sobrescreve tudo)
4. Argumentos de linha de comando (sobrescreve tudo)

## Exemplo Prático

### Configuração Base (application.yml):
```yaml
memory:
  llm:
    base-url: https://openrouter.ai/api/v1
```

### Configuração Dev (application-dev.yml):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/evomemory
```

### Configuração Prod (application-prod.yml):
```yaml
spring:
  datasource:
    url: ${DB_URL}  # Vem de variável de ambiente
```

### Resultado:
- **Dev:** Usa localhost + base-url do OpenRouter
- **Prod:** Usa DB_URL variável + base-url do OpenRouter

## Variáveis de Ambiente

### Obrigatórias em Produção:
- `DB_URL`: URL do banco de dados PostgreSQL
- `DB_USER`: Usuário do banco
- `DB_PASSWORD`: Senha do banco
- `OPENROUTER_API_KEY`: Chave da API OpenRouter
- `AUTH_JWKS_URI`: endereco HTTPS de chaves publicas
- `AUTH_ISSUER`: emissor esperado
- `AUTH_AUDIENCE`: audiencia esperada da API

### Opcionais em Desenvolvimento:
- `DB_USER`: Usa "evomemory" como padrão
- `DB_PASSWORD`: Usa "evomemory" como padrão
- `OPENROUTER_API_KEY`: Opcional (pode deixar vazio para testes)

## Boas Práticas

1. **Nunca** commitar credenciais reais no código
2. Usar variáveis de ambiente para dados sensíveis
3. Manter apenas placeholders em `application-prod.yml`; valores secretos ficam no ambiente
4. Usar valores padrão razoáveis em `application-dev.yml`
5. Documentar todas as variáveis de ambiente necessárias

## Criando Novos Profiles

Para adicionar um novo profile (ex: test):

1. Criar `application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
```

2. Ativar:
```bash
java -jar evo-memory.jar --spring.profiles.active=test
```

## Validação

Para verificar qual profile está ativo, adicione ao logger:
```java
@Value("${spring.profiles.active}")
private String activeProfile;

@PostConstruct
public void init() {
    log.info("Profile ativo: {}", activeProfile);
}
```
