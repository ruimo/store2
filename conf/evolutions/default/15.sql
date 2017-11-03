 ---

# --- !Ups

create table favo ( 
  favo_id bigint not null,
  kind integer not null,
  content_id bigint not null,
  store_user_id bigint not null,
  constraint favo_pk primary key (favo_id)
);

alter table favo
    add constraint favo_kind_content_id_store_user_id_unique unique (kind, content_id, store_user_id);

create sequence favo_seq start with 1;

# --- !Downs

drop table favo;

drop sequence favo_seq;
