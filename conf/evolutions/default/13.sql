 ---

# --- !Ups

create table user_group (
  user_group_id bigint not null,
  name varchar(256) not null,
  constraint user_group_id_pk primary key (user_group_id)
);

create table user_group_member (
  user_group_id bigint not null,
  store_user_id bigint not null
);

ALTER TABLE user_group_member
    ADD CONSTRAINT user_group_member_unique UNIQUE (user_group_id, store_user_id);

ALTER TABLE user_group_member
  ADD CONSTRAINT user_group_member_store_user_id_fkey
  FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id);

create sequence user_group_seq start with 1;

# --- !Downs

drop table user_group_member;

drop table user_group;

drop sequence user_group_seq;
