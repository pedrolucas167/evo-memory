# Dossiê: ablação da hipótese central (obsolescência)

**Status: confirmatório — hipótese NÃO demonstrada (empate a priori).**
Registro pré-execução:
[`../prereg/2026-10-19-ablacao-obsolescencia.md`](../prereg/2026-10-19-ablacao-obsolescencia.md)
(commit `93a6adf`, anterior a qualquer observação). Execução na branch
`feature/programa-pesquisa`, revision `c5edc5df58c0b971e7708e15848eeee2d87d6770`.

| Metadado | Valor |
|---|---|
| `run_id` | `9900a198d8f94a70972ead5675813081` |
| `dataset_sha256` | `bdeea8ab170bdbe421699f30133cd8911305a7033b51a9213ebcc05481013757` |
| Repetições | 5 (30 consultas por braço, 60 no total) |
| Duração total | 938,6 s |
| Modelos | chat/extração/juiz `openai/gpt-6-luna`; embeddings `baai/bge-m3` |
| Relatório bruto | `evals/results/obsolescence_9900a198d8f94a70972ead5675813081.json` (git-ignored, sha do dataset acima) |

## Dados

| Métrica | evo-memory | histórico completo |
|---|---|---|
| Respostas obsoletas | **0/30** | **0/30** |
| Respostas corretas (sem citar o antigo) | 26/30 | 28/30 |
| Respostas ambíguas (atual + antigo em contraste) | 4/30 | 2/30 |
| Falhas | 0/30 | 0/30 |
| Latência média por consulta | **29,6 s** | **1,6 s** |

Veredito por estudo de caso: `cidade-atual`, `cafe-preferido`, `futebol-dia`,
`gato-nome`, `cargo-trabalho` corretos 5/5 nos dois braços. O único caso com
divergência é `instrumento-musical`: o braço evo citou o fato antigo em 4 de
5 repetições e o baseline em 2 — sempre no mesmo padrão: **"Hoje você toca
piano; deixou o violão de lado"**. O juiz de verificação, por regra a priori,
classifica como ambíguo — nenhum é uso indevido do antigo; os dois braços
acertam o estado atual em todas.

## Decisão do critério pré-registrado

> Sustentada se A < B; refutada se A > B; empate (incluindo 0×0) = **não
> demonstrada**.

Contagem obsoleta 0×0 ⇒ **empate ⇒ hipótese central não demonstrada por este
experimento**. O critério foi decidido antes dos dados e o resultado entra
como está: o cenário de 7 turnos com modelo forte (`gpt-6-luna`) em prompt
neutro não produziu uso indevido de informação antiga em nenhum braço.

## Achados exploratórios (sem poder de decisão, sinalizados como novos registros)

1. **Custo real descoberto**: 29,6 s/consulta (evo) contra 1,6 s (baseline) —
   ~18× mais lento no pipeline completo. Para a hipótese valer a pena em
   produção, o ganho simulado teria que pagar isso. A diferença de custo
   também existe em chamadas LLM (o braço evo faz extração por turno + juiz);
   o parâmetro pré-registrado só exigia latência reportada.
2. **O caso `instrumento-musical` é o único que discrimina**: as duas
   memórias (piano/violão) coexistem no contexto dos dois braços e o modelo
   comenta a transição. Nada disso é obsolescência — é explicação —, mas o
   padrão repete principalmente no braço evo, provavelmente porque a memória
   semântica preserva o fato antigo na revisão e o contexto o apresenta
   nomeado.
3. O desfecho confirma a ameaça nº 4 do pré-registro: sem truncamento, a
   história de 7 turnos está longe do orçamento de contexto que estressaria
   o baseline. Recall perfeito dos dois lados em cenários curtos e latência
   18× pior — o desenho não alcança o regime onde a hipótese se separaria.

## Próxima iteração (nova pré-registra estrita, sem reuso deste resultado)

Cenários mais severos sugeridos pelo próprio desfecho, a definir em novo
arquivo `prereg/`: histórico longo o suficiente para truncar a janela
(30–50 turnos), perguntas que pedem explicitamente "a informação atual" com
distratores pesados, e medição formal de custo (chamadas LLM, tokens) no
protocolo. Nada disso aproveita este run como evidência de superioridade.

## Limitações

Mesmo as do pré-registro (verificador por marcador, prompt do baseline
conservador a favor do resultado nulo, `n` pequeno) — mais a nova descoberta
de latência, que muda o custo-benefício da hipótese mesmo em caso de futura
demonstração.
