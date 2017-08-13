# --- First database schema

# --- !Ups
-- CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;

CREATE TABLE address (
    address_id bigint NOT NULL,
    country_code integer NOT NULL,
    first_name character varying(64) NOT NULL,
    middle_name character varying(64) NOT NULL,
    last_name character varying(64) NOT NULL,
    first_name_kana character varying(64) NOT NULL,
    last_name_kana character varying(64) NOT NULL,
    zip1 character varying(32) NOT NULL,
    zip2 character varying(32) NOT NULL,
    zip3 character varying(32) NOT NULL,
    prefecture integer NOT NULL,
    address1 character varying(256) NOT NULL,
    address2 character varying(256) NOT NULL,
    address3 character varying(256) NOT NULL,
    address4 character varying(256) NOT NULL,
    address5 character varying(256) NOT NULL,
    tel1 character varying(32) NOT NULL,
    tel2 character varying(32) NOT NULL,
    tel3 character varying(32) NOT NULL,
    comment text DEFAULT ''::text NOT NULL,
    email character varying(255) DEFAULT ''::character varying NOT NULL
);

ALTER TABLE address
    ADD CONSTRAINT pk_address PRIMARY KEY (address_id);

CREATE SEQUENCE address_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE category (
    category_id bigint NOT NULL,
    category_code character varying(20) DEFAULT NULL::character varying NOT NULL
);

COMMENT ON TABLE category IS 'Category information. Each item has one category.';
COMMENT ON COLUMN category.category_id IS 'Surrogate key.';

ALTER TABLE category
    ADD CONSTRAINT category_code_unique UNIQUE (category_code);

ALTER TABLE category
    ADD CONSTRAINT pk_category PRIMARY KEY (category_id);

CREATE TABLE category_name (
    locale_id bigint NOT NULL,
    category_name character varying(32) NOT NULL,
    category_id bigint NOT NULL
);

ALTER TABLE category_name
    ADD CONSTRAINT pk_category_name PRIMARY KEY (locale_id, category_id);

CREATE INDEX ix_category_name1 ON category_name (category_id);

CREATE TABLE category_path (
    ancestor bigint NOT NULL,
    descendant bigint NOT NULL,
    path_length integer NOT NULL
);

ALTER TABLE category_path
    ADD CONSTRAINT category_path_pkey PRIMARY KEY (ancestor, descendant);

CREATE INDEX ix_category_path1 ON category_path (ancestor);

CREATE INDEX ix_category_path2 ON category_path (descendant);

CREATE INDEX ix_category_path3 ON category_path (path_length);

CREATE SEQUENCE category_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE coupon (
    coupon_id bigint NOT NULL,
    deleted boolean NOT NULL
);

ALTER TABLE coupon
    ADD CONSTRAINT pk_coupon PRIMARY KEY (coupon_id);

CREATE SEQUENCE coupon_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE coupon_item (
    coupon_item_id bigint NOT NULL,
    item_id bigint NOT NULL,
    coupon_id bigint NOT NULL
);

ALTER TABLE coupon_item
    ADD CONSTRAINT coupon_item_coupon_id_key UNIQUE (coupon_id);

ALTER TABLE coupon_item
    ADD CONSTRAINT coupon_item_item_id_key UNIQUE (item_id);

ALTER TABLE coupon_item
    ADD CONSTRAINT pk_coupon_item PRIMARY KEY (coupon_item_id);

CREATE SEQUENCE coupon_item_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE currency (
    currency_id bigint NOT NULL,
    currency_code character varying(3) NOT NULL
);

ALTER TABLE currency
    ADD CONSTRAINT pk_currency PRIMARY KEY (currency_id);

CREATE TABLE employee (
    employee_id bigint NOT NULL,
    site_id bigint NOT NULL,
    store_user_id bigint NOT NULL
);

ALTER TABLE employee
    ADD CONSTRAINT employee_store_user_id_key UNIQUE (store_user_id);

ALTER TABLE employee
    ADD CONSTRAINT pk_employee PRIMARY KEY (employee_id);

CREATE SEQUENCE employee_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item (
    item_id bigint NOT NULL,
    category_id bigint NOT NULL
);

ALTER TABLE item
    ADD CONSTRAINT pk_item PRIMARY KEY (item_id);

CREATE SEQUENCE item_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_description (
    item_description_id bigint NOT NULL,
    locale_id bigint NOT NULL,
    description text NOT NULL,
    item_id bigint NOT NULL,
    site_id bigint NOT NULL
);

ALTER TABLE item_description
    ADD CONSTRAINT pk_item_description PRIMARY KEY (item_description_id);

ALTER TABLE item_description
    ADD CONSTRAINT item_description_locale_id_item_id_site_id_key UNIQUE (locale_id, item_id, site_id);

CREATE INDEX ix_item_description1 ON item_description (item_id);

CREATE SEQUENCE item_description_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_inquiry (
    item_inquiry_id bigint NOT NULL,
    site_id bigint NOT NULL,
    item_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    inquiry_type integer NOT NULL,
    submit_user_name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    status integer NOT NULL,
    created timestamp NOT NULL
);

ALTER TABLE item_inquiry
    ADD CONSTRAINT pk_item_inquiry PRIMARY KEY (item_inquiry_id);

CREATE SEQUENCE item_inquiry_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_inquiry_field (
    item_inquiry_field_id bigint NOT NULL,
    item_inquiry_id bigint NOT NULL,
    field_name character varying(80) NOT NULL,
    field_value text NOT NULL
);

ALTER TABLE item_inquiry_field
    ADD CONSTRAINT item_inquiry_field_item_inquiry_id_field_name_key UNIQUE (item_inquiry_id, field_name);

