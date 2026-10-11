-- Experiências observadas (memória episódica)
create table episode (
    id              uuid primary key,
    user_id         varchar(100) not null,
    conversation_id varchar(100) not null,
    summary         text         not null,
    source          varchar(20)  not null,
    occurred_at     timestamptz  not null,
    created_at      timestamptz  not null default now()
);

create index idx_episode_user_time on episode (user_id, occurred_at);

-- Conhecimentos reutilizáveis sobre o usuário (memória semântica, estado atual)
create table semantic_memory (
    id                 uuid primary key,
    user_id            varchar(100) not null,
    kind               varchar(30)  not null,
    subject_key        varchar(200) not null,
    content            text         not null,
    confidence         numeric(4,3) not null check (confidence >= 0 and confidence <= 1),
    status             varchar(20)  not null,
    evidence_count     integer      not null default 1,
    version            integer      not null default 1,
    first_seen_at      timestamptz  not null,
    last_confirmed_at  timestamptz  not null,
    updated_at         timestamptz  not null,
    source_episode_id  uuid references episode (id),
    constraint uq_semantic_memory_subject unique (user_id, subject_key)
);

-- Histórico de mudanças de cada memória (modelo evolutivo)
create table memory_revision (
    id                   uuid primary key,
    memory_id            uuid         not null references semantic_memory (id),
    version              integer      not null,
    change_type          varchar(20)  not null,
    previous_content     text,
    new_content          text         not null,
    previous_confidence  numeric(4,3),
    new_confidence       numeric(4,3) not null,
    episode_id           uuid references episode (id),
    reason               text,
    changed_at           timestamptz  not null
);

create index idx_memory_revision_memory on memory_revision (memory_id, version);
