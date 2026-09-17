create table education_levels (
    id bigserial primary key,
    name varchar(120) not null unique,
    max_courses integer not null check (max_courses between 1 and 10),
    sort_order integer not null default 0,
    active boolean not null default true
);

create table courses (
    id bigserial primary key,
    education_level_id bigint not null references education_levels(id) on delete restrict,
    number integer not null,
    name varchar(120) not null,
    active boolean not null default true,
    unique (education_level_id, number)
);

create table study_groups (
    id bigserial primary key,
    course_id bigint not null references courses(id) on delete restrict,
    name varchar(180) not null,
    active boolean not null default true,
    unique (course_id, name)
);

create table schedule_entries (
    id bigserial primary key,
    group_id bigint not null references study_groups(id) on delete restrict,
    day_of_week varchar(12) not null,
    start_time time not null,
    end_time time,
    subject varchar(255) not null,
    room varchar(120),
    teacher_name varchar(180),
    note varchar(500)
);
create index idx_schedule_group_day_time on schedule_entries(group_id, day_of_week, start_time);

create table user_preferences (
    id bigserial primary key,
    messenger varchar(20) not null,
    external_user_id varchar(100) not null,
    education_level_id bigint references education_levels(id) on delete set null,
    course_id bigint references courses(id) on delete set null,
    group_id bigint references study_groups(id) on delete set null,
    unique (messenger, external_user_id)
);

create table admin_users (
    id bigserial primary key,
    username varchar(100) not null unique,
    password_hash varchar(100) not null,
    enabled boolean not null default true
);
