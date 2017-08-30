# ---

# --- !Ups

CREATE TABLE user_metadata (
    user_metadata_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    photo_url text,
    first_name_kana character varying(64),
    middle_name_kana character varying(64),
    last_name_kana character varying(64),
    tel_no0 character varying(64),
    tel_no1 character varying(64),
    tel_no2 character varying(64),
    joined_date timestamp,
    birth_month_day integer, -- mmdd
    profile_comment text
);

ALTER TABLE user_metadata
    ADD CONSTRAINT user_metadata_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;
    
CREATE INDEX user_metadata_joined_date ON user_metadata (joined_date);
CREATE INDEX user_metadata_birth_month_day ON user_metadata (birth_month_day);

CREATE SEQUENCE user_metadata_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

# --- !Downs

drop table user_metadata;

drop sequence user_metadata_seq;
