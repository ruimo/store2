 ---

# --- !Ups

create table file_conversion_status (
  file_conversion_status_id bigint not null,
  uploaded_file_id bigint not null,
  status integer not null,
  created_at timestamp not null
);

alter table file_conversion_status
    add constraint pk_file_conversion_status primary key (file_conversion_status_id);

alter table file_conversion_status
    add constraint file_conversion_statusuploaded_file_id_fkey foreign key (uploaded_file_id) references uploaded_file(uploaded_file_id) on delete cascade;

alter table file_conversion_status
    add constraint file_conversion_status_file_uploaded_file_id_unique unique (uploaded_file_id);

create sequence file_conversion_status_seq;

# --- !Downs

drop sequence file_conversion_status_seq;

drop table file_conversion_status;
