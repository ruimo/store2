# ---

# --- !Ups

CREATE SEQUENCE file_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

# --- !Downs

drop sequence file_seq;
