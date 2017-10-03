package models

import play.Logger
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

case class Directory(path: String) extends AnyVal {
  def isRoot: Boolean = path == "/"
  def parent: Directory = if (isRoot) this else {
    path.lastIndexOf("/") match {
      case -1 => throw new IllegalArgumentException("path does not starts with '/': '" + path + "'")
      case 0 => Directory("/")
      case idx: Int => Directory(path.substring(0, idx))
    }
  }
}

sealed trait UploadedPath {
  def id: Option[UploadedFileId]
  def storeUserId: Long
  def fileName: String
  def createdTime: Instant
  def categoryName: String
}

case class UploadedFileId(value: Long) extends AnyVal

case class UploadedFile(
  id: Option[UploadedFileId] = None,
  storeUserId: Long,
  fileName: String,
  contentType: Option[String],
  createdTime: Instant = Instant.now(),
  categoryName: String,
  uploadedDirectory: Option[UploadedFileId]
) extends UploadedPath

case class UploadedDirectory(
  id: Option[UploadedFileId] = None,
  storeUserId: Long,
  fileName: String,
  categoryName: String,
  createdTime: Instant = Instant.now()
) extends UploadedPath

case class DirectoryPath(
  ancestor: UploadedFileId,
  descendant: UploadedFileId,
  pathLength: Int
)

