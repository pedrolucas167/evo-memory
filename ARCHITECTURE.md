# Arquitetura do Evo Memory

## Estado atual

Monolito modular em camadas com ports and adapters. O dominio e independente
de Spring, JPA e HTTP. Os servicos de aplicacao usam modelos de dominio e ports;
transacoes e injecao ainda usam Spring. A fronteira dominio/aplicacao versus
infraestrutura e verificada por um teste de arquitetura.

## Pacotes

- `domain/memory`: Memory, Episode, MemoryRevisionDomain, candidatos, enums e
  transicoes puras em MemoryEvolution.
- `application/memory`: registro de episodios, extracao e ciclo de vida.
- `application/assistant`: recuperacao hibrida e composicao da resposta.
- `application/auth`: caso de uso de autenticacao.
- `application/model`: modelos de resultado, sem dependencia de HTTP ou JPA.
- `application/ports`: LLM, embeddings, autenticacao, persistencia, vetor,
  classificacao de mudancas e casos de uso de conversacao/recall.
- `infrastructure/persistence`: entidades JPA, repositories, PgMemoryAdapter e
  PgMemoryVectorAdapter. O adapter converte entidades persistidas em dominio.
- `infrastructure/llm`: HTTP OpenRouter, clientes, adapter de chat e juizes.
- `infrastructure/auth`: implementacao do contrato de autenticacao.
- `infrastructure/web`: controllers, DTOs de entrada, autenticacao/autorizacao,
  limites HTTP e mapeamento de erros.
- `config`: composicao e configuracoes operacionais.

Nao existe uma segunda pasta de servicos `memory` na raiz dos pacotes. Os
servicos foram movidos para application; a pasta vazia anterior foi removida.
Os pacotes de testes acompanham os pacotes de producao.

## Fluxo de escrita

Controller -> ConversationService -> ExtractionService -> MemoryService.
A extracao produz candidatos e tenta reutilizar a subject_key existente.
MemoryService consulta MemoryStorePort e solicita uma decisao a ChangeClassifier.
MemoryEvolutionService chama a regra pura; MemoryService persiste o novo estado,
a revisao com antes/depois e o episodio de origem. O vetor e atualizado depois
do flush, via VectorStorePort. Falha de embedding nao impede o registro da memoria.

## Regras preservadas

Confirmacao aumenta evidencias e atualiza confianca por
`c' = min(1, c + 0.1 * (1 - c))`. Complemento preserva a maior confianca;
contradicao substitui conteudo e confianca. Revisoes reiniciam a contagem de
evidencias. Correcao explicita fixa confianca em 1 e pode reativar uma memoria.
Esquecimento e logico, conserva historico e impede reativacao por observacao da
mesma subject_key. Uma nova chave que represente o mesmo fato ainda pode escapar
dessa regra; isso exige avaliacao adversarial.

A classificacao via LLM tem fallback textual. Confianca e uma heuristica,
nao uma probabilidade calibrada. O versionamento atual nao e controle otimista
de concorrencia de banco; escrita simultanea da mesma memoria requer protecao
adicional antes de uso concorrente intenso.

## Recuperacao

AssistantService depende de ConversationPort, MemoryRecallPort, LlmPort,
EmbeddingPort e VectorStorePort. Seleciona memorias ativas por similaridade e
sobreposicao lexical com sinonimos. A mensagem atual e aprendida primeiro;
memorias novas dessa mensagem sao excluidas do contexto para evitar repeticao.
Planos vencidos sao filtrados, com fallback de 90 dias sem confirmacao.

As regras e limiares foram preservados nesta reorganizacao. A camada de
aplicacao nao importa entidades JPA nem DTOs de controllers. A API manteve os
campos answer, learned e context.

## Operacao

A autenticacao e os limites operacionais sao tratados na proxima mudanca de
seguranca. Esta etapa mantem a interface existente durante a reorganizacao.

## Pesquisa

A arquitetura permite substituir adapters e testar regras sem banco, mas nao
demonstra ganho de memoria ou de processamento. O protocolo experimental e os
limites dos resultados estao em `evals/RESEARCH_PROTOCOL.md`.
