# Relatório técnico preliminar: transições e limites da memória evolutiva

Data: 24 de outubro de 2026. Status: rascunho de síntese, não um novo
experimento confirmatório. A rodada da frente 1.1 permanece pendente neste
documento. Nenhuma medição adicional foi executada para escrever esta síntese.

## Resumo

O Evo Memory investiga a manutenção de estado pessoal atualizado sem perder
episódios e revisões. Os experimentos concluídos não demonstraram vantagem
sobre histórico completo na redução de respostas obsoletas nos regimes
testados. Diagnósticos apontam dificuldades de resolução de assuntos,
persistência de referências a fases anteriores e seleção do contexto atual.
O fechamento de fases produziu ganho localizado no indicador de estado,
mas violou a guarda de regressão e não foi adotado como padrão.

O resultado atual sustenta uma análise de falhas e de instrumentação, não
uma alegação de memória superior, economia financeira ou novidade científica
comprovada. A novidade relativa à literatura exige revisão própria.

## Perguntas e desenho

1. O estado versionado reduz respostas obsoletas frente a histórico completo?
2. Encerrar fases melhora o estado ativo sem introduzir regressões?
3. Qual o esforço operacional de ingestão e consulta de cada abordagem?

Os protocolos e decisões originais permanecem nos respectivos pré-registros
e dossiês. Esta síntese não altera critérios, não soma amostras entre rodadas
e não promove diagnósticos exploratórios a resultados confirmatórios.

O desenho v2 usa seis cenários sintéticos, cinco repetições e histórias de
30 turnos com mudanças encadeadas A → B → C. O baseline recebe o histórico
completo e uma instrução explícita de preferir correções e fatos recentes.
Há apenas seis cenários distintos: as 30 observações não representam 30
histórias independentes nem uma população de usuários.

## Evidências concluídas

| Estudo | Resultado | Decisão registrada |
|---|---|---|
| Obsolescência v1 | Respostas obsoletas: 0/30 Evo e 0/30 histórico completo | Hipótese não demonstrada |
| Obsolescência v2 | Respostas obsoletas: 0/30 e 0/30; respostas corretas: 19/30 e 25/30 | Segundo empate; operacionalização encerrada |
| Candidatos largos no recall | Estado correto: 12/30 controle e 10/30 tratamento; falhas: 0 e 1 | Refutado pelo critério do estudo; não adota |
| Fechamento de assunto, rodada 1 | Estado correto: 11/30 controle e 14/30 tratamento; soma de obsolescência e falhas: 0 e 8 | Ganho no limiar, guarda violada; não adota |

Fontes: [obsolescência v1](registro/2026-10-19-ablacao-obsolescencia.md),
[obsolescência v2](registro/2026-10-20-ablacao-obsolescencia-v2.md),
[candidatos largos](registro/2026-10-22-ablacao-candidatos-largos-recall.md)
e [fechamento](registro/2026-10-24-fechamento-de-assunto.md).
Os números têm denominadores próprios; não devem ser agregados entre estudos.

### O que cada indicador mede

- A classe da resposta distingue resposta correta, ambígua, obsoleta e falha.
- `state_correct` examina marcadores do estado atual e dos antecessores no
  grafo retornado pela API. Não é uma avaliação semântica universal do grafo
  e não equivale a acerto da resposta. Eventos passados verdadeiros e textos
  de transição podem carregar marcadores antigos sem serem fatos falsos.
- O baseline não tem grafo persistido equivalente: comparar seu acerto de
  resposta com `state_correct` do Evo como se fossem a mesma métrica é inválido.

## Diagnóstico das transições

No fechamento de assunto, o ganho inteiro de três observações veio do cenário
de cargo. No cenário de mudança de entidade, a fase intermediária continuou
ativa e respostas obsoletas apareceram. Os dossiês levantam dois mecanismos:
o candidato novo pode não alcançar a fase antiga, e uma chave reutilizada
pode impedir a criação da nova linha após o fechamento.

