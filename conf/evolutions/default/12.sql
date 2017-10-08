 ---

# --- !Ups

alter table employee add column index integer not null default 1;

ALTER TABLE employee
    add CONSTRAINT employee_site_id_store_user_id_key unique (site_id, store_user_id);

ALTER TABLE employee
    add CONSTRAINT employee_store_user_id_index_key unique (store_user_id, index);

# --- !Downs

alter table drop constraint employee_site_id_store_user_id_key;
alter table drop constraint employee_store_user_id_index_key;

alter table employee drop column index;
