# ---

# --- !Ups

create table news_category (
  news_category_id bigint not null,
  category_name character varying(64),
  icon_url text,
  constraint pk_news_category primary key (news_category_id)
);

CREATE SEQUENCE news_category_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

alter table news add column news_category_id bigint default null;

ALTER TABLE news
    ADD CONSTRAINT news_category_id_fkey FOREIGN KEY (news_category_id) REFERENCES news_category(news_category_id);

# --- !Downs

drop sequence news_category_seq;

drop table news_category;

alter table news drop column news_category_id;
