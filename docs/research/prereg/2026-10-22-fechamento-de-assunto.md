# Pré-registro — Fechamento de assunto no grafo (frente 1, evolução)

**Data:** 2026-10-22 · **Status:** congelado antes da primeira observação do experimento de decisão.
**Antecedentes (exploratórios, rotulados):**
`registro/2026-10-22-ablacao-candidatos-largos-recall.md` (assinatura "resposta
`correct` com grafo `WRONG`" e resíduo de transição como driver dominante de
`state=WRONG`), `registro/2026-10-21-verificacao-fix-alias.md` (defeito
origem), `registro/2026-10-21-diagnostico-recall-offline.md`.

## Pergunta

Operacionalizar **fechamento de assunto** no grafo (o juiz pode marcar uma
memória como fase encerrada pela nova informação; a fase retrai e o estado
novo nasce como memória própria, descrito sem narrar a transição) melhora
acerto de estado (`state_correct`) no benchmark de obsolescência v2, sem
regressões?

## Mecanismo sob teste

O dossiê de 22/10 isolou a assinatura: com o recall sob controle (a ablação
A.2 refutou a via da margem), o resíduo de transição no grafo é o driver
dominante de `state=WRONG` — eventos datados ("a Mel morreu") e referências
("para substituir ele") mantêm memórias de fases fechadas ATIVAS, disputando
recall e corrompendo o próprio conteúdo da memória de estado (a fusão do
encadeamento de aliases guardou “A gata Mel morreu, e adotou uma gata chamada
Nina.” dentro da linha viva). O pacote fecha a fase:

1. **Juiz** ganha o veredito `CLOSED` (“a memória de referência descreve uma
   fase que a nova informação encerra”) — até aqui o contrato eram 4
   vereditos.
2. **Evolução (regra de domínio pura):** `CLOSED` retrai a memória de
   referência (mesmo estado de `FORGOTTEN`: conteúdo, confiança e histórico
   de revisão preservados; só a saída do recall — o filtro `status='ACTIVE'`
   do armazém vetorial e do grafo já exclui retratadas).
3. **Estado novo nasce como memória própria** (campos do candidato), com
   guarda determinística: se o candidato reusar a `subject_key` da fase
   fechada (única no par usuário+chave), a linha nova não nasce — só a
   retração.
4. **Extração** (mesma chave do pacote): o conteúdo do estado novo não narra
   a transição nem cita a fase encerrada, e recebe `subject_key` NOVO.

Âncoras verificadas por leitura de código (antes do congelamento): o endpoint
`/graph` serve apenas memórias ATIVAS (`recallActive` → `findActiveByUser`),
logo a métrica congelada do medidor v2 — que varre o conteúdo do grafo —
**enxerga a retração**; `nearest` do pgvector filtra a mesma tabela
(`semantic_memory`) por status, então nenhuma limpeza de índice é necessária;
`change_type` persiste como STRING (`@Enumerated(EnumType.STRING)`), sem
migração. O fallback determinístico (`SimpleChangeClassifier`) continua
conhecendo só CONFIRMED/CONTRADICTED — `CLOSED` nunca passa por ele.

## Operacionalização congelada

- **Código:** branch `feature/fechamento-de-assunto` (implementado nesta
  frente, testes determinísticos 123/123); chave única do pacote
  `MEMORY_SUBJECT_CLOSING` (`memory.llm.subject-closing`, padrão `false` =
  comportamento de hoje, contrato de 4 vereditos intacto).
- **Braço A (controle):** `MEMORY_SUBJECT_CLOSING=false` — juiz de 4
  vereditos, extração histórica, `CONTRADICTED` funde conteúdo (comportamento
  de hoje).
- **Braço B (tratamento):** `MEMORY_SUBJECT_CLOSING=true` — pacote completo:
  juiz de 5 vereditos, retração de fase fechada, estado novo com chave
  própria e conteúdo sem transição.
- **Dataset:** `evals/obsolescence_v2_cases.json` intacto (6 casos A→B→C com
  marcadores + fillers; consulta neutra de estado; layout 13/7/7; 30 turnos).
