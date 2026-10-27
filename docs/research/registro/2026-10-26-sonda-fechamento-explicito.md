# Dossiê — Sonda de fechamento explícito (frente 1.1) — 2026-10-26

**Pré-registro:** `prereg/2026-10-24-sonda-fechamento-explicito.md` (congelado
antes da primeira observação; commit `9ddbb8e`, PR #27 → `develop` = `839b040`,
PR #28 → `main` = `9bdb298`).
**Resultado: ADOTADO pelo critério a priori** — primeiro tratamento adotado
no medidor v2 de obsolescência.

## Execução

- Código idêntico nos dois braços (revisão com a frente 1.1, prompt do
  extrator estendido, `observe`/evolução/juíz/guarda idênticos à rodada 1);
  a ÚNICA diferença entre braços é `MEMORY_SUBJECT_CLOSING`.
- Modelos confirmados via `/api/experiments/{id}/configuration` antes de cada
  run (`gpt-6-luna` + `bge-m3`); código centralizado (25/10 tag de CI verde
  em `develop` e `main`).
- Notas de execução: zero abortos nesta rodada (primeira rodada da série
  sem suspensão na janela dos dois braços). Braço A em 24/10 16:49–17:44
  (~55 min), braço B em 24/10 17:45–19:45 (~2 h). Latência do braço B maior
  (238,4 s/caso vs 109,3 s no A) — pacote adiciona candidados/chamadas no
  fecho e a janela era outra; contexto declarado, contagem a priori intacta.

## Números congelados

Braço A (`run_id bb05317f81864e8eb1822f722189f214`, controle, chave off) e
braço B (`run_id 33ca8d87dded4f69be34080c9bc0d1b2`, tratamento, chave on).
Arquivos crus (git-ignorados) em `evals/results/obsolescence_v2_*.json`.

Braço evo-memory (30 observações por braço):

| classe | A (controle) | B (sonda) |
|---|---|---|
| **state_correct** | **11 (0,3667)** | **27 (0,9)** |
| correct (resposta) | 17 | 27 |
| obsolete_intermediate | 0 | 0 |
| obsolete_old | 0 | 0 |
| ambiguous | 13 | 2 |
| failed | 0 | 1 |
| latência média de consulta | 6,61 s | 8,65 s |
| latência média de caso | 109,3 s | 238,4 s |
| chamadas LLM estimadas | 1860 | 1798 |

Baseline full-history dos mesmos runs (nunca somado entre runs): A 24 correct /
6 ambiguous / 0 failed; B 24 correct / 6 ambiguous / 0 failed.

## Aplicação dos critérios (a priori)

- `state_correct(B) − state_correct(A) = 27 − 11 = +16/30` → muito acima do
  limiar de ganho (≥ +3/30).
- Soma de classes de falha: A = 0; B = 1 (`failed` futebol rep 1) →
  **+1/30 ≤ teto +2/30**.
- **Decisão: ADOTADO** — o comportamento do pacote (juiz de 5 vereditos,
  retração de fase encerrada, candidato de fechamento explícito, estado novo
  com chave própria, extração sem narrar transição) vira padrão, com a chave
  `MEMORY_SUBJECT_CLOSING` mantida como desligador emergencial (padrão
  `true`).

## Matriz por caso (evo, state_correct por repetição)

| caso | A | B |
|---|---|---|
| cidade-abc | 5/5 | 5/5 |
| cafe-abc | 5/5 | 5/5 |
| cargo-abc | 1/5 | **5/5** |
| futebol-abc | 0/5 | 2/5 |
| gato-abc | 0/5 | **5/5** |
| instrumento-abc | 0/5 | **5/5** |

## Evidência de mecanismo (textos crus e log do servidor)

- `gato-abc` 5/5 com respostas de estado puro ("Seu gato se chama Nina.") —
  a fase de Tobi, invicta na rodada 1 (respondeu "Tobi" 4/5), agora fecha:
  o candidato de fechamento explícito chega na chave exata e o CLOSED é
  sempre avaliado; o estado novo (Nina) nasce com chave própria.
- `instrumento-abc` 5/5 de estado com respostas idênticas às do controle
  ("Você toca violino."): a melhora do caso está no GRAFO, não na resposta —
  no controle a resposta acertava com resíduo ("para substituir ele") dentro
  do grafo ativo (`correct` com `state=WRONG` em 3 repetições); o pacote
  limpa o grafo e o estado puro sobrevive em ambos.
- `futebol-abc` 2/5 (de 0/5): o pacote limpou o grafo, mas o caso conserva
  resquício próprio — escorregão de DIA ("sábados" no rep 0; rep 3 com grafo
  sem "domingos"). Não é fechamento: é transcrição de dias em interesses
  paralelos (quartas + domingos) — defeito distinto, candidato a
  pré-registro novo se atacado.
- **Log do servidor B:** a guarda da chave única disparou 49× (comportamento
  esperado no protocolo — o candidato de fechamento usa MESMA chave da fase
  fechada; o estado novo entra por candidato separado) e **zero fallbacks
  do juiz** (nenhuma falha de parse/LLM na decisão).

## O que fica

- **Adotado**: `memory.llm.subject-closing` default `true` (env
  `MEMORY_SUBJECT_CLOSING` ainda reverte em emergência); suíte 123/123.
- `obsolete_old` segue **0/30 pelas 5 rodadas consecutivas**.
- Resíduo menor aberto: `futebol-abc` 2/5 (escorregões de dia em interesses
  paralelos) e custo do pacote (+129 s/caso vs controle nesta janela) —
  ambos releváveis para a próxima frente.
- Menu restante: (1) escorregões de dia no futebol (novo pré-registro),
  (2) janela truncada (regime não testado da hipótese central),
  (3) trajetória de 10 anos, (4) pausa.
