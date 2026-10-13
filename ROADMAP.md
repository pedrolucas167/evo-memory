# Roadmap - Evo Memory

## Implementado e validado

1. Preparacao do repositorio publico: regras de exclusao ampliadas para segredos,
   identidade local, logs, dumps e resultados; varredura limitada dos 22 commits.
   Nenhum padrao de chave OpenRouter ou cabecalho de chave privada foi encontrado.
   Isso nao certifica ausencia de segredos nem substitui revisao de dados.
2. Ports e adapters: persistencia e busca vetorial isoladas; modelos puros de
   memoria, episodio e revisao; servicos em application; testes de fronteira.
3. Autenticacao simples para pesquisa: chave local, tokens por usuario sintetico,
   bind loopback e modo institucional opcional. O modo local e excluido de prod.
4. Controles de seguranca: verificacao JWT e propriedade, limites de entrada,
   taxa e concorrencia, logs sem conteudo bruto, banco limitado ao loopback,
   atualizacao da stack e auditoria OSV das dependencias.
5. Benchmarks locais: scripts autenticados, identidades isoladas, casos de
   contradicao/correcao/esquecimento, baseline de historico completo e metadados.
6. Validacao: testes, PostgreSQL real, migracoes e execucao dos experimentos.
   Os resultados e limites estao no protocolo de pesquisa.

## Proxima etapa de pesquisa

- Congelar uma revisao reproduzivel e arquivar configuracao e hashes.
- Criar conjuntos separados de desenvolvimento, calibracao e teste.
- Aumentar duracao, numero de casos, contradicoes e distratores.
- Comparar modelos sob o mesmo orcamento; variar um fator por rodada.
- Adicionar baseline sem juiz evolutivo e avaliacao semantica independente.
- Medir fatos obsoletos, erros, tokens, chamadas, custo e latencia.
- Repetir experimentos e relatar intervalos de confianca.
- Adaptar e executar LongMemEval antes de alegar replicacao.
- Revisar dados e resultados antes de qualquer publicacao externa.

## Limites operacionais ainda abertos

- Quotas financeiras e distribuicao dos limites entre varias instancias.
- Reindexacao paginada e controle de concorrencia por memoria.
- Esquecimento robusto diante de novas subject_keys e apagamento fisico, se exigido.
- Reavaliacao dos avisos de Spring MVC se houver views, XSLT ou streaming.
- Integracao institucional real apenas quando houver necessidade e provedor.

As mudancas foram organizadas em branches locais encadeadas de higiene,
arquitetura, seguranca e benchmarks, com mensagens simples e sem coautoria.
Nao houve publicacao automatica no remoto.
