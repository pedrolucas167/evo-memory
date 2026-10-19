# Dossiê: corte absoluto de similaridade — resultado negativo

**Pergunta**: um limiar absoluto de similaridade (`RETRIEVAL_MIN_SIMILARITY`)
mais alto separa a memória correta de distratores no caso alvo
(`profissao-por-situacao`)?

## Status

**Confirmatório-adversarial, refutado por medição** (executado antes do
protocolo, documentado sob os princípios atuais).

## Protocolo executado

Servidor local com `RETRIEVAL_MIN_SIMILARITY=0.41` (padrão era 0.38); demais
chaves de montagem intocadas (ficha `setup-ambiente.md`). Três rodadas do
mini-benchmark; análises determinísticas dos embeddings das variantes.

## Dados

1. Mini com limiar 0.41: **5/6, 6/6, 5/6** — o caso alvo oscila com a
   redação da extração, os demais nunca regrediram.
2. Embeddings medidos contra a pergunta: memória correta **0.4127**;
   distratores 0.4003 (paráfrase longa), **0.4169** (reformulação curta),
   0.3889 ("filhos"), 0.4267 (romance).

## Leitura honesta

Intervalo **invertido**: a reformulação curta do distrator (0.4169) supera a
memória correta (0.4127). Nenhum limiar absoluto mantém uma e exclui a outra.
O oscilar 5/6↔6/6 não é instabilidade do sistema de recuperação, mas da
redação que o extrator produz para a memória semeada ("aprendendo a nadar e
pratica três vezes por semana" → às vezes "aprendendo a nadar três vezes por
semana").

## Consequência

Candidatura "calibrar o limiar por conjunto" refutada. Com o corte
espectral já refutado, restava julgamento semântico no momento da consulta —
o terceiro experimento (modelo juiz).

## Limitações

Um único limiar testado (0.41) com três rodadas; a leitura geometrica usa
uma amostra pequena de variantes redigidas manualmente; sem teste de
significância.
