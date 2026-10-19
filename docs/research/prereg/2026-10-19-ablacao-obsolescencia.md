# Pré-registro: hipótese central por ablação de obsolescência

**Registro pré-execução.** Assinado por commit imediatamente anterior ao
primeiro run (ver histórico do git). Qualquer alteração posterior invalida o
caráter confirmatório; correções devem renascer em novo arquivo citando este.

- Data de registro: 19/10/2026
- Tipo: confirmatório (única hipótese central do projeto, ainda não
  demonstrada — ver README)

## Hipótese sob teste

> Atualizações explícitas e versionamento de memória semântica reduzem o uso
> de informações obsoletas nas respostas do assistente, sob orçamento de
> contexto controlado.

## Operacionalização

Histórias sintéticas em português, cada uma com:

1. **Fato inicial** do usuário (preferência, fato pessoal, rotina ou plano);
2. **3 turnos de enchimento neutro** (conversa comum, sem relações com o
   fato), para estressar o orçamento de contexto — a hipótese diferencia os
   sistemas justamente quando a história cresce;
3. **Mudança explícita**: o usuário contradiz/*corrige* o fato inicial em
   linguagem natural;
4. **2 turnos de enchimento** após a mudança;
5. **Consulta** sobre o estado atual do fato.

A mesma história crua alimenta os dois braços:

- **Braço A (evo-memory)**: turnos enviados via `POST /api/conversations/{user}`
  (pipeline completo: extração, evolução, esquecimentos), consulta via
  `POST /api/assistant/{user}` com a configuração padrão entregue no
  `application.yml` vigente.
- **Braço B (histórico completo)**: API de chat completion diretamente com os
  turnos crus e o system prompt neutro do benchmark
  ("Use the supplied conversation history…"), mesmo modelo de chat do braço A.
  Este é o baseline comum de memórias de assistente: nada de estado — apenas
  a conversa inteira no contexto.

Um usuário sintético novo por (cenário, repetição) nos dois braços.

## Métricas (por consulta, por resposta de texto)

Marcadores `expected` (fato atual) e `obsolete` (fato antigo) declarados por
cenário no dataset; texto normalizado (minúsculas, sem acento):

- **resposta correta**: contém `expected`;
- **resposta obsoleta**: contém `obsolete` **e** não contém `expected`;
- **resposta ambígua**: contém ambos (ex: respondeu o estado atual e citou o
  antigo para contrastar — não é uso indevido);
- **falha**: não contém nenhum, ou erro do braço.

Métrica de decisão: **taxa de resposta obsoleta** = (respostas obsoletas +
respostas ambíguas classificadas separadamente, contando apenas obsoletas
estritas) / nº de consultas do braço. Métricas secundárias reportadas sem
critério de decisão: corretas, ambíguas, falhas, latência média por consulta.

## Amostra

`n` = 6 cenários × 5 repetições × 2 braços = 30 consultas por braço,
60 no total. Repetições executadas na mesma montagem, sequenciais.

## Critérios de decisão (a priori)

- **Sustentada**: contagem de respostas obsoletas do braço A **estritamente
  menor** que a do braço B (0 ≤ A < B ≤ 30).
- **Refutada**: contagem do braço A **estritamente maior** que a do braço B.
- **Não demonstrada (empate)**: contagens iguais — incluindo o empate 0/30 ×
  0/30, em que o cenário de teste não consegue distinguir os braços e a
  hipótese permanece sem demonstração (o README continua honesto).

Critérios secundários sem poder de decisão, mas registrados: corretas por
braço, latência, custo em número de chamadas LLM.

## Ameaças à validade conhecidas a priori

1. **Verificação por marcador é bruta**: respostas que explicam a mudança
   ("antes você X, hoje Y") contam como ambíguas, não obsoletas — regra
   simétrica para os dois braços.
2. **Prompt do braço B é o mesmo do benchmark temporal**, que já orienta
   "prefer explicit corrections and the most recent facts" — favorece o
   baseline, tende contra a sustentação da hipótese (viés conservador).
3. **Extração não determinística no braço A**: cada semeadura pode reformular
   a memória; o efeito sobre marcadores é mitigado porque a pergunta e os
   marcadores antigos/novos são fatos objetivos, mas o ruído existe e reflui
   em latência/extração colateral (princípio 5: reportar, não silenciar).
4. **Sem truncamento forçado**: a história de ~7 turnos cabe na janela do
   modelo de chat; o teste não aproxima limite real de contexto, apenas
   estressa o raciocínio sobre estado.
5. **n = 30 por braço, sem teste de significância formal**: diferenças de ±1
   caso podem ser ruído; o relatório apresentará todas as contagens e
   evitará verbalizar força além do suportado.
6. **Braços usam a mesma modelagem de modelo** (`gpt-6-luna`), mas trilhas
   diferentes de chamadas (system prompts distintos por design — cada
   sistema com seu melhor prompt).

## Proibições pós-dados (anti-p-hacking)

- Não trocar modelo, prompt ou parâmetros de recuperação entre braços depois
  de ver resultados parciais.
- Não reexecutar casos que falharam e somar com a rodada anterior (princípio
  de `run_id` único).
- Não publicar métrica primária diferente desta após os dados chegarem;
  resultados imprevistos viram **exploratórios** e geram novo pré-registro.

## Custo previsto

~7 chamadas LLM por caso do braço A (extração por turno + consulta; juiz de
relevância quando ≥2 candidatos) e 2 chamadas do braço B por caso, com
repetições. Estimativa total: ~330 chamadas.