ALTER TABLE item_inquiry_field
    ADD CONSTRAINT pk_item_inquiry_field PRIMARY KEY (item_inquiry_field_id);

CREATE INDEX ix_item_inquiry_field1 ON item_inquiry_field (item_inquiry_id);

CREATE SEQUENCE item_inquiry_field_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_name (
    item_name_id bigint NOT NULL,
    locale_id bigint NOT NULL,
    item_id bigint NOT NULL,
    item_name text NOT NULL
);

ALTER TABLE item_name
    ADD CONSTRAINT item_name_locale_id_item_id_key UNIQUE (locale_id, item_id);

ALTER TABLE item_name
    ADD CONSTRAINT pk_item_name PRIMARY KEY (item_name_id);

CREATE INDEX ix_item_name1 ON item_name (item_id);

CREATE SEQUENCE item_name_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_numeric_metadata (
    item_numeric_metadata_id bigint NOT NULL,
    item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata bigint
);

ALTER TABLE item_numeric_metadata
    ADD CONSTRAINT item_numeric_metadata_item_id_metadata_type_key UNIQUE (item_id, metadata_type);

ALTER TABLE item_numeric_metadata
    ADD CONSTRAINT pk_item_numeric_metadata PRIMARY KEY (item_numeric_metadata_id);

CREATE SEQUENCE item_numeric_metadata_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_price (
    item_price_id bigint NOT NULL,
    site_id bigint NOT NULL,
    item_id bigint NOT NULL
);

ALTER TABLE item_price
    ADD CONSTRAINT item_price_site_id_item_id_key UNIQUE (site_id, item_id);

ALTER TABLE item_price
    ADD CONSTRAINT pk_item_price PRIMARY KEY (item_price_id);

CREATE SEQUENCE item_price_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_price_history (
    item_price_history_id bigint NOT NULL,
    item_price_id bigint NOT NULL,
    tax_id bigint NOT NULL,
    currency_id bigint NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    valid_until timestamp NOT NULL,
    cost_price numeric(15,2) DEFAULT 0 NOT NULL,
    list_price numeric(15,2)
);

ALTER TABLE item_price_history
    ADD CONSTRAINT item_price_history_item_price_id_valid_until_key UNIQUE (item_price_id, valid_until);

ALTER TABLE item_price_history
    ADD CONSTRAINT pk_item_price_history PRIMARY KEY (item_price_history_id);

CREATE SEQUENCE item_price_history_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE item_text_metadata (
    item_text_metadata_id bigint NOT NULL,
    item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata text
);

ALTER TABLE item_text_metadata
    ADD CONSTRAINT item_text_metadata_item_id_metadata_type_key UNIQUE (item_id, metadata_type);

ALTER TABLE item_text_metadata
    ADD CONSTRAINT pk_item_text_metadata PRIMARY KEY (item_text_metadata_id);

CREATE SEQUENCE item_text_metadata_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE locale (
    locale_id bigint NOT NULL,
    lang character varying(8) NOT NULL,
    country character varying(3) DEFAULT ''::character varying NOT NULL,
    precedence integer NOT NULL
);

COMMENT ON TABLE locale IS 'Locale information.';
COMMENT ON COLUMN locale.locale_id IS 'Surrogate key.';
COMMENT ON COLUMN locale.lang IS 'ISO 639 lang code such as ja, en.';
COMMENT ON COLUMN locale.country IS 'ISO 3166 country code such as JP, US.';
COMMENT ON COLUMN locale.precedence IS 'Precedence. If requested locale is not registed in this table, the record having greatest precedence will be used instead.';

ALTER TABLE locale
    ADD CONSTRAINT locale_lang_country_key UNIQUE (lang, country);

ALTER TABLE locale
    ADD CONSTRAINT pk_locale PRIMARY KEY (locale_id);

CREATE TABLE news (
    news_id bigint NOT NULL,
    site_id bigint,
    title text NOT NULL,
    contents text NOT NULL,
    release_time timestamp NOT NULL,
    updated_time timestamp NOT NULL
);

COMMENT ON COLUMN news.site_id IS 'Owner site of this news or null if administrator message.';

ALTER TABLE news
    ADD CONSTRAINT pk_news PRIMARY KEY (news_id);

CREATE INDEX ix_news01 ON news (release_time);

CREATE SEQUENCE news_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE order_notification (
    order_notification_id bigint NOT NULL,
    store_user_id bigint NOT NULL
);

ALTER TABLE order_notification
    ADD CONSTRAINT order_notification_store_user_id_key UNIQUE (store_user_id);

ALTER TABLE order_notification
    ADD CONSTRAINT pk_order_notification PRIMARY KEY (order_notification_id);

CREATE SEQUENCE order_notification_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE password_dict (
    password character varying(24) NOT NULL
);

ALTER TABLE password_dict
    ADD CONSTRAINT pk_password_dict PRIMARY KEY (password);

CREATE TABLE recommend_by_admin (
    recommend_by_admin_id bigint NOT NULL,
    site_id bigint NOT NULL,
    item_id bigint NOT NULL,
    score bigint NOT NULL,
    enabled boolean NOT NULL
);

ALTER TABLE recommend_by_admin
    ADD CONSTRAINT pk_recommend_by_admin PRIMARY KEY (recommend_by_admin_id);

ALTER TABLE recommend_by_admin
    ADD CONSTRAINT recommend_by_admin_site_id_item_id_key UNIQUE (site_id, item_id);

CREATE INDEX ix_recommend_by_admin1 ON recommend_by_admin (score);

CREATE SEQUENCE recommend_by_admin_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE reset_password (
    reset_password_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    token bigint NOT NULL,
    reset_time timestamp NOT NULL
);