@Singleton
class UploadedFileRepo @Inject() (
  storeUserRepo: StoreUserRepo,
  uploadedDirectoryRepo: UploadedDirectoryRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("uploaded_file.uploaded_file_id") ~
    SqlParser.get[Long]("uploaded_file.store_user_id") ~
    SqlParser.get[String]("uploaded_file.file_name") ~
    SqlParser.get[Option[String]]("uploaded_file.content_type") ~
    SqlParser.get[Instant]("uploaded_file.created_time") ~
    SqlParser.get[String]("uploaded_file.category_name") ~
    SqlParser.get[Option[Long]]("uploaded_file.uploaded_directory_id") map {
      case id~storeUserId~fileName~contentType~createdTime~categoryName~dirid => UploadedFile(
        id.map(UploadedFileId.apply), storeUserId, fileName, contentType, createdTime, categoryName,
        dirid.map(UploadedFileId.apply)
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
    storeUserId: Long, fileName: String, contentType: Option[String], createdTime: Instant, categoryName: String,
    uploadedDirectoryId: Option[UploadedFileId]
  )(implicit conn: Connection): UploadedFileId = {
    SQL(
      """
      insert into uploaded_file(
        uploaded_file_id, store_user_id, file_name, content_type, created_time, category_name, uploaded_directory_id
      ) values (
        (select nextval('uploaded_file_seq')),
        {storeUserId}, {fileName}, {contentType}, {createdTime}, {categoryName}, {uploadedDirectoryId}
      )
      """
    ).on(
      'storeUserId -> storeUserId,
      'fileName -> fileName,
      'contentType -> contentType,
      'createdTime -> createdTime,
      'categoryName -> categoryName,
      'uploadedDirectoryId -> uploadedDirectoryId.map(_.value)
    ).executeUpdate()

    UploadedFileId(SQL("select currval('uploaded_file_seq')").as(SqlParser.scalar[Long].single))
  }

  val lsSimple = {
    SqlParser.get[Option[Long]]("path_id") ~
    SqlParser.get[Long]("sid") ~
    SqlParser.get[String]("file_name") ~
    SqlParser.get[String]("category_name") ~
    SqlParser.get[Instant]("ctime") ~
    SqlParser.get[Option[String]]("content_type") ~
    SqlParser.get[Option[Long]]("uploaded_directory_id") ~
    SqlParser.get[Int]("is_directory") map {
      case id~storeUserId~fileName~categoryName~createdTime~contentType~uploadedFileId~isDirectory => {
        if (isDirectory == 1) {
          UploadedDirectory(
            id.map(UploadedFileId.apply),
            storeUserId,
            fileName,
            categoryName,
            createdTime
          )
        }
        else {
          UploadedFile(
            id.map(UploadedFileId.apply),
            storeUserId,
            fileName,
            contentType,
            createdTime,
            categoryName,
            uploadedFileId.map(UploadedFileId.apply)
          )
        }
      }
    }
  }

  val lsSimpleWithUser = lsSimple ~ storeUserRepo.simple map {
    case uf~storeUser => (uf, storeUser)
  }

  def lsRoot(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy, categoryName: String
  )(
    implicit conn: Connection
  ): PagedRecords[(UploadedPath, StoreUser)] = {
    import scala.language.postfixOps

    val offset: Int = pageSize * page
    val (records, count) = uploadedDirectoryRepo.getByDirectory(Directory("/")) match {
      case Some(dir) =>
        val baseSql = """
         select * from (
           select
              uploaded_directory_id path_id,
              store_user_id sid,
              file_name,
              category_name,
              created_time ctime,
              null content_type,
              null uploaded_directory_id,
              1 is_directory
            from uploaded_directory
            where uploaded_directory_id in (
              select descendant from directory_path where ancestor = {dirId} and path_length = 1
            ) and category_name = {categoryName}
            union
            select
              uploaded_file_id path_id,
              store_user_id sid,
              file_name,
              category_name,
              created_time ctime,
              content_type,
              uploaded_directory_id,
              0 is_directory
            from uploaded_file
            where
              category_name = {categoryName} and
              (uploaded_directory_id = {dirId} or
              uploaded_directory_id is null)
        ) v
        inner join store_user u on u.store_user_id = sid
        """
        (
          SQL(
            baseSql + s"order by $orderBy limit {pageSize} offset {offset}"
          ).on(
            'dirId -> dir.id.get.value,
            'pageSize -> pageSize,
            'offset -> offset,
            'categoryName -> categoryName
          ).as(lsSimpleWithUser *),
          SQL(
            "select count(*) from (" + baseSql + ") count"
          ).on(
            'dirId -> dir.id.get.value,
            'categoryName -> categoryName
          ).as(SqlParser.scalar[Long].single)
        )
      case None =>
        val baseSql = """
          select * from (
            select
              uploaded_file_id path_id,
              store_user_id sid,
              file_name,
              category_name,
              created_time ctime,
              content_type,
              uploaded_directory_id,
              0 is_directory
            from uploaded_file
            where
              category_name = {categoryName} and
              uploaded_directory_id is null
          ) v
          inner join store_user u on u.store_user_id = sid
        """
        (
          SQL(
            baseSql + s"order by $orderBy limit {pageSize} offset {offset}"
          ).on(
            'pageSize -> pageSize,
            'offset -> offset,
            'categoryName -> categoryName
          ).as(lsSimpleWithUser *),
          SQL(
            "select count(*) from (" + baseSql + ") count"
          ).on(
            'categoryName -> categoryName
          ).as(SqlParser.scalar[Long].single)
        )
    }

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }

  def ls(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy = OrderBy("file_name"),
    categoryName: String = "", directory: Directory
  )(
    implicit conn: Connection
  ): Option[PagedRecords[(UploadedPath, StoreUser)]] = {
    import scala.language.postfixOps

    Logger.info("ls()")
    if (directory.isRoot) {
      Logger.info("ls() root")
      Some(lsRoot(page, pageSize, orderBy, categoryName))
    }
    else {
      Logger.info("ls() non root")
      val offset: Int = pageSize * page
      uploadedDirectoryRepo.getByDirectory(directory).map { dir =>
        val baseSql = """
          select * from (
            select
              uploaded_directory_id path_id,
              store_user_id sid,
              file_name,
              category_name,
              created_time ctime,
              null content_type,
              null uploaded_directory_id,
              1 is_directory
            from uploaded_directory
            where
              category_name = {categoryName} and
              uploaded_directory_id in (
                select descendant from directory_path where ancestor = {dirId} and path_length = 1
              )
            union
            select
              uploaded_file_id path_id,
              store_user_id sid,
              file_name,
              category_name,
              created_time ctime,
              content_type,
              uploaded_directory_id,
              0 is_directory
            from uploaded_file
            where
              uploaded_directory_id = {dirId} and
              category_name = {categoryName}
          ) v
          inner join store_user u on u.store_user_id = sid
        """
        val records = SQL(
          baseSql + s"order by $orderBy limit {pageSize} offset {offset}"
        ).on(
          'dirId -> dir.id.get.value,
          'pageSize -> pageSize,
          'offset -> offset,
          'categoryName -> categoryName
        ).as(
          lsSimpleWithUser *
        )

        val count = SQL(
          "select count(*) from (" + baseSql + ") count"
        ).on(
          'dirId -> dir.id.get.value,
          'categoryName -> categoryName
        ).as(SqlParser.scalar[Long].single)

        PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
      }
    }
  }
}

@Singleton
class UploadedDirectoryRepo @Inject() (
  storeUserRepo: StoreUserRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("uploaded_directory.uploaded_directory_id") ~
    SqlParser.get[Long]("uploaded_directory.store_user_id") ~
    SqlParser.get[String]("uploaded_directory.file_name") ~
    SqlParser.get[String]("uploaded_directory.category_name") ~
    SqlParser.get[Instant]("uploaded_directory.created_time") map {
      case id~storeUserId~fileName~categoryName~createdTime => UploadedDirectory(
        id.map(UploadedFileId.apply), storeUserId, fileName, categoryName, createdTime
      )
    }
  }

  def get(id: UploadedFileId)(implicit conn: Connection): Option[UploadedDirectory] = SQL(
    "select * from uploaded_directory where uploaded_directory_id={id}"
  ).on(
    'id -> id.value
  ).as(
    simple.singleOpt
  )

  def getByDirectory(dir: Directory)(implicit conn: Connection): Option[UploadedDirectory] = SQL(
    "select * from uploaded_directory where file_name={dir}"
  ).on(
    'dir -> dir.path
  ).as(
    simple.singleOpt
  )

  def remove(id: UploadedFileId)(implicit conn: Connection): Int = SQL(
    "delete from uploaded_directory where uploaded_directory_id={id}"
  ).on(
    'id -> id.value
  ).executeUpdate()

  private[this] def createIfRootNotFound()(implicit conn: Connection) {
    if (SQL("select count(*) form uploaded_directory where file_name = '/'").as(SqlParser.scalar[Long].single) == 0) {
    }
  }

  def create(
    storeUserId: Long, directory: Directory,  createdTime: Instant, categoryName: String
  )(implicit conn: Connection): UploadedFileId = {
    def inserter(pathName: String): UploadedFileId = {
      SQL(
        """
        insert into uploaded_directory(
          uploaded_directory_id, store_user_id, file_name, created_time, category_name
        ) values (
          (select nextval('uploaded_file_seq')),
          {storeUserId}, {directoryName}, {createdTime}, {categoryName}
        )
        """
      ).on(
        'storeUserId -> storeUserId,
        'directoryName -> pathName,
        'createdTime -> createdTime,
        'categoryName -> categoryName
      ).executeUpdate()
      UploadedFileId(SQL("select currval('uploaded_file_seq')").as(SqlParser.scalar[Long].single))
    }

    def preparePath(parent: UploadedFileId, id: UploadedFileId)(implicit conn: Connection) {
      SQL(
        """
        insert into directory_path (ancestor, descendant, path_length)
          select t.ancestor, {id}, t.path_length + 1
          from directory_path t
          where t.descendant = {aid}
          union select """ + id.value + ", " + id.value + ", 0"
      ).on(
        'id -> id.value,
        'aid -> parent.value
      ).executeUpdate()
    }

    if (SQL("select count(*) from uploaded_directory where file_name = '/'").as(SqlParser.scalar[Long].single) == 0) {
      val id = inserter("/")
      preparePath(id, id)
    }

    val parentDir = directory.parent
    val parent = getByDirectory(parentDir).getOrElse {
      throw new IllegalArgumentException("Parent '" + parentDir + "' not found.")
    }
    val id = inserter(directory.path)

    preparePath(parent.id.get, id)

    id
  }

  def isEmpty(id: UploadedFileId)(implicit conn: Connection): Boolean = {
    SQL(
      """
      select count(*) from directory_path
      where ancestor = {id} and path_length <> 0
      """
    ).on(
      'id -> id.value
    ).as(SqlParser.scalar[Long].single) == 0 &&
    SQL(
      """
      select count(*) from uploaded_file
      where uploaded_directory_id = {id}
      """
    ).on(
      'id -> id.value
    ).as(SqlParser.scalar[Long].single) == 0
  }
}
