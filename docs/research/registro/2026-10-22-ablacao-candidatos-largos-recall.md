# Dossiê — Ablação de candidatos largos para o juiz (frente A.2, recall) — 2026-10-22

**Pré-registro:** `prereg/2026-10-21-ablacao-candidatos-largos-recall.md` (congelado antes
da primeira observação; commit da chave + pré-registro em `254aacf`, branch
`feature/recall-candidatos-largos`, PR #20 → `develop` = `fc0cdb6`).
**Resultado: REFUTADO pelo critério a priori. Não adota. A frente A.2 encerra-se.**

## Execução

- Braço do run: controle A primeiro, tratamento B depois, na mesma máquina/dia (21–22/10),
  cada braço com servidor próprio na 8090 e Postgres docker na 5433.
- Nota de execução: a primeira tentativa do braço A (21/10) foi abortada por suspensão da
  máquina no meio do run (~18:00–19:00); o medidor grava só no fim, logo não há dados dessa
  tentativa e nada dela toma parte dos números abaixo. Reinício do zero com `run_id` novo,
  conforme pré-registro. Nenhum dado parcial foi misturado.
- Modelos confirmados via `/api/experiments/{id}/configuration` antes de cada run
  (`gpt-6-luna` + `bge-m3`), código = `develop` `fc0cdb6` em ambos.

## Números congelados

Braço A (`run_id 97c6c914839b4c909a3f9b545c61c665`, margem relativa ativa — padrão de hoje)
e braço B (`run_id c0420b4c062044f78ad41438b28b15fc`, `RETRIEVAL_JUDGE_WIDE_CANDIDATES=true`).
Arquivos crus (git-ignorados): `evals/results/obsolescence_v2_97c6c914839b4c909a3f9b545c61c665.json`
e `evals/results/obsolescence_v2_c0420b4c062044f78ad41438b28b15fc.json`.

Braço evo-memory (30 observações por braço):

| classe | A (estreito) | B (largos) |
|---|---|---|
| correct (resposta) | 15 | 14 |
| obsolete_intermediate | 1 | 1 |
| obsolete_old | 0 | 0 |
| ambiguous | 14 | 14 |
| failed | 0 | **1** |
| **state_correct** | **12 (0,40)** | **10 (0,3333)** |
| latência média de consulta | 5,56 s | 6,04 s |
| latência média de caso | 107,1 s | 105,69 s |
| chamadas LLM estimadas | 1860 | 1860 |

Braço full-history (complementar, ambos os runs): 25 correct / 5 ambiguous / 0 falha, 2,72 s
(A) e 2,29 s (B), 432 tokens de prompt em média — se segurar a regra "não somar runs", os dois
runs de baseline vivem separados nos arquivos crus e só comparam dentro de cada run.

## Aplicação dos critérios (a priori)

- `state_correct(B) − state_correct(A) = 10 − 12 = −2/30` → **diferença negativa ⇒ REFUTADO**
  (critério: qualquer déficit negativo recusa a adoção).
- Critério de regressão de falha também ferido no braço B: `failed` 0 → 1.
- Adoção REJEITADA; a chave permanece `false` (padrão atual de comportamento intacto).

## Matriz por caso (evo, state_correct por repetição)

| caso | A | B |
|---|---|---|
| cidade-abc | 5/5 | 5/5 (1 resposta `failed` com grafo ok) |
| cafe-abc | 5/5 | 5/5 |
| cargo-abc | 2/5 | 0/5 |
| futebol-abc | 0/5 | 0/5 |
| gato-abc | 0/5 | 0/5 (1 resposta `correct` com grafo errado) |
| instrumento-abc | 0/5 | 0/5 (3 respostas `correct` com grafo errado) |

## Leitura honesta do resultado

1. A lógica offline (margem corta o estado atual por 0,0014 de cosine; juiz com 6 candidatos
   seleciona as duas linhas corretas) **não sobrevive ao sistema completo**. O ganho teórico de
   cobertura vira variação: no braço largo, mais distractores reais de semeadura entram na lista
   do juiz e o ruído sistêmico (extração, composição do prompt, temperatura 0,70 da síntese)
   consome resultado bruto — viu-se no caso `cargo-abc`, que perdeu os 2 acertos de estado
   (A → 0 em B) com 1 `obsolete_intermediate` extra e 1 `failed` novo.
2. `obsolete_old` segue **0/30 em ambos os braços** — terceiro run consecutivo do medidor v2
   com esse número (os dois runs anteriores estão nos dossiês v1/v2): respostas obsoletas tipo
   "estado antigo" continuam ausentes no regime de 30 turnos.
3. Padrão sistemático reconfirmado: **resposta `correct` com grafo `WRONG`** (B: gato 1×,
   instrumento 3×, cargo 1× com resposta certa e grafo errado) e **`ambiguous` saturando os
   casos `futebol`/`instrumento`/`gato`** — o grafo guarda resíduo da transição e a resposta
   final alterna quando o resíduo entra no contexto. Essa assinatura sobreviveu às duas
   configurações de geração de candidatos: muda quem entra na lista, não o resíduo — que é um
   problema de evolução (eventos e referências de transição já mapeados no dossiê v2), não de
   gate de recall.
4. O custo segue dominado pela semeadura (~105 s/caso, ~62 chamadas por caso); a consulta em si
   ficou em 5,56–6,04 s nos dois braços.

## O que fica

- **Não adota**; chave `RETRIEVAL_JUDGE_WIDE_CANDIDATES` permanece disponível (padrão `false`)
  para futuras explorações, mas nenhuma mudança de comportamento padrão sobe.
- **Frente A.2 encerrada** pelo rito (refutada por critério a priori). Outras mecanicas de
  conflito de recall seguem possíveis, mas esta via específica — margem suspensa na geração de
  candidatos ao juiz — tem evidência negativa registrada.
- Abertas para frente nova (pré-registro próprio): (1) filtro de resíduo de transição no grafo
  (eventos datados que fecham assunto continuam ativos), (2) janela truncada (hipótese central
  restante), (3) trajetória de 10 anos (ideia do proprietário).