Esses mecanismos motivaram a frente 1.1: um candidato explícito na chave da
fase a encerrar e outro, com chave própria, para o estado novo. A implementação
existe e os 45 testes focados executados nesta sessão passaram. Testes com
clientes simulados verificam contratos e regras, não a obediência do modelo
real nem ganho de benchmark. O OLS permanece fora da intervenção desta rodada.

## Esforço operacional e limite financeiro

Na obsolescência v2 concluída, o Evo levou 117,1 s por cenário, incluindo
ingestão e consulta, contra 2,35 s do histórico completo. A consulta isolada
do Evo levou 7,2 s. O medidor estimou 62 chamadas LLM por cenário do Evo,
contra uma chamada do baseline; este retornou em média 432 tokens de entrada
e 42 de saída. São medidas diferentes, não uma conta financeira equivalente.

A ingestão é trabalho antecipado; a comparação observada contém uma consulta
por história. Ela não determina o custo amortizado de várias consultas sobre
uma mesma memória. As latências também dependem do provedor: no estudo de
fechamento, houve instabilidade e falhas nos dois sistemas.

Não há rastreamento completo e simétrico de tokens, embeddings, chamadas do
juiz e preços efetivamente cobrados. Logo, não se estima aqui saldo restante,
valor a pagar, economia ou multiplicador monetário. Uma avaliação financeira
futura deve registrar uso por operação, preços da rodada, falhas cobradas e
quantidade de consultas que amortizam a ingestão.

## Rodada atual e risco de crédito

A frente 1.1 tem [pré-registro próprio](prereg/2026-10-24-sonda-fechamento-explícito.md).
Foi identificada uma execução em andamento e um relatório completo produzido
hoje; a associação final dos arquivos aos grupos deve ser confirmada antes
de publicar uma comparação. Esta síntese não interpreta dados parciais.

| Entrega da frente 1.1 | Estado nesta síntese |
|---|---|
| Identificação e conferência dos relatórios A e B | Pendente |
| Contagens por cenário e critérios congelados | Pendente |
| Latências e estimativas de chamadas | Pendente |
| Decisão de adoção | Pendente; padrão não alterado |

O saldo do OpenRouter não foi consultado. Falta de crédito é um risco informado
pelo proprietário, não uma causa de falha observada e confirmada aqui. Se
ocorrer, registrar a evidência disponível, o ponto da interrupção e as falhas
sem credenciais. Não atribuir automaticamente todo erro ao saldo, nem remover
falhas do denominador. Aplicam-se as regras do pré-registro: resultados
parciais não formam uma rodada completa nem são somados a outra execução.
Interrupções ou desvios precisam ser declarados, não corrigidos silenciosamente.

O medidor atual escreve o relatório consolidado apenas no fim. Contagens de
progresso no banco não substituem esse relatório. Persistência incremental
de resultados é uma melhoria futura de instrumentação, fora desta rodada
congelada. Não executar repetições extras apenas para substituir falhas.

## Limitações e próximos passos

- Amostra sintética pequena, repetida e sem validação em histórico real longo.
- Trinta turnos cabem no contexto do modelo; não se demonstrou ganho sob
  truncamento nem memória de trajetória de anos.
- Avaliadores por marcadores não capturam toda a semântica temporal.
- Controle antes do tratamento confunde configuração com variações temporais
  do provedor; não há estimativa causal forte ou significância demonstrada.
- A mudança é um pacote: não isola o efeito de cada componente nem do OLS.

Primeiro, concluir ou documentar a interrupção da frente 1.1 e aplicar somente
os critérios já registrados. Depois, formular um novo protocolo para o que
restar aberto. Uma avaliação futura deve separar preservação histórica,
correção do estado atual, qualidade de resposta e custo amortizado, com
persistência incremental, orçamento definido e casos de validação separados.

## Conclusão provisória

A evidência disponível identifica limites da evolução e da recuperação de
estado, e mostra que um ganho localizado pode coexistir com regressões.
Isso é material para um relatório técnico exploratório reproduzível. Ainda
não demonstra superioridade da arquitetura, benefício do OLS neste regime
ou custo financeiro competitivo. A rodada em andamento deve ser acrescentada
em atualização posterior, com seus resultados completos ou sua interrupção
explicitamente documentada.
