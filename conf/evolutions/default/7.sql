# ---

# --- !Ups

alter table uploaded_file add column category_name character varying(16) default '';

alter table uploaded_file alter column file_name type varchar(2048);

CREATE INDEX uploaded_file_store_user_id ON uploaded_file (store_user_id);
CREATE INDEX uploaded_file_file_name ON uploaded_file (file_name);
CREATE INDEX uploaded_file_created_time ON uploaded_file (created_time);

# --- !Downs

alter table uploaded_file drop column category_name;
