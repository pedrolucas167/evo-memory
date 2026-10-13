# Revisao de seguranca - 2026-10-13

## Escopo

Revisao de codigo/configuracao, consulta OSV do classpath Maven incluindo
bibliotecas de testes, verificacao pontual dos avisos e testes locais.
Nao e teste de invasao, certificacao institucional ou garantia de ausencia de
vulnerabilidades. A analise OSV nao determina explorabilidade.

## Correcoes aplicadas

- Rotas /api exigem identidade verificada e propriedade do usuario da URL.
  Ausencia/token invalido resulta em 401; acesso entre usuarios em 403.
- JWT institucional usa Nimbus JOSE JWT 10.9, RS256, JWKS HTTPS, emissor,
  audiencia, exp obrigatoria, nbf quando presente e subject nao vazio.
- O modo local usa HS256 e uma chave aleatoria de 256 bits em .local/auth-secret.
  E limitado ao loopback e nao e registrado no perfil prod. Quem possui a chave
  pode criar identidades sinteticas: nao e gestao institucional de contas.
- Limites locais: corpo 64 KiB, 4 requisicoes simultaneas, 120 por minuto por
  identidade; validacao de mensagens e tamanhos antes dos casos de uso.
- Erros do provedor e do parser nao incluem respostas brutas. Fallbacks e avisos
  de extracao nao registram conteudo de memoria. Logging web detalhado foi
  desativado no perfil dev.
- PostgreSQL publicado somente em 127.0.0.1; credenciais de desenvolvimento
  continuam exclusivas para ambiente local.
- Excecoes de recurso inexistente usam 404 sem expor dados; falha conhecida do
  provedor usa resposta 502 generica.
- Correcao, esquecimento e historico verificam propriedade tambem no caso de uso.

## Dependencias

A stack passou de Spring Boot 3.3.5 para 3.5.16. A consulta inicial OSV encontrou
17 pacotes com avisos em 98 dependencias de teste/runtime. A atualizacao do BOM
reduziu a seis pacotes. Foram aplicadas correcoes compativeis: Jackson BOM
2.21.7, Tomcat 10.1.60, PostgreSQL JDBC 42.7.12 e Log4j API 2.25.5.
A consulta subsequente encontrou um pacote com avisos em 95 dependencias.

Os avisos restantes sao no Spring MVC 6.2.19:

- GHSA-j9f9-w8pj-32f8: renderizacao de fragments em streams SSE.
- GHSA-pc63-qcmh-9cmg: limitacao de caminhos em XsltView.

A API atual usa controllers REST JSON, sem SseEmitter, fragment rendering,
XsltView ou configuracao de views XSLT encontrada no codigo. A aplicabilidade
na configuracao atual nao foi demonstrada. Nao houve supressao permanente
desses avisos; mudancas de rotas e views exigem nova revisao. Nao atualizar o
Spring Framework isoladamente para uma major incompativel apenas para limpar
o relatorio.

Reexecutar: `python evals/audit_dependencies.py --details`, depois de `mvn test`.
O relatorio local fica em target/security, ignorado pelo Git. Consulta somente
nomes e versoes publicas; nao envia codigo ou credenciais. As contagens nao
representam o numero de falhas exploraveis da aplicacao.

Fontes: https://api.osv.dev/ e https://tomcat.apache.org/security-10.html.
As respostas OSV completas dos avisos sao salvas com --details.

## Repositorio publico

O .gitignore cobre .env e variantes, .local, chaves/certificados privados comuns,
keystores, logs, dumps, backups, configuracoes locais e resultados de avaliacao.
Templates .env.example/.env.template podem ser rastreados sem valores reais.

A varredura limitada dos 22 commits procurou formatos de chave OpenRouter e
cabecalhos de chave privada e nao encontrou correspondencias. Tambem nao houve
historico rastreado de .env ou dos caminhos de credenciais pesquisados.
Outros tipos de segredo e dados pessoais exigem revisao adicional. Ignorar
arquivos nao limpa historico ja publicado; se um segredo for encontrado, revogar
antes de considerar reescrita de historico. Nenhum historico foi reescrito.

## Riscos e limitacoes abertos

- Limites sao por processo e janela fixa; nao substituem quota financeira ou
  defesa distribuida. Reindexacao ainda percorre todas as memorias.
- O contador de versao semantica nao e locking otimista de banco; escritas
  simultaneas da mesma memoria ainda podem concorrer.
- Esquecimento e logico: revisoes e episodios conservam conteudo. Nao e
  apagamento fisico. Uma subject_key nova pode recriar um fato semelhante.
- Mensagens e memorias sao enviadas ao provedor externo de modelos. Requisitos
  de consentimento, finalidade e retencao precisam ser definidos antes de dados reais.
- Regras de nao reter dados sensiveis dependem em parte do prompt; faltam
  avaliacao adversarial e controles deterministas de retencao.
- Rede de producao, TLS da API, backups, permissoes dos arquivos locais e
  politicas institucionais nao foram auditados.
- O perfil prod requer configuracao institucional e nunca foi implantado aqui.

As consultas JDBC examinadas usam parametros; isso nao elimina riscos de
autorizacao, memoria maliciosa ou outros problemas nao cobertos pela revisao.