ALTER TABLE reset_password
    ADD CONSTRAINT pk_reset_password PRIMARY KEY (reset_password_id);

ALTER TABLE reset_password
    ADD CONSTRAINT reset_password_store_user_id_key UNIQUE (store_user_id);

CREATE SEQUENCE reset_password_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE shipping_address_history (
    shipping_address_history_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    address_id bigint NOT NULL,
    updated_time timestamp NOT NULL
);

ALTER TABLE shipping_address_history
    ADD CONSTRAINT pk_shipping_address_history PRIMARY KEY (shipping_address_history_id);

ALTER TABLE shipping_address_history
    ADD CONSTRAINT shipping_address_history_store_user_id_address_id_updated_t_key UNIQUE (store_user_id, address_id, updated_time);

CREATE SEQUENCE shipping_address_history_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE shipping_box (
    shipping_box_id bigint NOT NULL,
    site_id bigint NOT NULL,
    item_class bigint NOT NULL,
    box_size integer NOT NULL,
    box_name character varying(32) NOT NULL
);

ALTER TABLE shipping_box
    ADD CONSTRAINT pk_shipping_box PRIMARY KEY (shipping_box_id);

ALTER TABLE shipping_box
    ADD CONSTRAINT shipping_box_site_id_item_class_key UNIQUE (site_id, item_class);

CREATE SEQUENCE shipping_box_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE shipping_fee (
    shipping_fee_id bigint NOT NULL,
    shipping_box_id bigint NOT NULL,
    country_code integer NOT NULL,
    location_code integer NOT NULL
);

ALTER TABLE shipping_fee
    ADD CONSTRAINT pk_shipping_fee PRIMARY KEY (shipping_fee_id);

ALTER TABLE shipping_fee
    ADD CONSTRAINT shipping_fee_constraint1 UNIQUE (shipping_box_id, country_code, location_code);

CREATE SEQUENCE shipping_fee_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE shipping_fee_history (
    shipping_fee_history_id bigint NOT NULL,
    shipping_fee_id bigint NOT NULL,
    tax_id bigint NOT NULL,
    fee numeric(15,2) NOT NULL,
    valid_until timestamp NOT NULL,
    cost_fee numeric(15,2) DEFAULT NULL::numeric
);

ALTER TABLE shipping_fee_history
    ADD CONSTRAINT pk_shipping_fee_history PRIMARY KEY (shipping_fee_history_id);

ALTER TABLE shipping_fee_history
    ADD CONSTRAINT shipping_fee_history_shipping_fee_id_valid_until_key UNIQUE (shipping_fee_id, valid_until);

CREATE INDEX ix_shipping_fee_history1 ON shipping_fee_history (shipping_fee_id);

CREATE SEQUENCE shipping_fee_history_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE shopping_cart_item (
    shopping_cart_item_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    seq integer NOT NULL,
    site_id bigint NOT NULL,
    item_id bigint NOT NULL,
    quantity integer NOT NULL
);

ALTER TABLE shopping_cart_item
    ADD CONSTRAINT pk_shopping_cart_item PRIMARY KEY (shopping_cart_item_id);

ALTER TABLE shopping_cart_item
    ADD CONSTRAINT shopping_cart_item_store_user_id_seq_key UNIQUE (store_user_id, seq);

CREATE INDEX ix_shopping_cart_item1 ON shopping_cart_item (item_id);

CREATE SEQUENCE shopping_cart_item_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE shopping_cart_shipping (
    shopping_cart_shipping_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    site_id bigint NOT NULL,
    shipping_date timestamp NOT NULL
);

ALTER TABLE shopping_cart_shipping
    ADD CONSTRAINT pk_shopping_cart_shipping PRIMARY KEY (shopping_cart_shipping_id);

ALTER TABLE shopping_cart_shipping
    ADD CONSTRAINT shopping_cart_shipping_store_user_id_site_id_key UNIQUE (store_user_id, site_id);

CREATE SEQUENCE shopping_cart_shipping_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE site (
    site_id bigint NOT NULL,
    locale_id bigint NOT NULL,
    site_name character varying(32) NOT NULL,
    deleted boolean DEFAULT false NOT NULL
);

COMMENT ON TABLE site IS 'Site information. Site expresses store. In this application, each site treates one locale each other.';
COMMENT ON COLUMN site.site_id IS 'Surrogate key.';
COMMENT ON COLUMN site.locale_id IS 'Locale.';
COMMENT ON COLUMN site.site_name IS 'Name of this site.';

ALTER TABLE site
    ADD CONSTRAINT pk_site PRIMARY KEY (site_id);

ALTER TABLE site
    ADD CONSTRAINT site_site_name_key UNIQUE (site_name);

CREATE SEQUENCE site_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE site_category (
    category_id bigint NOT NULL,
    site_id bigint NOT NULL
);

ALTER TABLE site_category
    ADD CONSTRAINT pk_site_category PRIMARY KEY (category_id, site_id);

CREATE TABLE site_item (
    item_id bigint NOT NULL,
    site_id bigint NOT NULL,
    created timestamp DEFAULT now() NOT NULL
);

ALTER TABLE site_item
    ADD CONSTRAINT pk_site_item PRIMARY KEY (item_id, site_id);

CREATE TABLE site_item_numeric_metadata (
    site_item_numeric_metadata_id bigint NOT NULL,
    site_id bigint NOT NULL,
    item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata bigint,
    valid_until timestamp DEFAULT '9999-12-31 00:00:00'::timestamp NOT NULL
);

ALTER TABLE site_item_numeric_metadata
    ADD CONSTRAINT pk_site_item_numeric_metadata PRIMARY KEY (site_item_numeric_metadata_id);

