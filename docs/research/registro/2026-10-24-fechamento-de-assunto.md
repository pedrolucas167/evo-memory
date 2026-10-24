# Dossiê — Fechamento de assunto no grafo (frente 1, evolução) — 2026-10-24

**Pré-registro:** `prereg/2026-10-22-fechamento-de-assunto.md` (congelado antes
da primeira observação; commit `cc8fd78`, branch `feature/fechamento-de-assunto`,
PR #23 → `develop` = `0893af4`).
**Resultado: critério de adoção NÃO satisfeito — o ganho primário veio
exatamente no limiar (+3/30), mas a regressão de falha foi +8/30 (limiar
+2/30). NÃO ADOTA.** Não é o "Refutado" do pré-registro (o delta primário é
positivo, não negativo); a frente segue aberta como operacionalização de
1 rodada não adotada.

## Execução

- Código idêntico nos dois braços (`develop` = `0893af4`, árvore limpa); a
  ÚNICA diferença entre braços é `MEMORY_SUBJECT_CLOSING` (A desligado,
  B ligado) — a ablação mais limpa da série.
- Modelos confirmados via `/api/experiments/{id}/configuration` antes de cada
  run (`gpt-6-luna` + `bge-m3`).
- Nota de execução: o braço B teve 3 tentativas — as duas primeiras abortadas
  por reinício da máquina (22/10 ~15:53 e 23/10), sem dados (medidor grava
  só no fim; reinício do zero com `run_id` novo em cada tentativa). Janela de
  execução: braço A em 22/10, braço B em 23–24/10.
- Provedor instável na janela: latência média de caso 508,5 s (A) e **1771,0 s
  (B)** contra ~105 s das rodadas de 21–22/10; no rep 4 do braço B três casos
  evo falharam em sequência E o baseline full-history falhou em futebol
  (evidência externa de que parte das `failed` de B é ruído do provedor, não
  efeito do tratamento). Os critérios a priori contam as falhas como medidas,
  independente da causa — relatado como medido, com o contexto declarado.

## Números congelados

Braço A (`run_id 69d598aa0db44426bd841eb0c8d56478`, controle) e braço B
(`run_id 6686b0756b784b6789fd5c0faa52942d`, tratamento). Arquivos crus
(git-ignorados): `evals/results/obsolescence_v2_69d598aa0db44426bd841eb0c8d56478.json`
e `evals/results/obsolescence_v2_6686b0756b784b6789fd5c0faa52942d.json`.

Braço evo-memory (30 observações por braço):

| classe | A (controle) | B (fechamento) |
|---|---|---|
| **state_correct** | **11 (0,3667)** | **14 (0,4667)** |
| correct (resposta) | 16 | 14 |
| obsolete_intermediate | 0 | **4** |
| obsolete_old | 0 | 0 |
| ambiguous | 14 | 8 |
| failed | 0 | **4** |
| latência média de consulta | 5,77 s | 6,59 s |
| latência média de caso | 508,5 s | 1771,0 s |
| chamadas LLM estimadas | 1860 | 1612 (falhas cortam cenários) |

Baseline full-history dos mesmos runs (nunca somado entre runs): A 24 correct /
6 ambiguous / 0 failed; B 25 correct / 4 ambiguous / 1 failed (futebol rep 4).

## Aplicação dos critérios (a priori)

- `state_correct(B) − state_correct(A) = 14 − 11 = +3/30` → satisfaz o limiar
  de ganho (≥ +3/30), **no limite exato**.
- Soma de classes de falha (`obsolete_intermediate + obsolete_old + failed`):
  A = 0; B = 8 → **+8/30, acima do teto de +2/30 → critério de adoção
  VIOLADO**.
- **Decisão: NÃO ADOTA** (critério conjuntivo). A chave
  `MEMORY_SUBJECT_CLOSING` permanece disponível com padrão `false`;
  nenhum comportamento padrão muda.
- A frente 1 NÃO encerra pelo rito (encerramento por empates exige 2
  consecutivos; aqui o delta primário é positivo). É a primeira vez desde o
  início do programa que um tratamento produz delta primário positivo no
  medidor.

## Matriz por caso (evo, state_correct por repetição)

| caso | A | B |
|---|---|---|
| cidade-abc | 5/5 | 5/5 |
| cafe-abc | 5/5 | 5/5 |
| cargo-abc | 1/5 | **4/5** |
| futebol-abc | 0/5 | 0/5 |
| gato-abc | 0/5 | 0/5 |
| instrumento-abc | 0/5 | 0/5 |

O ganho inteiro (+3/30) vem de `cargo-abc` (1→4). `gato-abc` contribuiu zero
nos dois braços.

## Evidência de mecanismo nos textos de resposta (dados crus)

- **cargo-abc (fechamento funcionou de ponta a ponta):** B responde "Você
  trabalha como engenheira de produto." (4/5) — sem narrar a transição ("Antes,
  era engenheira de dados", padrão do braço A, desapareceu). O juiz fechou as
  fases de cargo, a extração escreveu estado puro, o grafo ativo ficou limpo.
- **gato-abc (fechamento falhou no elo do alias):** B responde "Seu gato se
  chama Tobi." em 4/5 (classe `obsolete_intermediate` nova). Leitura
  EXPLORATÓRIA (hipótese, não decisão): no turno 3 ("Tobi fugiu; adotei
  Nina"), o candidato da extração provavelmente não cita "tobi" e não
  compartilha suficientes tokens com a linha "Adotou um gato chamado
  Tobi." → a fila de alias não sonda → o veredito CLOSED nunca é avaliado →
  a fase de Tobi nunca fecha e vence o recall (a fase de Mel ainda fechou no
  turno 2, onde a morte cita "mel"). O fechamento só dispara quando o
  candidato pousar na linha a fechar — o gap é a PROPOSTA de alias, não o
  veredito: cargo fechou porque a extração reutilizou a chave; gato não
  fechou porque trocou de chave sem mencionar a antiga.
- **Evidência do log do servidor B (23/10, 15:17):** a guarda determinística
  da chave única disparou — "Estado novo fechou assunto com a mesma chave da
  fase encerrada; sem nova linha" — o extrator reutilizou a `subject_key` da
  fase fechada em ao menos uma observação e, pela regra do pré-registro, o
  estado novo não nasceu nela. Complementa o gap: (a) às vezes o candidato
  nem sonda a fase (sem menção), (b) às vezes fecha com a MESMA chave e a
  linha nova é abortada — dois comportamentos do extrator/juiz que a frente
  1.1 deve fechar, sem mudança post-hoc nesta rodada.

## O que fica

- **NÃO ADOTA**; chave `MEMORY_SUBJECT_CLOSING` disponível (padrão `false`),
  123/123 testes, CI verde em `develop`/`main` (tag `v0.1.0` já cobre o código
  da frente).
- **Frente 1 segue aberta** com uma rodada registrada (ganho primário real,
  regressão real): a próxima rodada — se houver — é uma NOVA pré-registro
  (frente 1.1) mirando o gap de alias descrito acima (sonda de fechamento sem
  menção literal), nunca uma rodada extra "para conferir".
- `obsolete_old` segue **0/30** pela 4ª rodada consecutiva do medidor v2.
- Abertos (menu): (1) frente 1.1 — fechamento sem menção (novo pré-registro),
  (2) janela truncada — único regime não testado da hipótese central,
  (3) trajetória de 10 anos, (4) pausa.
