# Dossiê — Frente 2: fechamento em transição declarada (`closing-split`) — 2026-10-30

**Pré-registro:** `prereg/2026-10-30-closing-split.md` (congelado antes da
primeira observação do run de decisão; PR #33 → `develop` = `dd3224c`,
PR #34 → `main` = `a8ad274`, CI verde).
**Resultado: ADOTADO pelo critério a priori.**

## Execução

- Revisão idêntica nos dois braços (revisão da frente 2, `dd3224c`/`a8ad274`);
  a ÚNICA diferença entre braços é `MEMORY_CLOSING_SPLIT` (braço B ligou a
  chave no boot, confirmada por sonda HTTP com comportamento novo visível).
- Modelos confirmados antes de cada run via `/api/experiments/{id}/configuration`
  (`gpt-6-luna` + `bge-m3`); medidor v2 e base de casos **intocados**
  (`run_id` próprio por braço; nada somado entre runs).
- Rodada sem abortos. Braço A gravou às 20:25 e braço B às 21:24
  (30/10); janela do provedor **rápida e estável** (165,2 s/caso no A e
  110,8 s/caso no B — curiosidade: o braço B com a regra extra no prompt
  rodou mais rápido que o controle; a janela manda, não o mecanismo).
- Diagnóstico design-time (nunca entrou em critério): usuários dedicados
  `diag-dia-10/11` mostraram a perda — fase retrai e o estado novo é
  descartado pela guarda da chave única quando o extrator colapsa a
  mensagem num único candidato de fechamento.

## Números congelados

Braço A (`run_id 00053136526b472c915a9d94b29d4fcc`, controle, chave off) e
braço B (`run_id dbdf3fe8c76549f9aa917ba290111a18`, tratamento, chave on).
Arquivos crus (git-ignorados) em `evals/results/obsolescence_v2_*.json`.

Braço evo-memory (30 observações por braço):

| classe | A (controle) | B (tratamento) |
|---|---|---|
| **state_correct** | **25 (0,8333)** | **29 (0,9667)** |
| correct (resposta) | 26 | 30 |
| obsolete_intermediate | 0 | 0 |
| obsolete_old | 0 | 0 |
| ambiguous | 3 | 0 |
| failed | 1 | 0 |
| latência média de caso | 165,2 s | 110,8 s |
| latência média de consulta | 5,32 s | 6,18 s |
| chamadas LLM estimadas | 1798 | 1860 |

Baseline full-history dos mesmos runs (nunca somado entre runs):
A 23 correct / 7 ambiguous / 0 failed; B 25 correct / 5 ambiguous / 0 failed.

## Aplicação dos critérios (a priori, pré-registro 30/10)

- Δ `state_correct` = 29 − 25 = **+4/30** → acima do limiar de ganho
  (≥ +3/30).
- Soma de classes de falha: A = 1 (`failed` instrumento rep 4); B = 0 →
  Δ **−1/30** ≤ teto +2/30 (melhorou).
- **Decisão: ADOTADO** — `memory.llm.closing-split` vira padrão
  (`true` no yml; env `MEMORY_CLOSING_SPLIT=false` reverte em emergência).

## Matriz por caso (evo, state_correct por repetição)

| caso | A | B | nota |
|---|---|---|---|
| cidade-abc | 5/5 | 5/5 | |
| cafe-abc | 4/5 (rep3) | 4/5 (rep2) | escorregão simétrico em cada braço |
| futebol-abc | 4/5 (rep3) | **5/5** | caso-alvo da frente |
| gato-abc | 3/5 (rep0, rep4) | **5/5** | |
| cargo-abc | 5/5 | 5/5 | |
| instrumento-abc | 4/5 (rep4 `failed`) | 5/5 | |

## Evidência de mecanismo

- **Sonda do braço B (boot do servidor de tratamento):** após
  "Jogo futebol aos sabados." + "Mudei meu jogo: de sabado para quarta
  agora.", o grafo ativo tem UMA única memória —
  `futebol-as-quartas` "Joga futebol às quartas-feiras." — com a fase
  `futebol-aos-sabados` retraída em revisão. Esse é o desenho do pacote:
  fechamento pela MESMA chave + estado novo com chave própria.
- **Respostas do caso-alvo no run B:** "Você joga futebol às
  quartas-feiras." nas 5 repetições — a fase do domingo, que sobrevivia em
  15/20 das tentativas nos braços históricos, sumiu; nada de "sábados" nem
  "domingos" no grafo/resposta.
- **Log do servidor B:** guarda da chave única disparou 50× (comportamento
  esperado — o candidato de fechamento usa a MESMA chave da fase fechada);
  **zero** fallbacks do juiz e **zero** "não relacionada" na chave exata.
- O ruído do caso cafe (B rep 2: resposta correta, grafo sujo) é fratura
  conhecida de janela do provedor (o controle A também perdeu cafe rep 3
  no mesmo dia); no estado de hoje não indica regressão da frente —
  fica marcado para observação em rodadas futuras, sem critério novo.

## O que fica

- **Adotado**: `memory.llm.closing-split` default `true` com o fechamento de
  assunto como base (env `MEMORY_CLOSING_SPLIT=false` reverte em
  emergência); suíte 125/125.
- `obsolete_old` segue **0/30 pelas 6 rodadas consecutivas**.
- Custo do pacote: zero chamada LLM nova (a regra é texto no prompt
  existente); latência da janela estabilizou a comparação (B mais rápido
  que A neste par).

## Menu restante

1. **Janela truncada** — o único regime não testado da hipótese central
   (ablações v1/v2 terminaram em janelas completas).
2. **Trajetória de usuário em 10 anos** — ideia do proprietário (medidor
   episódico novo, desenho próprio).
3. **Pausa.**
