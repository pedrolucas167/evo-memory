-- Data até a qual uma memória (ex.: um plano) continua válida. Nula quando não se aplica.
alter table semantic_memory add column valid_until date;
