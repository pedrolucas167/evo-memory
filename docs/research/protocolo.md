# Protocolo de pesquisa do Evo Memory

Este documento é a carta do programa experimental. Nenhum experimento do
projeto foge dele; exceções precisam ser registradas no próprio experimento
com justificativa.

## Princípios

1. **Medir ≥ decidir.** Nenhuma alavanca entra ou sai da base de código padrão
   sem números de benchmark publicados no README. Intuição motiva hipóteses,
   nunca decisões.
2. **Pré-registro.** Antes da primeira observação de um experimento, o
   registro em `prereg/` define hipótese, operacionalização, `n`, métricas e
   critérios de decisão. O commit do registro precede o primeiro run (a ordem
   é auditável pelo histórico do git). Rodar antes e escrever depois é
   proibido: nesse caso, o experimento entra como exploratório, nunca como
   confirmatório.
3. **Honestidade completa.** Resultados negativos são resultados. Toda rodada
   publicada inclui contagens brutas, falhas, custos e limitações; nada é
   descartado por "não ter funcionado".
4. **Replicação mínima.** Decisões de comportamento (ligar/desligar padrões,
   trocar rankings) exigem no mínimo 3 rodadas independentes do conjunto
   decisivo, a menos que o registro declare outra coisa.
5. **Report completo de efeito.** Além de acerto/erro: taxa por caso,
   tamanho da diferença, `n` de cada lado, custo e latência medidos.
   Sem significância estatística formal quando `n` é pequeno — o relatório
   declara o risco de ruído explicitamente.
6. **Dado versionado.** Datasets de benchmark vivem no git (`evals/*.json`),
   e todo relatório grava `dataset_sha256`, estado do código (`revision`,
   `dirty`, `source_sha256`) e a configuração dos modelos lida do endpoint
   `/api/experiments/*/configuration` na hora da execução.
7. **Ficha da montagem antes do experimento.** `setup-ambiente.md` descreve
   ambiente, modelos e chaves de configuração; qualquer mudança de montagem
   invalida comparações entre rodadas e exige nova coluna na tabela do
   registro.
8. **Domínio puro, medidor puro.** Os scripts de benchmark não importam nada
   do código de produção; acessam o sistema apenas por HTTP autenticado ou
   pela API do provedor de LLM (caso do baseline). Medidor que lê estado
   interno é quantificador contaminado.

## Fluência de um experimento

1. Ideia anotada na seção candidata do README ou issue.
2. Pré-registro criado e commitado.
3. Implementação do medidor (se necessário) e semeadura do dataset.
4. Execução com montagem registrada; relatório bruto em `evals/results/`
   (git-ignored, sha256 citado no dossiê).
5. Dossiê em `registro/` com protocolo executado, tabelas, leitura honesta e
   limitações.
6. README atualizado com a conclusão e, quando aplicável, mudança de padrão.
7. Commit referenciando o registro e o relatório.

## Estrutura de diretórios

- `prereg/` — registros pré-execução (imutáveis após o first run; correções
  viram rascunho novo referenciando o antigo).
- `registro/` — dossiês pós-execução, um por experimento, referenciando o
  registro e os relatórios brutos.
- `setup-ambiente.md` — montagem vigente e histórico de mudanças de montagem.
