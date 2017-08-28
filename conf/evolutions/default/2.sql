# ---

# --- !Ups

alter table news add column store_user_id bigint default null;

# --- !Downs

alter table news drop column store_user_id;