CREATE SEQUENCE site_item_numeric_metadata_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE site_item_text_metadata (
    site_item_text_metadata_id bigint NOT NULL,
    site_id bigint NOT NULL,
    item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata text
);

ALTER TABLE site_item_text_metadata
    ADD CONSTRAINT pk_site_item_text_metadata PRIMARY KEY (site_item_text_metadata_id);

ALTER TABLE site_item_text_metadata
    ADD CONSTRAINT site_item_text_metadata_site_id_item_id_metadata_type_key UNIQUE (site_id, item_id, metadata_type);

CREATE SEQUENCE site_item_text_metadata_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE site_user (
    site_user_id bigint NOT NULL,
    site_id bigint NOT NULL,
    store_user_id bigint NOT NULL
);

ALTER TABLE site_user
    ADD CONSTRAINT pk_site_user PRIMARY KEY (site_user_id);

ALTER TABLE site_user
    ADD CONSTRAINT site_user_site_id_store_user_id_key UNIQUE (site_id, store_user_id);

CREATE SEQUENCE site_user_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE store_user (
    store_user_id bigint NOT NULL,
    user_name character varying(64) NOT NULL,
    first_name character varying(64) NOT NULL,
    middle_name character varying(64),
    last_name character varying(64) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash bigint NOT NULL,
    salt bigint NOT NULL,
    deleted boolean NOT NULL,
    user_role integer NOT NULL,
    company_name character varying(64),
    created_time timestamp DEFAULT now(),
    stretch_count integer DEFAULT 1 NOT NULL,
    CONSTRAINT user_user_role_check1 CHECK (user_role in (0,1,2,3))
);

ALTER TABLE store_user
    ADD CONSTRAINT pk_user PRIMARY KEY (store_user_id);

ALTER TABLE store_user
    ADD CONSTRAINT store_user_user_name_key UNIQUE (user_name);

CREATE INDEX ix_store_user1 ON store_user (created_time);

CREATE INDEX ix_store_user2 ON store_user (user_role);

CREATE SEQUENCE store_user_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE supplemental_category (
    item_id bigint NOT NULL,
    category_id bigint NOT NULL
);

ALTER TABLE supplemental_category
    ADD CONSTRAINT supplemental_category_item_id_category_id_key UNIQUE (item_id, category_id);

CREATE TABLE supplemental_user_email (
    supplemental_user_email_id bigint NOT NULL,
    email character varying(255) NOT NULL,
    store_user_id bigint NOT NULL
);

ALTER TABLE supplemental_user_email
    ADD CONSTRAINT pk_supplemental_user_email PRIMARY KEY (supplemental_user_email_id);

CREATE INDEX ix_supplemental_user_email ON supplemental_user_email (store_user_id);

CREATE SEQUENCE supplemental_user_email_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE tax (
    tax_id bigint NOT NULL
);

ALTER TABLE tax
    ADD CONSTRAINT pk_tax PRIMARY KEY (tax_id);

CREATE SEQUENCE tax_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE tax_history (
    tax_history_id bigint NOT NULL,
    tax_id bigint NOT NULL,
    tax_type integer NOT NULL,
    rate numeric(5,3) NOT NULL,
    valid_until timestamp NOT NULL,
    CONSTRAINT tax_history_check1 CHECK (tax_type in (0,1,2))
);

ALTER TABLE tax_history
    ADD CONSTRAINT pk_tax_history PRIMARY KEY (tax_history_id);

ALTER TABLE tax_history
    ADD CONSTRAINT tax_history_tax_id_valid_until_key UNIQUE (tax_id, valid_until);

CREATE INDEX ix_tax_history1 ON tax_history (valid_until);

CREATE SEQUENCE tax_history_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE tax_name (
    tax_name_id bigint NOT NULL,
    tax_id bigint NOT NULL,
    locale_id bigint NOT NULL,
    tax_name character varying(32) NOT NULL
);

ALTER TABLE tax_name
    ADD CONSTRAINT pk_tax_name PRIMARY KEY (tax_name_id);

ALTER TABLE tax_name
    ADD CONSTRAINT tax_name_tax_id_locale_id_key UNIQUE (tax_id, locale_id);

CREATE SEQUENCE tax_name_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_coupon (
    transaction_coupon_id bigint NOT NULL,
    transaction_item_id bigint NOT NULL,
    coupon_id bigint NOT NULL
);

ALTER TABLE transaction_coupon
    ADD CONSTRAINT pk_transaction_coupon PRIMARY KEY (transaction_coupon_id);

CREATE SEQUENCE transaction_coupon_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_credit_tender (
    transaction_credit_tender_id bigint NOT NULL,
    transaction_id bigint NOT NULL,
    amount numeric(15,2) NOT NULL
);

ALTER TABLE transaction_credit_tender
    ADD CONSTRAINT pk_transaction_credit_tender PRIMARY KEY (transaction_credit_tender_id);

CREATE SEQUENCE transaction_credit_tender_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_header (
    transaction_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    transaction_time timestamp NOT NULL,
    currency_id bigint NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    tax_amount numeric(15,2) NOT NULL,
    transaction_type integer NOT NULL
);

ALTER TABLE transaction_header
    ADD CONSTRAINT pk_transaction PRIMARY KEY (transaction_id);

CREATE SEQUENCE transaction_header_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_item (
    transaction_item_id bigint NOT NULL,
    transaction_site_id bigint NOT NULL,
    item_id bigint NOT NULL,
    item_price_history_id bigint NOT NULL,
    quantity integer NOT NULL,
    amount numeric(15,2) NOT NULL,
    cost_price numeric(15,2) DEFAULT 0 NOT NULL,
    tax_id bigint DEFAULT 2 NOT NULL
);

