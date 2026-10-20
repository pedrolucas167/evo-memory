# Pré-registro v2: ablação de obsolescência em regime de orçamento (A→B→C)

**Registro pré-execução.** Renascimento do registro
`2026-10-19-ablacao-obsolescencia.md` (empate 0×0, hipótese não demonstrada).
Corrige o desenho conforme os achados exploratórios do primeiro run — sem
aproveitar nenhum número daquele run como evidência de superioridade.

- Data de registro: 19/10/2026
- Tipo: confirmatório (segunda tentativa formal da hipótese central)
- Montagem: a vigente em `setup-ambiente.md` (nenhuma mudança de montagem
  desde a ficha; o relatório confirma via endpoint de configuração).

## Por que uma segunda operacionalização

Motivação do novo desenho, declarada a priori (pós-dados do run 1,
pré-dados deste registro):

1. Run 1 apontou: 7 turnos estão longe do regime de contexto em que a
   hipótese separaria os braços;
2. perguntas com "atual"/"hoje" dão ao baseline a pista de recência —
   queremos medição sem pista;
3. custo real de latência (18×) precisa virar métrica de protocolo.

## Hipótese sob teste (a mesma, inalterada)

> Atualizações explícitas e versionamento de memória semântica reduzem o uso
> de informações obsoletas nas respostas do assistente, sob orçamento de
> contexto controlado.

## Operacionalização v2

Para cada cenário, história de **30 turnos**:

1. **Fato A** (turno 1);
2. **13 turnos de enchimento neutro** rotacionados de um pool fixo
   (determinístico, idêntico nos dois braços);
3. **Mudança 1**: usuário contradiz A e declara B (correção explícita);
4. **7 turnos de enchimento**;
5. **Mudança 2**: contradiz B e declara C (o "estado final");
6. **Mais 7 turnos de enchimento**;
7. **Consulta neutra de estado** — sem "atual", sem "hoje", sem
   "agora": formulação que NÃO dá pista de recência a nenhum braço.

O fato passa por **duas mudanças encadeadas (A→B→C)** dentro de uma única
história: a hipótese de decisão é a versão de estado C, e o regime adverso
do baseline recencia B como estado mais recente anotado até a mudança 2.

Braço A (evo-memory): turnos via `POST /api/conversations/{user}`
(pipeline completo), consulta via `POST /api/assistant/{user}`, configuração
padrão vigente. Braço B (histórico completo): as mesmas 31 mensagens cruas
(30 turnos + consulta) via chat completion direta, system prompt neutro —
o mesmo do run 1 (viés conservador declarado).

Usuário sintético novo por (cenário, repetição) nos dois braços.

## Métricas (por consulta)

Marcadores por cenário: `expected` (C), `intermediate` (B), `old` (A),
normalizados (minúsculas, sem acento):

- **correta**: contém C, sem A e sem B;
- **obsoleta-intermediária**: contém B, sem C (uso do estado "penúltimo");
- **obsoleta-antiga**: contém A, sem C e sem B;
- **ambígua**: contém C e (A ou B) — explicação histórica de transição;
- **falha**: nenhum marcador.

Métrica primária de decisão: **taxa de resposta obsoleta** =
(intermediária + antiga) / nº de consultas do braço. Secundárias reportadas
sem decisão: corretas, ambíguas, falhas, decomposição intermediária/antiga,
**custo** (latência média medida; chamadas LLM contadas exatamente no
baseline e **estimadas** no braço evo como turnos×(1–2) + 2 — extração por
turno e, quando o assunto já existe, juiz de evolução; consulta com juiz de
relevância; marcado como estimativa; tokens de
entrada/saída do braço baseline lidos do `usage` da API), e **atribuição de
mecanismo** no braço evo (estado do grafo após semeadura: C presente e A/B
ausentes), diagnóstico puro para separar falha de evolução de falha de
resposta — nunca critério de decisão.

## Amostra

`n` = 6 cenários × 5 repetições × 2 braços = **30 consultas por braço**,
60 no total, sequenciais na mesma montagem.

## Critérios de decisão (a priori, idênticos em espírito ao v1)

- **Sustentada**: contagem de respostas obsoletas (intermediária + antiga)
  do braço evo **estritamente menor** que a do baseline.
- **Refutada**: contagem do braço evo **estritamente maior**.
- **Não demonstrada (empate)**: contagens iguais, incluindo 0×0 — segundo
  empate 0×0 consecutivo encerra a tentativa confirmatória desta
  operacionalização; a hipótese permanece não demonstrada e a próxima
  iteração precisa de desenho radicalmente diferente (ex.: truncamento real
  de janela) ou o programa declara a operacionalização esgotada.

Verificação intermediária proibida: sem leitura de resultados parciais antes
de o run fechar (60/60).

## Ameaças à validade conhecidas a priori

1. Marcador por substring bruta; padrão de "explicar transição" contado como
   ambíguo, regra simétrica.
2. System prompt do baseline diz "prefer explicit corrections and the most
   recent facts" — favorece C; conservador para a hipótese.
3. Extração não determinística no braço evo: relatório reporta, não silencia.
4. 30 turnos ainda cabem na janela do chat model — o estresse vem de
   densidade de distratores + dupla mudança, não de truncamento real.
5. `n` = 30/braço sem teste de significância formal; ±1 caso pode ser ruído.
6. Mesma modelagem de modelo nos braços, trilhas de chamada distintas.

## Proibições pós-dados

As do v1 permanecem (não trocar nenhum parâmetro entre braços após
resultados parciais; proibição de somar runs; métrica primária imutável).
Resultado imprevisto → exploratório + novo pré-registro.

## Custo previsto

Braço evo: ~30 chamadas de extração + ~2 evoluções + 1 consulta (com juiz)
por (cenário, repetição) ≈ 1000+ chamadas; braço baseline: 30 consultas
com contexto longo. Duração estimada total: 1–2 h em background.
