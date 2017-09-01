package controllers

import play.api.data.Form
import play.api.data.Forms._
import java.time.Instant
import play.api.i18n.{Lang, Messages, MessagesProvider}
import controllers.NeedLogin.{Authenticated, OptAuthenticated}
import play.api.Configuration
import java.nio.file.{Files, NoSuchFileException, Path, Paths}
import play.Logger
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import models.{LoginSessionRepo, StoreUserRepo, UploadedFile, UploadedFileId, OrderBy, LoginSession, RemoveFile}
import play.api.db.Database
import helpers.Cache
import play.api.libs.Files.TemporaryFile

@Singleton
class FileServer @Inject() (
  cc: MessagesControllerComponents,
  val configForTesting: Configuration,
  val authenticated: Authenticated,
  val optAuthenticated: OptAuthenticated,
  val cache: Cache,
  implicit val db: Database,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val loginSessionRepo: LoginSessionRepo
) extends MessagesAbstractController(cc) with Pictures with TimeZoneSupport {
  val removeForm = Form(
    mapping(
      "fileId" -> longNumber
    )(RemoveFile.apply)(RemoveFile.unapply)
  )

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
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit req: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = req.login
    Ok(views.html.files(page, pageSize, orderBySpec))
  }

  def fileList(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      implicit val login: LoginSession = request.login
      Ok(
        views.html.fileList(
          page, pageSize, OrderBy(orderBySpec),
          UploadedFile.list(page, pageSize, OrderBy(orderBySpec)),
          TimeZoneSupport.formatter(Messages("imageDateFormatInImageList")),
          toLocalDateTime(_)(implicitly),
          removeForm
        )
      )
    }
  }

  def create() = authenticated(parse.multipartFormData) { implicit req: AuthMessagesRequest[MultipartFormData[TemporaryFile]] =>
    implicit val login = req.login
    db.withConnection { implicit conn =>
      req.body.files.foreach { file =>
        val fileName = file.filename
        val contentType = file.contentType
        val ufid = UploadedFile.create(
          login.userId, fileName, contentType, Instant.now()
        )

        file.ref.moveTo(attachmentPath.resolve(f"${ufid.value}%016d"), true)
      }
    }
    Ok("")
  }

  def getFile(id: Long) = authenticated { implicit req =>
    db.withConnection { implicit conn =>
      UploadedFile.get(UploadedFileId(id)).map { uf =>
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

  def remove(page: Int, pageSize: Int, orderBySpec: String) = authenticated { implicit req: AuthMessagesRequest[AnyContent] =>
    implicit val login = req.login
    removeForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in FileServer.remove() " + formWithErrors)
        Forbidden
      },
      removeData => {
        val id = removeData.fileId
        db.withConnection { implicit conn =>
          val ufid = UploadedFileId(id)
          UploadedFile.get(ufid).map { uf =>
            if (uf.storeUserId != login.userId  && ! login.isSuperUser) {
              Forbidden
            }
            else {
              UploadedFile.remove(ufid)
              Redirect(routes.FileServer.fileList(0, pageSize, orderBySpec))
            }
          }.getOrElse(Redirect(routes.FileServer.fileList(0, pageSize, orderBySpec)))
        }
      }
    )
  }
}
