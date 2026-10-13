# Protocolo de pesquisa

## Hipotese e limites

Investigar se regras explicitas de atualizacao e versionamento temporal reduzem
o uso de fatos obsoletos, sob um orcamento de contexto e chamadas controlado.
O codigo atual e uma plataforma experimental, nao evidencia de superioridade.
Nao chamar estas verificacoes de ganho de processamento ou de memoria de longo
prazo: os casos atuais sao curtos e sinteticos.

## Execucao local

1. Iniciar Docker e a aplicacao no perfil dev.
2. Executar `python evals/run_temporal_benchmark.py --repetitions 3`.
3. Executar `python evals/run_temporal_benchmark.py --system full-history --repetitions 3`.
4. Executar os conjuntos de retrieval separados, sem ajustar limiares nos held-out.

Usar `--base http://127.0.0.1:8081` se a aplicacao estiver em outra porta.
O perfil dev cria a chave de autenticacao local; scripts so usam essa chave em
loopback. Cada caso e repeticao utiliza identidade nova. Nao enviar dados reais
de usuarios para este protocolo. Nao publicar chaves, resultados com dados
pessoais ou arquivos locais de identidade.

## O que e comparavel

O experimento Evo Memory verifica estado atual, resposta, auditoria de
contradicao, correcao explicita, esquecimento logico e nao reativacao pela mesma
chave de assunto. O baseline de historico completo recebe as mesmas observacoes
e correcoes e usa o mesmo modelo de chat, mas nao possui estado persistido.
Comparar somente `latest_answer` e `corrected_answer` entre sistemas. Nao
comparar o total de invariantes do Evo com o total de respostas do baseline.

Os prompts dos sistemas diferem e a temperatura e 0.7. Os checadores de resposta
usam presenca de termos, nao um juiz semantico; uma resposta que menciona fatos
antigos pode acertar esse checador. Os checadores de estado tambem sao baseados
em termos. Avaliacao humana ou anotacao semantica independente sera necessaria.
O baseline nao demonstra capacidade de esquecimento fisico nem seguranca.

## Rastreabilidade

O relatorio temporal salva hash do dataset, revisao Git, indicacao de checkout
modificado, hash do codigo fonte, configuracao efetiva do servidor, repeticoes,
latencia por caso e verificacoes individuais. A configuracao informa modelos,
limiares e versao do framework sem revelar credenciais. Os resultados sao
ignorados pelo Git por padrao. Um hash de fonte nao substitui um commit ou
snapshot arquivado: publicar somente a partir de uma revisao reproduzivel.

Nao repetir automaticamente escritas que retornaram falha do servidor: a
observacao pode ja ter sido persistida. O cliente repete apenas 429, respeitando
Retry-After. Registrar falhas como resultado, sem remove-las do denominador.

## Rodada preliminar de 2026-10-13

- Evo Memory temporal: 2/2 cenarios; invariantes locais verificados.
- Historico completo: 2/2 cenarios; respostas atual e corrigida corretas.
- Retrieval v2: 5/6; contexto de natacao e filhos entrou no caso de profissao.

Essas execucoes iniciais ocorreram antes da reinicializacao final da stack
atualizada. Os arquivos locais distinguem cada rodada. Os resultados anteriores
de 6/6 no README sao historicos e nao devem ser apresentados como resultado atual.
Nao ha evidencia de vantagem estatistica com essa amostra. O conjunto temporal
foi criado nesta implementacao e funciona como smoke suite, nao como held-out.

## Proxima fase

Congelar uma revisao e separar desenvolvimento, calibracao e teste. Aumentar
numero de usuarios sinteticos, duracao e distratores; inserir varias mudancas
sucessivas, regressos a estados antigos, negacoes, mudancas de subject_key e
planos expirados. Comparar historico completo e uma memoria sem juiz evolutivo,
controlando modelo, prompt, orcamento de contexto e acesso aos dados.

Medir respostas corretas, uso de fatos obsoletos, contradicoes, cobertura e
precisao de retrieval, tokens, chamadas, custo, latencia e falhas. Relatar n e
intervalos de confianca com repeticoes. Repeticoes de dois cenarios nao sao
usuarios independentes nem substituem diversidade de casos. LongMemEval e
outros datasets externos precisam de adaptacao, baseline e execucao reais antes
de qualquer alegacao de replicacao. Publicacao externa exige uma rodada
reproduzivel, revisao dos dados e autorizacao para publicar.
