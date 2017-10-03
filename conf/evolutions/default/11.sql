# ---

# --- !Ups

CREATE TABLE uploaded_directory (
  uploaded_directory_id bigint not null,
  store_user_id bigint NOT NULL,
  file_name character varying(2048) not null,
  category_name character varying(16) not null,
  created_time timestamp not null default current_timestamp,
  constraint pk_uploaded_directory primary key (uploaded_directory_id)
);

ALTER TABLE uploaded_directory
    ADD CONSTRAINT uploaded_directory_file_name_unique UNIQUE (file_name);

alter table uploaded_file add column uploaded_directory_id bigint;

ALTER TABLE uploaded_file
  ADD CONSTRAINT uploaded_file_uploaded_directory_id_fkey
  FOREIGN KEY (uploaded_directory_id) REFERENCES uploaded_directory(uploaded_directory_id);

create table directory_path (
    ancestor bigint NOT NULL,
    descendant bigint NOT NULL,
    path_length integer NOT NULL
);

ALTER TABLE directory_path
    ADD CONSTRAINT directory_path_pkey PRIMARY KEY (ancestor, descendant);

CREATE INDEX ix_directory_path1 ON directory_path (ancestor);

CREATE INDEX ix_directory_path2 ON directory_path (descendant);

CREATE INDEX ix_directory_path3 ON directory_path (path_length);

ALTER TABLE directory_path
    ADD CONSTRAINT directory_path_ancestor_fkey
    FOREIGN KEY (ancestor) REFERENCES uploaded_directory(uploaded_directory_id) ON DELETE CASCADE;

ALTER TABLE directory_path
    ADD CONSTRAINT directory_path_descendant_fkey
    FOREIGN KEY (descendant) REFERENCES uploaded_directory(uploaded_directory_id) ON DELETE CASCADE;

CREATE SEQUENCE directory_path_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

# --- !Downs

drop table directory_path;

alter table uploaded_file drop column uploaded_directory_id;

drop table uploaded_directory;

drop sequence directory_path_seq;