- **Modelos:** `openai/gpt-6-luna` (extração, juiz, chat) e `baai/bge-m3`
  (embedding), igual ao protocolo v2. Nada muda além da chave do pacote.
- **Design:** cada braço = 6 casos × 5 repetições = 30 consultas por braço;
  semeadura nova a cada repetição; cada braço com `run_id` próprio e servidor
  próprio na 8090 — resultados nunca somados entre runs.
- **Nota de confundimento declarada:** o tratamento é o PACOTE (juiz +
  extração + evolução sob uma chave). Uma diferença negativa não localiza a
  alavanca; um resultado positivo também é do pacote. Desdobramento por
  alavanca, se algum dia necessário, pede pré-registro próprio.
- **Interpretação do `state_correct` declarada a priori:** a métrica congelada
  exige ausência LITERAL ("mel"/"tobi") do texto de TODO o grafo ativo. O
  fechamento só a satisfaz se a fase encerrada sair do grafo ativo E o estado
  novo não citar a fase fechada — exatamente o que o pacote impõe. O caso
  `gato-abc` é o alvo estrutural (retração da fase Mel + fase Tobi); os casos
  `cidade`/`cafe`/`cargo`/`instrumento` podem migrar de `CONTRADICTED` para
  `CLOSED` pelo juiz (a troca de residência também encerra uma fase), o que
  é parte do tratamento e será reportado por caso.
- **Métrica primária:** taxa `state_correct` (classe `correct` do medidor v2).
- **Métricas secundárias (todas reportadas, inclusive negativas):** taxas de
  `obsolete_intermediate`, `obsolete_old`, `ambiguous`, `failed`; latência por
  caso; chamadas LLM estimadas.
- **Ambiente:** servidor local 8090, Postgres docker (5433), hardware e
  janela de tempo da mesma máquina/dia; braço A antes do braço B, sequencial
  (sem concorrência de CPU/embedding entre braços).

## Critérios de decisão (a priori, com 30 observações por braço)

- **Adotado (vira padrão, junto com o fix já em `develop`):**
  `state_correct(B) − state_correct(A) ≥ +3/30` (10 pontos percentuais) **e**
  nenhuma classe de falha piora além de +2/30 (`obsolete_old`,
  `obsolete_intermediate`, `failed` somados).
- **Refutado (não adota; frente encerrada por 0 evidência):** diferença
  negativa para o braço B em `state_correct`.
- **Empate (inclui variações dentro de ±2/30):** não demonstrado — não adota,
  registra; dois empates consecutivos encerram a frente, como no rito v1/v2.

## Custos e riscos

- ~2 execuções do medidor v2 (evo arm), historicamente ~105–117 s/caso e ~62
  chamadas LLM por caso ⇒ ~1 h por braço, ~1860 chamadas por braço
  (estimativas dos runs de 21–22/10; podem variar).
- Risco de suspensão da máquina no meio do run: o medidor só grava no fim —
  se abortar, reinicia-se do zero com `run_id` novo; dados parciais nunca
  entram no dossiê. (Já ocorreu 2× nesta série; nota de execução entra no
  dossiê sempre.)
- O juiz pode fechar demais (perdas de informação ativa em casos sem fecho:
  `CLOSED` migra para retração onde `CONTRADICTED` preservava tudo em uma
  linha) — é um risco MENSURÁVEL pelas classes de falha e pelas respostas
  `ambiguous`; sem critério post-hoc: a decisão segue os números do critério.
- Chave do OpenRouter vive no escopo máquina; nunca impressa/copiada.
- A semeadura é cara; nenhuma execução extra "para conferir" além das
  registradas aqui sem novo pré-registro.

## O que este registro NÃO afirma

- Nada sobre a hipótese central original (janela/obsolescência) — ela segue
  encerrada nos seus termos v1/v2.
- Nada fora do regime de 30 turnos; outros regimes pedem pré-registro novo.
- Nada sobre separação de alavancas do pacote (nota de confundimento acima).