ALTER TABLE transaction_item
    ADD CONSTRAINT pk_transaction_item PRIMARY KEY (transaction_item_id);

CREATE SEQUENCE transaction_item_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_item_numeric_metadata (
    transaction_item_numeric_metadata_id bigint NOT NULL,
    transaction_item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata bigint
);

ALTER TABLE transaction_item_numeric_metadata
    ADD CONSTRAINT pk_transaction_item_numeric_metadata PRIMARY KEY (transaction_item_numeric_metadata_id);

ALTER TABLE transaction_item_numeric_metadata
    ADD CONSTRAINT transaction_item_numeric_meta_transaction_item_id_metadata__key UNIQUE (transaction_item_id, metadata_type);

CREATE SEQUENCE transaction_item_numeric_metadata_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_item_text_metadata (
    transaction_item_text_metadata_id bigint NOT NULL,
    transaction_item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata text
);

ALTER TABLE transaction_item_text_metadata
    ADD CONSTRAINT pk_transaction_item_text_metadata PRIMARY KEY (transaction_item_text_metadata_id);

ALTER TABLE transaction_item_text_metadata
    ADD CONSTRAINT transaction_item_text_metadat_transaction_item_id_metadata__key UNIQUE (transaction_item_id, metadata_type);

CREATE SEQUENCE transaction_item_text_metadata_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_paypal_status (
    transaction_paypal_status_id bigint NOT NULL,
    transaction_id bigint NOT NULL,
    status integer NOT NULL,
    token bigint NOT NULL,
    payment_type integer NOT NULL
);

ALTER TABLE transaction_paypal_status
    ADD CONSTRAINT pk_transaction_paypal_status PRIMARY KEY (transaction_paypal_status_id);

ALTER TABLE transaction_paypal_status
    ADD CONSTRAINT transaction_paypal_status_transaction_id_key UNIQUE (transaction_id);

CREATE SEQUENCE transaction_paypal_status_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_shipping (
    transaction_shipping_id bigint NOT NULL,
    transaction_site_id bigint NOT NULL,
    amount numeric(15,2) NOT NULL,
    address_id bigint NOT NULL,
    item_class bigint NOT NULL,
    box_size integer NOT NULL,
    tax_id bigint NOT NULL,
    shipping_date timestamp DEFAULT '1970-01-01 00:00:00'::timestamp NOT NULL,
    box_count integer DEFAULT 1 NOT NULL,
    box_name character varying(32) DEFAULT '-'::character varying NOT NULL,
    cost_amount numeric(15,2) DEFAULT NULL::numeric
);

ALTER TABLE transaction_shipping
    ADD CONSTRAINT pk_transaction_shipping PRIMARY KEY (transaction_shipping_id);

CREATE SEQUENCE transaction_shipping_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_site (
    transaction_site_id bigint NOT NULL,
    transaction_id bigint NOT NULL,
    site_id bigint NOT NULL,
    total_amount numeric(15,2) NOT NULL,
    tax_amount numeric(15,2) NOT NULL
);

ALTER TABLE transaction_site
    ADD CONSTRAINT pk_transaction_site PRIMARY KEY (transaction_site_id);

ALTER TABLE transaction_site
    ADD CONSTRAINT transaction_site_transaction_id_site_id_key UNIQUE (transaction_id, site_id);

CREATE SEQUENCE transaction_site_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_site_item_numeric_metadata (
    transaction_site_item_numeric_metadata_id bigint NOT NULL,
    transaction_item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata bigint
);

ALTER TABLE transaction_site_item_numeric_metadata
    ADD CONSTRAINT pk_transaction_site_item_numeric_metadata PRIMARY KEY (transaction_site_item_numeric_metadata_id);

ALTER TABLE transaction_site_item_numeric_metadata
    ADD CONSTRAINT transaction_site_item_numeric_transaction_item_id_metadata__key UNIQUE (transaction_item_id, metadata_type);

CREATE UNIQUE INDEX site_item_numeric_metadata_u ON site_item_numeric_metadata (site_id, item_id, metadata_type, valid_until);

CREATE SEQUENCE transaction_site_item_numeric_metadata_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_site_item_text_metadata (
    transaction_site_item_text_metadata_id bigint NOT NULL,
    transaction_item_id bigint NOT NULL,
    metadata_type integer NOT NULL,
    metadata text
);

ALTER TABLE transaction_site_item_text_metadata
    ADD CONSTRAINT pk_transaction_site_item_text_metadata PRIMARY KEY (transaction_site_item_text_metadata_id);

ALTER TABLE transaction_site_item_text_metadata
    ADD CONSTRAINT transaction_site_item_text_me_transaction_item_id_metadata__key UNIQUE (transaction_item_id, metadata_type);

CREATE SEQUENCE transaction_site_item_text_metadata_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_status (
    transaction_status_id bigint NOT NULL,
    transaction_site_id bigint NOT NULL,
    status integer NOT NULL,
    transporter_id bigint,
    slip_code varchar(128) default null,
    last_update timestamp DEFAULT now() NOT NULL,
    mail_sent boolean DEFAULT false NOT NULL,
    planned_shipping_date timestamp,
    planned_delivery_date timestamp 
);

ALTER TABLE transaction_status
    ADD CONSTRAINT pk_transaction_status PRIMARY KEY (transaction_status_id);

ALTER TABLE transaction_status
    ADD CONSTRAINT transaction_status_transaction_site_id_key UNIQUE (transaction_site_id);

