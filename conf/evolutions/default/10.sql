# ---

# --- !Ups

ALTER TABLE employee
    drop CONSTRAINT employee_store_user_id_key;

# --- !Downs

ALTER TABLE employee
    ADD CONSTRAINT employee_store_user_id_key UNIQUE (store_user_id);
