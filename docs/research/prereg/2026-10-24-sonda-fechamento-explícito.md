# Pré-registro — Candidato de fechamento explícito no extrator (frente 1.1)

**Data:** 2026-10-24 · **Status:** congelado antes da primeira observação do
experimento de decisão. **Rodada 1 da operacionalização 1.1** (a rodada 1 do
pacote está em `registro/2026-10-24-fechamento-de-assunto.md`; esta é uma
extensão do pacote, NÃO uma reexecução dela).
**Antecedentes (exploratórios, rotulados):**
`registro/2026-10-24-fechamento-de-assunto.md` (rodada 1: ganho primário
+3/30 no limiar, todo em cargo; gato nunca fechou; guarda de chave única
disparou uma vez), `prereg/2026-10-22-fechamento-de-assunto.md`.

## Pergunta

Dar ao extrator, sob a mesma chave do pacote, a regra de devolver um
**candidato de fechamento explícito** (mesmo `kind` e MESMO `subject_key` da
memória existente cuja fase a mensagem encerra, com conteúdo curto afirmando
o fecho) além do estado novo com `subject_key` NOVO — elimina as duas falhas
de fechamento da rodada 1 (candidato que não sonda a fase por falta de
menção; fechamento na mesma chave que aborta a linha nova) e converte o
ganho primário de +3/30 em adoção (`state_correct` ≥ +3/30 SEM regressão de
falha)?

## Mecanismo sob teste

Na rodada 1, o fechamento só dispara quando o candidato pousa na linha a
fechar: `cargo-abc` fechou porque o extrator reutilizou a chave (estado
novo nasceu e o grafo ficou limpo — resposta de estado puro 4/5);
`gato-abc` nunca fechou porque o candidato do turno 3 trocou de chave sem
citar "tobi" — a fila de alias não sonda e o veredito CLOSED nunca é
avaliado. A regra nova torna o alvo do fechamento explícito: o extrator
recebe as memórias existentes (com chaves) e aponta a fase a fechar; o juiz
avalia CLOSED na chave exata; o estado novo nasce com chave própria
(independente da ordem dos candidatos — cada `observe()` é independente).

## Operacionalização congelada

- **Código:** branch da frente 1.1 (prompt do extrator estendido; NENHUMA
  outra mudança — `observe`, evolução, guarda e juiz idênticos à rodada 1;
  testes 123/123). Chave única do pacote: `MEMORY_SUBJECT_CLOSING`
  (`memory.llm.subject-closing`, padrão `false`).
- **Braço A (controle):** `MEMORY_SUBJECT_CLOSING=false` — comportamento de
  hoje (contrato de 4 vereditos, extração histórica).
- **Braço B (tratamento):** `MEMORY_SUBJECT_CLOSING=true` — pacote completo
  da frente 1.1 (juiz de 5 vereditos + retração de fase + candidato de
  fechamento explícito + estado novo com chave própria).
- **Dataset:** `evals/obsolescence_v2_cases.json` intacto (6 casos A→B→C,
  30 turnos). **Modelos:** `openai/gpt-6-luna` + `baai/bge-m3`.
- **Design:** 6 casos × 5 repetições = 30 consultas por braço; semeadura nova
  por repetição; `run_id` próprios; braço A antes do B, sequencial, mesma
  máquina. Números NUNCA somados entre runs nem entre rodadas — a comparação
  interna é A×B desta rodada; a rodada 1 é contexto interpretativo apenas.
- **Métrica primária:** taxa `state_correct` (medidor v2 congelado).
- **Métricas secundárias (todas reportadas):** `obsolete_intermediate`,
  `obsolete_old`, `ambiguous`, `failed`, latências, chamadas LLM.
- **Nota de confundimento:** o tratamento é o pacote inteiro sob uma chave;
  a diferença da rodada 1 está apenas no prompt do extrator (candidato de
  fechamento explícito). Falhas de provedor na janela são ruído declarado
  (rodada 1 teve 3 falhas evo + 1 full-history num único rep).

## Critérios de decisão (a priori, com 30 observações por braço)

- **Adotado (vira padrão junto com o pacote):**
  `state_correct(B) − state_correct(A) ≥ +3/30` **e** soma de classes de
  falha (`obsolete_intermediate + obsolete_old + failed`) não piora além de
  +2/30.
- **Refutado (não adota):** diferença negativa para o braço B em
  `state_correct`.
- **Empate (±2/30):** não demonstrado — não adota, registra.
- **Rito de encerramento da frente 1: dois não-adesos consecutivos**
  (a rodada 1 do pacote, registrada em 24/10, é o primeiro; um segundo
  resultado sem adoção **nesta rodada ou na seguinte, sem ganho primário**
  encerra a frente) — ganho primário positivo com regressão de falha NÃO
  encerra: pede nova rodada pré-registrada mirando a regressão.

## Custos e riscos

- ~2 execuções do medidor v2 ⇒ ~1 h por braço (latências de provedor podem
  variar muito — declarado; `failed` de provedor entra como medido).
- Risco: o extrator pode emitir candidatos de fechamento espúrios
  (fases que a mensagem não encerra) — o juiz os recebe na chave exata;
  UNRELATED degrada para preservação (COMPLEMENTED sem troca), CONTRADICTED
  indevido reescreve a linha — ambos contam como o medidor medir.
- Risco: suspensão da máquina (3 ocorrências na série) — aborto = relanço do
  zero com `run_id` novo; nada parcial entra no dossiê.
- Chave do OpenRouter no escopo máquina; nunca impressa.
- Nenhuma execução extra além das registradas, sem novo pré-registro.

## O que este registro NÃO afirma

- Nada fora do regime de 30 turnos / dataset congelado v2.
- Nada sobre a separação de alavancas do pacote (uma chave governa tudo).
- Nada sobre outros regimes de janela (hipótese central, frente B) ou
  trajetória de 10 anos (frente C) — pré-registros próprios.