CREATE SEQUENCE transaction_status_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transaction_tax (
    transaction_tax_id bigint NOT NULL,
    transaction_site_id bigint NOT NULL,
    tax_id bigint NOT NULL,
    tax_type integer NOT NULL,
    rate numeric(5,3) NOT NULL,
    target_amount numeric(15,2) NOT NULL,
    amount numeric(15,2) NOT NULL
);

ALTER TABLE transaction_tax
    ADD CONSTRAINT pk_transaction_tax PRIMARY KEY (transaction_tax_id);

CREATE SEQUENCE transaction_tax_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transporter (
    transporter_id bigint NOT NULL
);

ALTER TABLE transporter
    ADD CONSTRAINT pk_transporter PRIMARY KEY (transporter_id);

CREATE SEQUENCE transporter_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE transporter_name (
    transporter_name_id bigint NOT NULL,
    locale_id bigint NOT NULL,
    transporter_id bigint NOT NULL,
    transporter_name character varying(64)
);

ALTER TABLE transporter_name
    ADD CONSTRAINT pk_transporter_name PRIMARY KEY (transporter_name_id);

ALTER TABLE transporter_name
    ADD CONSTRAINT transporter_name_locale_id_transporter_id_key UNIQUE (locale_id, transporter_id);

ALTER TABLE transporter_name
    ADD CONSTRAINT transporter_name_transporter_name_key UNIQUE (transporter_name);

CREATE SEQUENCE transporter_name_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE user_address (
    user_address_id bigint NOT NULL,
    store_user_id bigint NOT NULL,
    address_id bigint NOT NULL,
    seq integer NOT NULL
);

ALTER TABLE user_address
    ADD CONSTRAINT pk_user_address PRIMARY KEY (user_address_id);

ALTER TABLE user_address
    ADD CONSTRAINT user_address_store_user_id_address_id_key UNIQUE (store_user_id, address_id);

ALTER TABLE user_address
    ADD CONSTRAINT user_address_store_user_id_address_id_seq_key UNIQUE (store_user_id, address_id, seq);

CREATE INDEX user_address1 ON user_address (store_user_id);

CREATE SEQUENCE user_address_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE web_snippet (
    web_snippet_id bigint NOT NULL,
    site_id bigint NOT NULL,
    content_code character varying(16) NOT NULL,
    content text NOT NULL,
    updated_time timestamp NOT NULL
);

ALTER TABLE web_snippet
    ADD CONSTRAINT pk_web_snippet PRIMARY KEY (web_snippet_id);

CREATE SEQUENCE web_snippet_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE category_name
    ADD CONSTRAINT category_name_category_id_fkey FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE CASCADE;

ALTER TABLE category_name
    ADD CONSTRAINT category_name_locale_id_fkey FOREIGN KEY (locale_id) REFERENCES locale(locale_id);

ALTER TABLE category_path
    ADD CONSTRAINT category_path_ancestor_fkey FOREIGN KEY (ancestor) REFERENCES category(category_id) ON DELETE CASCADE;

ALTER TABLE category_path
    ADD CONSTRAINT category_path_descendant_fkey FOREIGN KEY (descendant) REFERENCES category(category_id) ON DELETE CASCADE;

ALTER TABLE coupon_item
    ADD CONSTRAINT coupon_item_coupon_id_fkey FOREIGN KEY (coupon_id) REFERENCES coupon(coupon_id) ON DELETE CASCADE;

ALTER TABLE coupon_item
    ADD CONSTRAINT coupon_item_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE employee
    ADD CONSTRAINT employee_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id);

ALTER TABLE employee
    ADD CONSTRAINT employee_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id);

ALTER TABLE item
    ADD CONSTRAINT item_category_id_fkey FOREIGN KEY (category_id) REFERENCES category(category_id);

ALTER TABLE item_description
    ADD CONSTRAINT item_description_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE item_description
    ADD CONSTRAINT item_description_locale_id_fkey FOREIGN KEY (locale_id) REFERENCES locale(locale_id);

ALTER TABLE item_description
    ADD CONSTRAINT item_description_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE item_inquiry
    ADD CONSTRAINT item_inquiry_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id);

ALTER TABLE item_inquiry
    ADD CONSTRAINT item_inquiry_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id);

ALTER TABLE item_inquiry
    ADD CONSTRAINT item_inquiry_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id);

ALTER TABLE item_inquiry_field
    ADD CONSTRAINT item_inquiry_field_item_inquiry_id_fkey FOREIGN KEY (item_inquiry_id) REFERENCES item_inquiry(item_inquiry_id);

ALTER TABLE item_name
    ADD CONSTRAINT item_name_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE item_name
    ADD CONSTRAINT item_name_locale_id_fkey FOREIGN KEY (locale_id) REFERENCES locale(locale_id);

ALTER TABLE item_numeric_metadata
    ADD CONSTRAINT item_numeric_metadata_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id);

ALTER TABLE item_price
    ADD CONSTRAINT item_price_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE item_price
    ADD CONSTRAINT item_price_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE item_price_history
    ADD CONSTRAINT item_price_history_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES currency(currency_id) ON DELETE CASCADE;

ALTER TABLE item_price_history
    ADD CONSTRAINT item_price_history_item_price_id_fkey FOREIGN KEY (item_price_id) REFERENCES item_price(item_price_id) ON DELETE CASCADE;

ALTER TABLE item_price_history
    ADD CONSTRAINT item_price_history_tax_id_fkey FOREIGN KEY (tax_id) REFERENCES tax(tax_id) ON DELETE CASCADE;

ALTER TABLE item_text_metadata
    ADD CONSTRAINT item_text_metadata_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id);

ALTER TABLE news
    ADD CONSTRAINT news_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE order_notification
    ADD CONSTRAINT order_notification_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id);

