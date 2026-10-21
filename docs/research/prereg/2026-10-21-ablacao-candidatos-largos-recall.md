# Pré-registro — Ablação de candidatos largos para o juiz (frente A.2, recall)

**Data:** 2026-10-21 · **Status:** congelado antes da primeira observação do experimento de decisão.
**Antecedentes (exploratórios, rotulados):**
`registro/2026-10-21-diagnostico-recall-offline.md` e
`registro/2026-10-21-verificacao-fix-alias.md`.

## Pergunta

Suspender a margem relativa **na geração de candidatos ao juiz de re-ranking**
(corte único de `minSimilarity`) melhora acerto de estado (`state_correct`)
no benchmark de obsolescência v2, sem regressões?

## Mecanismo sob teste

Com o juiz decisor, a margem relativa não protege o contexto final — o filtro
conservador do juiz protege — e na medida offline eliminou o estado atual
(Nina, 0,5340 vs corte 0,5354 = −0,0014) antes da disputa. Hipótese de
engenharia: candidatos largos aumentam cobertura; o juiz mantém a precisão
(na medida offline: 2/2 linhas do caso, 4/4 fillers fora, temperatura 0).

## Operacionalização congelada

- **Código:** branch com o implementado nesta frente; chave
  `RETRIEVAL_JUDGE_WIDE_CANDIDATES` (padrão `false` = braço estreito).
  Ambos os braços incluem o fix de alias já adotado (única diferença: a chave).
- **Braço A (controle):** `RETRIEVAL_JUDGE_WIDE_CANDIDATES=false` — margem
  relativa corta candidatos antes do juiz (comportamento de hoje).
- **Braço B (tratamento):** `RETRIEVAL_JUDGE_WIDE_CANDIDATES=true` — margem
  não corta a lista do juiz; o caminho legado (juiz indisponível/desligado)
  degrada por ordenação pura, sem margem.
- **Dataset:** `evals/obsolescence_v2_cases.json` intacto (6 casos A→B→C com
  marcadores + fillers; consulta neutra de estado; layout 13/7/7; 30 turnos).
- **Modelos:** `openai/gpt-6-luna` (extração, juiz, chat) e `baai/bge-m3`
  (embedding), igual ao protocolo v2. Nada muda além da chave ablação.
- **Design:** cada braço = 6 casos × 5 repetições = 30 consultas por braço;
  semeadura nova a cada repetição; cada braço com `run_id` próprio —
  resultados nunca somados entre runs.
- **Métrica primária:** taxa `state_correct` (classe `correct` do medidor v2).
- **Métricas secundárias (todas reportadas, inclusive negativas):** taxas de
  `obsolete_intermediate`, `obsolete_old`, `ambiguous`, `failed`; latência por
  caso; chamadas LLM e custo estimado quando o medidor as registrar.
- **Ambiente:** servidor local (8090/8091), Postgres docker (5433), hardware
  e janela de tempo da mesma máquina/dia; braço A antes do braço B.

## Critérios de decisão (a priori, com 30 observações por braço)

- **Adotado (vira padrão):** `state_correct(B) − state_correct(A) ≥ +3/30`
  (10 pontos percentuais) **e** nenhuma classe de falha piora além de
  +2/30 (`obsolete_old`, `obsolete_intermediate`, `failed` somados).
- **Refutado (não adota; frente encerrada por 0 evidência):** diferença
  negativa para o braço B em `state_correct`.
- **Empate (inclui variações dentro de ±2/30):** não demonstrado — não
  adota, registra; dois empates consecutivos sobre a mesma operacionalização
  encerram esta frente, como no rito v1/v2 (v1 e v2 já empataram 0×0 antes;
  estes empates são sobre a MÉTRICA de acerto, não sobre respostas obsoletas
  — ainda assim o rito de encerramento vale).

## Custos e riscos

- ~2 execuções do medidor v2 (evo arm), historicamente ~117 s/caso e ~62
  chamadas LLM por caso ⇒ ~1 h por braço, ~1860 chamadas por braço (estimativas do run v2; podem variar).
- Risco de suspensão da máquina no meio do run: o medidor só grava no fim —
  se abortar, reinicia-se do zero com `run_id` novo; dados parciais nunca
  entram no dossiê.
- Chave do OpenRouter vive no escopo máquina; nunca impressa/copiada.
- A semeadura é cara; nenhuma execução extra "para conferir" além das
  registradas aqui sem novo pré-registro.

## O que este registro NÃO afirma

- Nada sobre a hipótese central original (janela/obsolescência) — ela segue
  encerrada nos seus termos v1/v2.
- Nada fora do regime de 30 turnos; outros regimes pedem pré-registro novo.
