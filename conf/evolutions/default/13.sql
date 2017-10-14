 ---

# --- !Ups

create table user_group (
  user_group_id bigint not null,
  name varchar(256) not null,
  constraint user_group_id_pk primary key (user_group_id)
);

alter table user_group
    add constraint user_group_name_unique unique (name);

create table user_group_member (
  user_group_id bigint not null,
  store_user_id bigint not null
);

alter table user_group_member
    add constraint user_group_member_unique unique (user_group_id, store_user_id);

alter table user_group_member
  add constraint user_group_member_store_user_id_fkey
  foreign key (store_user_id) references store_user(store_user_id);

create sequence user_group_seq start with 1;

# --- !Downs

drop table user_group_member;

drop table user_group;

drop sequence user_group_seq;
