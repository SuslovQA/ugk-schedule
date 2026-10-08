create table bot_polling_state (
    stream varchar(80) primary key,
    cursor_value bigint
);
create table bot_updates (
    id bigserial primary key,
    stream varchar(80) not null references bot_polling_state(stream),
    event_key varchar(64) not null,
    user_key varchar(100) not null,
    payload text,
    attempts integer not null default 0,
    available_at timestamptz not null default current_timestamp,
    created_at timestamptz not null default current_timestamp,
    completed_at timestamptz,
    last_failure varchar(120),
    unique (stream, event_key)
);
create index idx_bot_updates_pending on bot_updates(stream, available_at, id) where completed_at is null;
create index idx_bot_updates_completed on bot_updates(completed_at) where completed_at is not null;
create index idx_bot_updates_user on bot_updates(stream, user_key, id) where completed_at is null;
