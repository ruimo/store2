package models

import java.time.Instant
import java.io.{InputStream, FileInputStream}
import com.ruimo.scoins.LoanPattern._
import java.time.Instant
import java.nio.file.Path
import anorm._
import java.sql.Connection
import com.ruimo.scoins.ImmutableByteArray
import javax.inject.Singleton
import javax.inject.Inject

case class UploadedFileId(value: Long) extends AnyVal

case class UploadedFile(
  id: Option[UploadedFileId] = None,
  storeUserId: Long,
  fileName: String,
  contentType: Option[String],
  createdTime: Instant = Instant.now(),
  category_name: String
)

@Singleton
class UploadedFileRepo @Inject() (
  storeUserRepo: StoreUserRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("uploaded_file.uploaded_file_id") ~
    SqlParser.get[Long]("uploaded_file.store_user_id") ~
    SqlParser.get[String]("uploaded_file.file_name") ~
    SqlParser.get[Option[String]]("uploaded_file.content_type") ~
    SqlParser.get[Instant]("uploaded_file.created_time") ~
    SqlParser.get[String]("uploaded_file.category_name") map {
      case id~storeUserId~fileName~contentType~createdTime~categoryName => UploadedFile(
        id.map(UploadedFileId.apply), storeUserId, fileName, contentType, createdTime, categoryName
      )
    }
  }

  val withUser = simple ~ storeUserRepo.simple map {
    case uploadedFile~storeUser => (uploadedFile, storeUser)
  }

  def get(id: UploadedFileId)(implicit conn: Connection): Option[UploadedFile] = SQL(
    "select * from uploaded_file where uploaded_file_id={id}"
  ).on(
    'id -> id.value
  ).as(
    simple.singleOpt
  )

  def remove(id: UploadedFileId)(implicit conn: Connection): Int = SQL(
    "delete from uploaded_file where uploaded_file_id={id}"
  ).on(
    'id -> id.value
  ).executeUpdate()

  def list(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy, categoryName: String
  )(
    implicit conn: Connection
  ): PagedRecords[(UploadedFile, StoreUser)] = {
    import scala.language.postfixOps
    val offset: Int = pageSize * page
    val records: Seq[(UploadedFile, StoreUser)] = SQL(
      s"""
      select * from uploaded_file uf
      inner join store_user u on u.store_user_id = uf.store_user_id
      where category_name = {categoryName}
      order by $orderBy limit {pageSize} offset {offset}
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> offset,
      'categoryName -> categoryName
    ).as(
      withUser *
    )

    val count = SQL(
      "select count(*) from uploaded_file where category_name = {categoryName}"
    ).on(
      'categoryName -> categoryName
    ).as(SqlParser.scalar[Long].single)
      
    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }

  def create(
    storeUserId: Long, fileName: String, contentType: Option[String], createdTime: Instant, categoryName: String
  )(implicit conn: Connection): UploadedFileId = {
    SQL(
      """
      insert into uploaded_file(
        uploaded_file_id, store_user_id, file_name, content_type, created_time, category_name
      ) values (
        (select nextval('uploaded_file_seq')),
        {storeUserId}, {fileName}, {contentType}, {createdTime}, {categoryName}
      )
      """
    ).on(
      'storeUserId -> storeUserId,
      'fileName -> fileName,
      'contentType -> contentType,
      'createdTime -> createdTime,
      'categoryName -> categoryName
    ).executeUpdate()

    UploadedFileId(SQL("select currval('uploaded_file_seq')").as(SqlParser.scalar[Long].single))
  }
}
