# ---

# --- !Ups

alter table news column store_user_id bigint default null;

# --- !Downs

alter table news drop column store_user_id;
