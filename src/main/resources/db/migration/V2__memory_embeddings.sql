create extension if not exists vector;

alter table semantic_memory add column embedding vector(1024);
alter table semantic_memory add column embedding_model varchar(100);

create index idx_semantic_memory_embedding on semantic_memory using hnsw (embedding vector_cosine_ops);
