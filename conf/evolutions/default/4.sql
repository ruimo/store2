# ---

# --- !Ups

create table uploaded_file (
  uploaded_file_id bigint not null,
  store_user_id bigint NOT NULL,
  file_name text not null,
  content_type text,
  created_time timestamp not null default current_timestamp,
  constraint pk_image primary key (uploaded_file_id)
);

ALTER TABLE uploaded_file
  ADD CONSTRAINT uploaded_file_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id);

CREATE SEQUENCE uploaded_file_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

# --- !Downs

drop sequence uploaded_file_seq;

drop table uploaded_file;