ALTER TABLE recommend_by_admin
    ADD CONSTRAINT recommend_by_admin_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE recommend_by_admin
    ADD CONSTRAINT recommend_by_admin_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE reset_password
    ADD CONSTRAINT reset_password_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE shipping_address_history
    ADD CONSTRAINT shipping_address_history_address_id_fkey FOREIGN KEY (address_id) REFERENCES address(address_id) ON DELETE CASCADE;

ALTER TABLE shipping_address_history
    ADD CONSTRAINT shipping_address_history_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE shipping_box
    ADD CONSTRAINT shipping_box_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE shipping_fee
    ADD CONSTRAINT shipping_fee_shipping_box_id_fkey FOREIGN KEY (shipping_box_id) REFERENCES shipping_box(shipping_box_id);

ALTER TABLE shipping_fee_history
    ADD CONSTRAINT shipping_fee_history_shipping_fee_id_fkey FOREIGN KEY (shipping_fee_id) REFERENCES shipping_fee(shipping_fee_id) ON DELETE CASCADE;

ALTER TABLE shipping_fee_history
    ADD CONSTRAINT shipping_fee_history_tax_id_fkey FOREIGN KEY (tax_id) REFERENCES tax(tax_id) ON DELETE CASCADE;

ALTER TABLE shopping_cart_item
    ADD CONSTRAINT shopping_cart_item_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE shopping_cart_item
    ADD CONSTRAINT shopping_cart_item_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE shopping_cart_item
    ADD CONSTRAINT shopping_cart_item_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE shopping_cart_shipping
    ADD CONSTRAINT shopping_cart_shipping_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE shopping_cart_shipping
    ADD CONSTRAINT shopping_cart_shipping_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE site
    ADD CONSTRAINT site_locale_id_fkey FOREIGN KEY (locale_id) REFERENCES locale(locale_id);

ALTER TABLE site_category
    ADD CONSTRAINT site_category_category_id_fkey FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE CASCADE;

ALTER TABLE site_category
    ADD CONSTRAINT site_category_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE site_item
    ADD CONSTRAINT site_item_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE site_item
    ADD CONSTRAINT site_item_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE site_item_numeric_metadata
    ADD CONSTRAINT site_item_numeric_metadata_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id);

ALTER TABLE site_item_numeric_metadata
    ADD CONSTRAINT site_item_numeric_metadata_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id);

ALTER TABLE site_item_text_metadata
    ADD CONSTRAINT site_item_text_metadata_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id);

ALTER TABLE site_item_text_metadata
    ADD CONSTRAINT site_item_text_metadata_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id);

ALTER TABLE site_user
    ADD CONSTRAINT site_user_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

ALTER TABLE site_user
    ADD CONSTRAINT site_user_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE supplemental_category
    ADD CONSTRAINT supplemental_category_category_id_fkey FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE CASCADE;

ALTER TABLE supplemental_category
    ADD CONSTRAINT supplemental_category_item_id_fkey FOREIGN KEY (item_id) REFERENCES item(item_id) ON DELETE CASCADE;

ALTER TABLE supplemental_user_email
    ADD CONSTRAINT supplemental_user_email_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE tax_history
    ADD CONSTRAINT tax_history_tax_id_fkey FOREIGN KEY (tax_id) REFERENCES tax(tax_id) ON DELETE CASCADE;

ALTER TABLE tax_name
    ADD CONSTRAINT tax_name_locale_id_fkey FOREIGN KEY (locale_id) REFERENCES locale(locale_id);

ALTER TABLE tax_name
    ADD CONSTRAINT tax_name_tax_id_fkey FOREIGN KEY (tax_id) REFERENCES tax(tax_id) ON DELETE CASCADE;

ALTER TABLE transaction_coupon
    ADD CONSTRAINT transaction_coupon_transaction_item_id_fkey FOREIGN KEY (transaction_item_id) REFERENCES transaction_item(transaction_item_id) ON DELETE CASCADE;

ALTER TABLE transaction_credit_tender
    ADD CONSTRAINT transaction_credit_tender_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES transaction_header(transaction_id) ON DELETE CASCADE;

ALTER TABLE transaction_header
    ADD CONSTRAINT transaction_header_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES currency(currency_id);

ALTER TABLE transaction_item
    ADD CONSTRAINT transaction_item_transaction_site_id_fkey FOREIGN KEY (transaction_site_id) REFERENCES transaction_site(transaction_site_id) ON DELETE CASCADE;

ALTER TABLE transaction_item_numeric_metadata
    ADD CONSTRAINT transaction_item_numeric_metadata_transaction_item_id_fkey FOREIGN KEY (transaction_item_id) REFERENCES transaction_item(transaction_item_id) ON DELETE CASCADE;

ALTER TABLE transaction_item_text_metadata
    ADD CONSTRAINT transaction_item_text_metadata_transaction_item_id_fkey FOREIGN KEY (transaction_item_id) REFERENCES transaction_item(transaction_item_id) ON DELETE CASCADE;

ALTER TABLE transaction_paypal_status
    ADD CONSTRAINT transaction_paypal_status_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES transaction_header(transaction_id) ON DELETE CASCADE;

ALTER TABLE transaction_shipping
    ADD CONSTRAINT transaction_shipping_transaction_site_id_fkey FOREIGN KEY (transaction_site_id) REFERENCES transaction_site(transaction_site_id) ON DELETE CASCADE;

ALTER TABLE transaction_site
    ADD CONSTRAINT transaction_site_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES transaction_header(transaction_id) ON DELETE CASCADE;

ALTER TABLE transaction_site_item_numeric_metadata
    ADD CONSTRAINT transaction_site_item_numeric_metadata_transaction_item_id_fkey FOREIGN KEY (transaction_item_id) REFERENCES transaction_item(transaction_item_id) ON DELETE CASCADE;

