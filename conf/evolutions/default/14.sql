 ---

# --- !Ups

create table third_party_token ( 
  third_party_token_id bigint not null,
  kind integer not null,
  store_user_id bigint not null,
  token text not null,
  expires timestamp,
  constraint third_party_token_pk primary key (third_party_token_id)
);

alter table third_party_token
    add constraint third_party_token_unique unique (kind, store_user_id);

create sequence third_party_token_seq start with 1;

# --- !Downs

drop table third_party_token;

drop sequence third_party_token_seq;
