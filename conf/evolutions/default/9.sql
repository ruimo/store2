# ---

# --- !Ups

alter table user_metadata add constraint user_metadata_pk primary key(user_metadata_id);

create unique index user_metadata_store_user_id on user_metadata(store_user_id);

# --- !Downs

alter table user_metadata drop constraint user_metadata_pk;

drop index user_metadata_store_user_id;
