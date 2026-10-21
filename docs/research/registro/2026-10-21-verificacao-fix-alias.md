# Verificação viva do fix de resolução de alias (2026-10-21)

**Natureza:** verificação de regressão de engenharia, exploratória, 1 amostra, sem uso de decisão.
**Fix em teste:** commit `8048dfc` (branch `fix/resolucao-alias-assunto`) — resolução de alias
determinística (`SubjectMatcher`) + veredito `UNRELATED` do juiz + fila de até 3 probes em
`MemoryService.observe`.
**Código do medidor:** `evals/diagnostic_state_flag.py` (semeia o caso `gato-abc` do dataset
`obsolescence_v2_cases.json`, 30 turnos, layout 13/7/7, e despeja o grafo).
**Servidor:** local, porta 8091, código novo (pós-fix); Postgres via docker compose (5433).
**Usuário de teste:** `diagnostic-gato-9e8e8c2c2d204de9ad8bf3181991b574` (semeadura nova, sem
mistura com runs anteriores).

## Caso semeado (`gato-abc`)

- `initial`: "Tenho uma gata chamada Mel." (A)
- `change1`: "A Mel morreu e adotei um gato chamado Tobi." (B)
- `change2`: "O Tobi fugiu; adotei uma gata chamada Nina para substituir ele." (C)
- `question`: "Qual o nome do meu gato?" — esperado: **Nina**

## Grafo final pós-fix

```
[B]   kind=FACT  "Adotou um gato chamado Tobi, que fugiu."        (subject gato-tobi, conf 1.0, version 2)
[C,A] kind=FACT  "A gata Mel morreu, e adotou uma gata chamada Nina."
[+]  4 fillers (PLAN/FACT/PROJECT) sem marcador
```

Comparação com o diagnóstico pré-fix (dossiê 2026-10-20): as três linhas paralelas de posse
(A/B/C como assuntos separados por nome de entidade) viraram **duas** linhas — a morte da Mel
e a adoção da Nina foram **fundidas na mesma linha** pelo encadeamento de alias, e a linha do
Tobi evoluiu (version 2) para carregar o evento da fuga.

## Resposta de estado (pergunta real, pipeline completo)

```
answer: "O nome do seu gato é Tobi."   ← ERRADO (esperado Nina)
context: apenas a linha [B] (gato-tobi, conf 1.0) — a linha [C,A] NÃO foi recuperada
```

## Decomposição honesta

1. **Encadeamento sob renomeação: funciona.** O passo A→B encadeou via citação de assunto
   ("mel" aparece na observação; gate de identidade do `SubjectMatcher`), e o passo C fundiu
   estado novo na linha correta. O defeito mapeado no dossiê v2 (linhas paralelas por chave de
   entidade, cadeia de contradição morta) está fechado, confirmado em chave real do extrator.
2. **O sintoma (resposta errada) persiste, agora por outra via: recall.** A pergunta
   "Qual o nome do meu **gato**?" casa literalmente com a linha do Tobi ("gato chamado Tobi",
   conf 1.0) e o vetorizador/ranqueador não trouxe a linha `[C,A]` (que fala de "gata" e
   "Nina") para o contexto. Sem a linha certa no contexto, nenhuma síntese pode acertar.
3. **Duas linhas em vez de uma.** A linha `[B]` está semanticamente correta (Tobi foi adotado
   e fugiu — evento de partida), mas compete com a linha de estado atual e vence no recall por
   similaridade literal + confiança.

## Conclusão

O fix de alias é **necessário e verificado** (13 testes determinísticos + encadeamento vivo),
mas **não suficiente** para acertar a resposta de estado: a frente restante é **resolução de
conflito no recall** (quando duas linhas ativas do mesmo domínio competem, o recall precisa
discriminar estado atual de histórico/evento). Isto é um novo defeito mapeado, não uma
regressão do fix.

**Próxima frente candidata:** critério de conflito/recall com discriminação temporal — por
pré-registro próprio antes de qualquer observação nova.
