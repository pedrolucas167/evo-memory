# Pré-registro — Frente 2: fechamento de fase em transição "de X para Y" (2026-10-30)

**Commit congelando:** posterior a este arquivo, anterior à primeira observação do run
de decisão ( nenhum número de decisão foi observado até agora; ver seção
"diagnóstico" abaixo — é design-time e nunca entra em critério).

## Motivação (diagnóstico design-time, nunca decisão)

Par de runs da sonda 1.1 (24/10): `futebol-abc` ficou 2/30 no tratamento com
respostas "quartas **e domingos**" ou "quartas **e sábados**" (15/20 das
tentativas do caso em todos os braços históricos têm essa forma). Diagnóstico
de grafo turno a turno (usuários dedicados `diag-dia-10/11`, never mixed com
runs de decisão) mostrou o caminho da perda:

- **turno 14 (change1)**: em rep 10 a troca sábados→domingos entrou perfeita;
  em rep 11 a fase dos sábados **sumiu sem que o domingo nascesse**.
- **turno 22 (change2)**: em rep 10 o domingo **sumiu sem que a quarta
  nascesse** — grafo final também não tinha a fase nova.

Leitura de mecanismo: o candidato de fechamento segue o contrato
"mesma chave da fase fechada", mas quando o extrator **colapsa a mensagem
num único candidato de fechamento** (sem candidato de estado novo) o
`closePhase` retrai a fase e a guarda da chave única descarta a linha nova
(WARN disparou 49× no run B da 1.1). O estado novo morre com a fase.

O juiz não pode resgatar o estado (não vê a mensagem crua, só memória +
candidato); o conteúdo do estado novo ("quarta-feira") só existe na mensagem
original. Logo o ponto de intervenção é o extrator.

## Hipótese (que a medição decide)

A regra de fechamento do extrator (pacote 1.1) enumera encerramentos por
evento (morreu, partiu, foi vendido, terminou, cancelado, concluído) e não
cobre **transições declaradas "de X para Y"/"agora X, não mais Y"** de fatos
do dia a dia (dia de jogo, cargo, cidade, sabor). Estendendo a regra para
exigir **dois candidatos** nesses casos (fechamento com a MESMA chave da fase
+ estado novo com chave NOVA e conteúdo sem citar a fase), o estado novo
deixa de depender de a LLM escolher dividir a mensagem por conta própria:
`state_correct` do evo-memory sobe sem regressão nos casos limpos já adotados.

## Mecanismo pré-registrado (única mudança de código)

- `ExtractionService`: com `memory.llm.subject-closing=LIGADA` e a chave nova
  `memory.llm.closing-split` LIGADA, o prompt `SYSTEM_PROMPT_WITH_CLOSING`
  ganha uma regra extra: transições "de X para Y"/"agora X, não mais Y"
  fecham a fase antiga e introduzem estado novo — devolva sempre os dois
  candidatos (fechamento: mesmo `kind` e MESMO `subject_key` da memória
  existente, conteúdo curto afirmando que a fase terminou; estado novo:
  `subject_key` NOVO, content descrevendo só o estado novo, sem narrar a
  transição nem citar a fase encerrada).
- Nenhuma outra camada muda: juiz e veredito CLOSED idênticos, `observe`/
  `closePhase`/guarda idênticos, evolução idêntica. Sem chamada LLM nova.
- Chave nova: `MEMORY_CLOSING_SPLIT` env, `memory.llm.closing-split`
  default `false` (classe e yml): **nenhuma mudança de comportamento fora
  do tratamento** (o default adotado da 1.1 permanece).

## Protocolo de decisão

- Medidor v2 congelado (`evals/run_obsolescence_v2.py`) e base de casos v2
  congelada (`evals/obsolescence_v2_cases.json`) — nenhuma editada; rodar em
  `http://127.0.0.1:8090`; Postgres em 5433 via compose.
- 30 casos/brasão (5 repetições × 6 casos × 2 sistemas evo/full-history),
  **cada braço com run_id próprio; proibido somar dados entre runs**.
- **Braço A (controle):** revisão corrente, `MEMORY_CLOSING_SPLIT` off
  (`MEMORY_SUBJECT_CLOSING` on, o padrão adotado).
- **Braço B (tratamento):** mesma revisão, `MEMORY_CLOSING_SPLIT=true`.
- Modelos confirmados por `/api/experiments/{id}/configuration` antes de cada
  run; aborto de máquina = relançar o braço do zero com run_id novo (sem
  dados parciais).
- Diagnóstico de dossiê pós-run permitido (textos crus + log do servidor p/
  evidência de mecanismo), desde que nunca altere critério/métrica.

## Critérios a priori (congelados antes do run A/B; idiom da família)

Métrica primária: `state_correct` do braço evo-memory (30 observações por
braço, run_id único por braço).

- **Adotar**: Δ = B − A ≥ **+3/30** **e** soma de classes de falha
  (`obsolete_intermediate` + `obsolete_old` + `failed`) ≤ **+2/30**.
  Em caso de adoção: `memory.llm.closing-split` passa a default `true`
  (env reverte; javadoc registra a medição).
- **Refutar**: Δ < 0 (resultado negativo publicado no dossiê).
- **Empate**: |Δ| ≤ +2/30 → manter disponível, default `false`; o rito de
  encerramento de frente: **dois não-adesos consecutivos** encerram a frente.

Métricas secundárias registradas, sem mordida de decisão: `state_correct`
por caso (esperado que `futebol-abc` capture um quarteirão do ganho);
latências médias (caso/consulta); `estimated_llm_calls_sum`; contagem do
WARN da guarda e dos fallbacks do juiz no log do servidor.

## Riscos declarados a priori

- O prompt pode não convencer o extrator a dividir em todas as janelas
  (nondeterminismo do provedor permanece); a tentativa é estrutural, não
  garantida.
- A regra extra pode gerar candidatos de fechamento espúrios em turnos
  neutros (fillers); interesse conjunto é capturado pelo critério (teto de
  falhas) e pelo `state_correct` por caso no dossiê.
- `obsolete_old` já amarra 0/30 nas 5 rodadas; qualquer re-aparição é
  registrada e quebra a leitura de continuidade.
