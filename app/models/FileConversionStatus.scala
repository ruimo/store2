package models

import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import scala.language.postfixOps
import java.sql.Connection
import anorm._
import anorm.SqlParser
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoField
import javax.inject.Singleton
import javax.inject.Inject
import scala.collection.{immutable => imm}

case class FileConversionStatusId(value: Long) extends AnyVal

case class FileConversionStatus(
  id: Option[FileConversionStatusId],
  uploadedFileId: UploadedFileId,
  status: FileConversionStatusValue,
  createdAt: Instant
)

@Singleton
class FileConversionStatusRepo @Inject() (
) {
  val simple = {
    SqlParser.get[Option[Long]]("file_conversion_status.file_conversion_status_id") ~
    SqlParser.get[Long]("file_conversion_status.uploaded_file_id") ~
    SqlParser.get[Int]("file_conversion_status.status") ~
    SqlParser.get[Instant]("file_conversion_status.created_at") map {
      case id~fileId~status~createdAt => FileConversionStatus(
        id.map(FileConversionStatusId.apply), UploadedFileId(fileId), FileConversionStatusValue.byIndex(status), createdAt
      )
    }
  }

  def create(
    uploadedFileId: UploadedFileId, status: FileConversionStatusValue, createdAt: Instant = Instant.now()
  )(implicit conn: Connection): FileConversionStatus = {
    SQL(
      """
      insert into file_conversion_status(
        file_conversion_status_id, uploaded_file_id, status, created_at
      ) values (
        (select nextval('file_conversion_status_seq')),
        {uploadedFileId}, {status}, {createdAt}
      )
      """
    ).on(
      'uploadedFileId -> uploadedFileId.value,
      'status -> status.ordinal,
      'createdAt -> createdAt
    ).executeUpdate()

    val id = SQL("select currval('file_conversion_status_seq')").as(SqlParser.scalar[Long].single)

    FileConversionStatus(Some(FileConversionStatusId(id)), uploadedFileId, status, createdAt)
  }

  def get(uploadedFileId: UploadedFileId)(implicit conn: Connection): Option[FileConversionStatus] = SQL(
    """
    select * from file_conversion_status
    where uploaded_file_id = {uploadedFileId}
    """
  ).on(
    'uploadedFileId -> uploadedFileId.value
  ).as(
    simple.singleOpt
  )

  def update(
    id: FileConversionStatusId, status: FileConversionStatusValue
  )(implicit conn: Connection): Int = {
    SQL(
      """
      update file_conversion_status
      set status = {status}
      where file_conversion_status_id = {id}
      """
    ).on(
      'status -> status.ordinal,
      'id -> id.value
    ).executeUpdate()
  }

  def list(maxCount: Int = 10)(implicit conn: Connection): imm.Seq[FileConversionStatus] = SQL(
    """
    select * from file_conversion_status
    where status in(""" +
      FileConversionStatusValue.WAITING.ordinal + ", " +
      FileConversionStatusValue.CONVERTING.ordinal +
    """)
    order by created_at desc limit {maxCount}
    """
  ).on(
    'maxCount -> maxCount
  ).as(simple *)
}