ALTER TABLE transaction_site_item_text_metadata
    ADD CONSTRAINT transaction_site_item_text_metadata_transaction_item_id_fkey FOREIGN KEY (transaction_item_id) REFERENCES transaction_item(transaction_item_id) ON DELETE CASCADE;

ALTER TABLE transaction_status
    ADD CONSTRAINT transaction_status_fk1 FOREIGN KEY (transporter_id) REFERENCES transporter(transporter_id);

ALTER TABLE transaction_status
    ADD CONSTRAINT transaction_status_transaction_site_id_fkey FOREIGN KEY (transaction_site_id) REFERENCES transaction_site(transaction_site_id);

ALTER TABLE transaction_tax
    ADD CONSTRAINT transaction_tax_transaction_site_id_fkey FOREIGN KEY (transaction_site_id) REFERENCES transaction_site(transaction_site_id) ON DELETE CASCADE;

ALTER TABLE transporter_name
    ADD CONSTRAINT transporter_name_locale_id_fkey FOREIGN KEY (locale_id) REFERENCES locale(locale_id);

ALTER TABLE transporter_name
    ADD CONSTRAINT transporter_name_transporter_id_fkey FOREIGN KEY (transporter_id) REFERENCES transporter(transporter_id) ON DELETE CASCADE;

ALTER TABLE user_address
    ADD CONSTRAINT user_address_address_id_fkey FOREIGN KEY (address_id) REFERENCES address(address_id) ON DELETE CASCADE;

ALTER TABLE user_address
    ADD CONSTRAINT user_address_store_user_id_fkey FOREIGN KEY (store_user_id) REFERENCES store_user(store_user_id) ON DELETE CASCADE;

ALTER TABLE web_snippet
    ADD CONSTRAINT web_snippet_site_id_fkey FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE CASCADE;

insert into currency (currency_id, currency_code) values (1, 'JPY');
insert into currency (currency_id, currency_code) values (2, 'USD');

insert into locale (locale_id, lang, precedence) values (1, 'ja', 2);
insert into locale (locale_id, lang, precedence) values (2, 'en', 1);

# --- !Downs

drop sequence web_snippet_seq;
drop table web_snippet;

drop sequence user_address_seq;
drop table user_address;

drop sequence transporter_name_seq;
drop table transporter_name;

drop sequence transaction_status_seq;
drop table transaction_status;

drop sequence transporter_seq;
drop table transporter;

drop sequence transaction_tax_seq;
drop table transaction_tax;

drop sequence transaction_site_item_text_metadata_seq;
drop table transaction_site_item_text_metadata;

drop sequence transaction_site_item_numeric_metadata_seq;
drop table transaction_site_item_numeric_metadata;

drop sequence transaction_coupon_seq;
drop table transaction_coupon;

drop sequence transaction_item_numeric_metadata_seq;
drop table transaction_item_numeric_metadata;

drop sequence transaction_item_text_metadata_seq;
drop table transaction_item_text_metadata;

drop sequence transaction_item_seq;
drop table transaction_item;

drop sequence transaction_shipping_seq;
drop table transaction_shipping;

drop sequence transaction_site_seq;
drop table transaction_site;

drop sequence transaction_paypal_status_seq;
drop table transaction_paypal_status;

drop sequence transaction_credit_tender_seq;
drop table transaction_credit_tender;

drop sequence transaction_header_seq;
drop table transaction_header;

drop sequence tax_name_seq;
drop table tax_name;

drop sequence tax_history_seq;
drop table tax_history;

drop sequence item_price_history_seq;
drop table item_price_history;

drop sequence shipping_fee_history_seq;
drop table shipping_fee_history;

drop sequence tax_seq;
drop table tax;

drop sequence supplemental_user_email_seq;
drop table supplemental_user_email;

drop table supplemental_category;

drop sequence reset_password_seq;
drop table reset_password;

drop sequence employee_seq;
drop table employee;

drop sequence shipping_address_history_seq;
drop table shipping_address_history;

drop sequence shopping_cart_item_seq;
drop table shopping_cart_item;

drop sequence site_user_seq;
drop table site_user;

drop sequence shopping_cart_shipping_seq;
drop table shopping_cart_shipping;

drop sequence item_inquiry_field_seq;
drop table item_inquiry_field;

drop sequence item_inquiry_seq;
drop table item_inquiry;

drop sequence order_notification_seq;
drop table order_notification;

drop sequence store_user_seq;
drop table store_user;

drop sequence site_item_text_metadata_seq;
drop table site_item_text_metadata;

drop sequence site_item_numeric_metadata_seq;
drop table site_item_numeric_metadata;

drop table site_item;

drop table site_category;

drop sequence item_price_seq;
drop table item_price;

drop sequence item_description_seq;
drop table item_description;

drop sequence shipping_fee_seq;
drop table shipping_fee;

drop sequence shipping_box_seq;
drop table shipping_box;

drop sequence recommend_by_admin_seq;
drop table recommend_by_admin;

drop sequence news_seq;
drop table news;

drop sequence site_seq;
drop table site;

drop sequence address_seq;
drop table address;

drop sequence item_text_metadata_seq;
drop table item_text_metadata;

drop sequence item_name_seq;
drop table item_name;

drop sequence item_numeric_metadata_seq;
drop table item_numeric_metadata;

drop sequence coupon_item_seq;
drop table coupon_item;

drop sequence item_seq;
drop table item;

drop table category_name;

drop table category_path;

drop sequence category_seq;
drop table category;

drop sequence coupon_seq;
drop table coupon;

drop table currency;

drop table locale;

drop table password_dict;
