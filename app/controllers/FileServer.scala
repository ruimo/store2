package controllers

import play.api.libs.json._
import play.api.data.validation.Constraints._
import play.api.data.Form
import play.api.data.Forms._
import java.time.Instant
import play.api.i18n.{Lang, Messages, MessagesProvider}
import controllers.NeedLogin.{Authenticated, OptAuthenticated, AuthenticatedJson}
import play.api.Configuration
import java.nio.file.{Files, NoSuchFileException, Path, Paths}
import play.Logger
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import models._
import play.api.db.Database
import helpers.Cache
import play.api.libs.Files.TemporaryFile

@Singleton
class FileServer @Inject() (
  cc: MessagesControllerComponents,
  val configForTesting: Configuration,
  val authenticated: Authenticated,
  val optAuthenticated: OptAuthenticated,
  val authenticatedJson: AuthenticatedJson,
  val cache: Cache,
  uploadedFileRepo: UploadedFileRepo,
  uploadedDirectoryRepo: UploadedDirectoryRepo,
  fileCategories: FileCategories,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val db: Database,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val loginSessionRepo: LoginSessionRepo
) extends MessagesAbstractController(cc) with Pictures with TimeZoneSupport {
  def isTesting = configForTesting.getOptional[Boolean]("files.fortest").getOrElse(false)
  def picturePathForTesting: Path = {
    val ret = config.getOptional[String]("files.path").map {
      s => Paths.get(s)
    }.getOrElse {
      Paths.get(System.getProperty("user.home"), "files")
    }

    Logger.info("Using files.path = '" + ret + "'")
    ret
  }
  def onPictureNotFound(id: Long, no: Int): Result = Results.NotFound

  def index(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directory: Option[String]
  ) = authenticated { implicit req: AuthMessagesRequest[AnyContent] =>
    Logger.info("FileServer.index()")
    implicit val login: LoginSession = req.login
    Ok(views.html.files(page, pageSize, orderBySpec, categoryName, directory.map(Directory.apply)))
  }

  def fileList(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directory: Option[String]
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    Logger.info("FileServer.fileList()")
    db.withConnection { implicit conn =>
      implicit val login: LoginSession = request.login
      val dir = directory.map(Directory.apply)
      Ok(
        views.html.fileList(
          page, pageSize, OrderBy(orderBySpec),
          uploadedFileRepo.ls(page, pageSize, OrderBy(orderBySpec), categoryName, dir.getOrElse(Directory("/"))).get,
          TimeZoneSupport.formatter(Messages("imageDateFormatInImageList")),
          toLocalDateTime(_)(implicitly),
          categoryName,
          dir
        )
      )
    }
  }

  def openDirectory(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directoryId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    Logger.info("FileServer.openDirectory()")
    db.withConnection { implicit conn =>
      implicit val login: LoginSession = request.login
      val dir = uploadedDirectoryRepo.get(UploadedFileId(directoryId)).map { d => Directory(d.fileName) }
      Ok(
        views.html.fileList(
          page, pageSize, OrderBy(orderBySpec),
          uploadedFileRepo.ls(page, pageSize, OrderBy(orderBySpec), categoryName, dir.getOrElse(Directory("/"))).get,
          TimeZoneSupport.formatter(Messages("imageDateFormatInImageList")),
          toLocalDateTime(_)(implicitly),
          categoryName,
          dir
        )
      )
    }
  }

  def create(
    categoryName: String, uploadedDirectory: String
  ) = authenticated(parse.multipartFormData) { implicit req: AuthMessagesRequest[MultipartFormData[TemporaryFile]] =>
    implicit val login = req.login
    db.withConnection { implicit conn =>
      val dirId = uploadedDirectoryRepo.getByDirectory(Directory(uploadedDirectory))
      req.body.files.foreach { file =>
        val fileName = file.filename
        val contentType = file.contentType
        val ufid = uploadedFileRepo.create(
          login.userId, fileName, contentType, Instant.now(), categoryName, dirId.map(_.id.get)
        )

        file.ref.moveTo(attachmentPath.resolve(f"${ufid.value}%016d"), true)
      }
    }
    Ok("")
  }

  def getFile(id: Long) = authenticated { implicit req =>
    db.withConnection { implicit conn =>
      uploadedFileRepo.get(UploadedFileId(id)).map { uf =>
        val path = attachmentPath.resolve(f"${uf.id.get.value}%016d")
        if (Files.isReadable(path)) {
          if (isModified(path, req))
            readFile(path, uf.contentType.getOrElse("application/octet-stream"), Some(uf.fileName))
          else NotModified
        }
        else {
          NotFound
        }
      }.getOrElse(NotFound)
    }
  }

  def removeJson(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directory: Option[String]
  ) = authenticatedJson { implicit req: AuthMessagesRequest[AnyContent] =>
    implicit val login = req.login
    req.body.asJson.map { json =>
      val pathId = (json \ "pathId").as[String].toLong
      db.withConnection { implicit conn =>
        val ufid = UploadedFileId(pathId)
        uploadedFileRepo.get(ufid).map { uf =>
          if (uf.storeUserId != login.userId  && ! login.isSuperUser) {
            BadRequest(Json.obj("status" -> "forbidden"))
          }
          else {
            uploadedFileRepo.remove(ufid)
            Files.delete(attachmentPath.resolve(f"${ufid.value}%016d"))
            Ok(Json.obj("status" -> "ok"))
          }
        }.getOrElse(BadRequest(Json.obj("status" -> "notfound")))
      }
    }.get
  }

  def createDirJson(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directory: Option[String]
  ) = authenticatedJson { implicit req: AuthMessagesRequest[AnyContent] =>
    implicit val login = req.login

    req.body.asJson.map { json =>
      val path = (json \ "path").as[String]
      val dir = Directory(directory.getOrElse("") + "/" + path)
      db.withConnection { implicit conn =>
        if (uploadedDirectoryRepo.getByDirectory(dir).isDefined) {
          BadRequest(
            Json.obj(
              "status" -> "duplicated"
            )
          )
        }
        else {
          uploadedDirectoryRepo.create(login.userId, dir, Instant.now(), categoryName)

          Ok(
            Json.obj(
              "status" -> "ok"
            )
          )
        }
      }
    }.get
  }

  def removeDirJson(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directory: Option[String]
  ) = authenticatedJson { implicit req: AuthMessagesRequest[AnyContent] =>
    implicit val login = req.login

    req.body.asJson.map { json =>
      val pathId = (json \ "pathId").as[String].toLong
      db.withConnection { implicit conn =>
        val ufid = UploadedFileId(pathId)
        uploadedDirectoryRepo.get(ufid).map { uf =>
          if (uf.storeUserId != login.userId  && ! login.isSuperUser) {
            BadRequest(Json.obj("status" -> "forbidden"))
          }
          else {
            if (uploadedDirectoryRepo.isEmpty(ufid)) {
              uploadedDirectoryRepo.remove(ufid)
              Ok(Json.obj("status" -> "ok"))
            }
            else {
              BadRequest(Json.obj("status" -> "notempty"))
            }
          }
        }.getOrElse(BadRequest(Json.obj("status" -> "notfound")))
      }
    }.get
  }

  def showFilesInCategory(
    page: Int, pageSize: Int, orderBySpec: String, categoryName: String, directory: Option[String]
  ) = authenticated { implicit req: AuthMessagesRequest[AnyContent] =>
    Logger.info("FileServer.showFilesInCategory")
    implicit val login = req.login
    val dir = directory.map(Directory.apply)
    Ok(
      views.html.showFilesInCategory(
        page, pageSize, OrderBy(orderBySpec),
        fileCategories.values.filter(_.value == categoryName).headOption.map(_.menuText).getOrElse(""),
        dir,
        TimeZoneSupport.formatter(Messages("imageDateFormatInImageList")),
        toLocalDateTime(_)(implicitly),
        db.withConnection { implicit conn =>
          uploadedFileRepo.ls(page, pageSize, OrderBy(orderBySpec), categoryName, dir.getOrElse(Directory("/"))).get
        }
      )
    )
  }
}
