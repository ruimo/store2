package controllers

import scala.collection.JavaConverters._
import scala.collection.Iterable
import java.text.{ParseException, SimpleDateFormat}
import java.util.{Locale, TimeZone}

import play.api.Configuration
import java.nio.file.{Files, NoSuchFileException, Path}

import controllers.NeedLogin.{Authenticated, OptAuthenticated}
import play.api.mvc._
import play.api.i18n.Messages
import models.{LoginSession, LoginSessionRepo, StoreUserRepo}
import play.Logger
import play.api.db.Database
import play.api.libs.Files.TemporaryFile

import scala.io.Source

trait Pictures extends MessagesAbstractController {
  val loginSessionRepo: LoginSessionRepo
  implicit val db: Database
  implicit val storeUserRepo: StoreUserRepo
  val authenticated: Authenticated
  val optAuthenticated: OptAuthenticated
  val CacheDateFormat = new ThreadLocal[SimpleDateFormat]() {
    override def initialValue: SimpleDateFormat = {
      val f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
      f.setTimeZone(TimeZone.getTimeZone("GMT"))
      f
    }
  }

  def isTesting: Boolean
  def config: Configuration = if (isTesting) configForTesting else configForProduction
  def configForTesting: Configuration
  // Cache config
  lazy val configForProduction = configForTesting
  lazy val picturePathForProduction: Path = picturePathForTesting
  def picturePathForTesting: Path
  def picturePath: Path = if (isTesting) picturePathForTesting else picturePathForProduction

  def attachmentPath: Path = {
    val path = picturePath.resolve("attachments")
    if (! Files.exists(path)) {
      Files.createDirectories(path)
    }
    path
  }

  def notfoundPath =
    if (isTesting) notfoundPathForTesting else notfoundPathForProduction
  // Cache path
  lazy val notfoundPathForProduction: Path = notfoundPathForTesting
  def notfoundPathForTesting: Path = {
    val path = picturePath.resolve("notfound.jpg")
    Logger.info("Not found picture '" + path.toAbsolutePath + "' will be used.")
    path
  }

  def uploadPicture(
    id: Long, no: Int, retreat: Long => Call
  ) = Action(parse.multipartFormData) { implicit request: MessagesRequest[MultipartFormData[TemporaryFile]] =>
    val login = db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) }
    login match {
      case None => NeedLogin.onUnauthorized(request)
      case Some(user) =>
        if (user.isBuyer) NeedLogin.onUnauthorized(request)
        else {
          request.body.file("picture").map { picture =>
            val filename = picture.filename
            val contentType = picture.contentType
            if (contentType != Some("image/jpeg")) {
              Redirect(
                retreat(id)
              ).flashing("errorMessage" -> Messages("jpeg.needed"))
            }
            else {
              picture.ref.moveTo(toPath(id, no).toFile, true)
              Redirect(
                retreat(id)
              ).flashing("message" -> Messages("itemIsUpdated"))
            }
          }.getOrElse {
            Redirect(retreat(id)).flashing(
              "errorMessage" -> Messages("file.not.found")
            )
          }.withSession(
            request.session + (loginSessionRepo.loginUserKey -> user.withExpireTime(System.currentTimeMillis + loginSessionRepo.sessionTimeout).toSessionString)
          )
        }
    }
  }

  def toPath(id: Long, no: Int) = picturePath.resolve(pictureName(id, no))
  def pictureName(id: Long, no: Int) = id + "_" + no + ".jpg"
  def allPicturePaths(id: Long): Iterable[Path] = iterableAsScalaIterable(Files.newDirectoryStream(picturePath, id + "_*.jpg"))

  def isModified(path: Path, request: RequestHeader): Boolean = {
    request.headers.get("If-Modified-Since").flatMap { value =>
      try {
        Some(CacheDateFormat.get.parse(value))
      }
      catch {
        case e: ParseException => {
          Logger.error("Invalid date format '" + value + "'")
          None
        }
      }
    } match {
      case Some(t) =>
        t.getTime < path.toFile.lastModified
      case None => true
    }
  }

  def removeAllPictures(id: Long) {
    allPicturePaths(id).foreach { path =>
      try {
        Files.delete(path)
        notfoundPath.toFile.setLastModified(System.currentTimeMillis)
      }
      catch {
        case e: NoSuchFileException =>
        case e: Throwable => throw e
      }
    }
  }

  def removePicture(
    id: Long, no: Int, retreat: Long => Call, message: String,
    assumeOperator: LoginSession => (=> Result) => Result
  )(
    implicit request: AuthMessagesRequest[AnyContent]
  ): Result = {
    implicit val login = request.login
    assumeOperator(login) {
      try {
        Files.delete(toPath(id, no))
        notfoundPath.toFile.setLastModified(System.currentTimeMillis)
      }
      catch {
        case e: NoSuchFileException =>
        case e: Throwable => throw e
      }
      Redirect(
        retreat(id)
      ).flashing("message" -> message)
    }
  }

  def getPath(itemId: Long, no: Int): Path = {
    val path = toPath(itemId, no)
    if (Files.isReadable(path)) path
    else notfoundPath
  }

  def readFile(path: Path, contentType: String = "image/jpeg", fileName: Option[String] = None): Result = {
    val source = Source.fromFile(path.toFile)(scala.io.Codec.ISO8859)
    val byteArray = try {
      source.map(_.toByte).toArray
    }
    finally {
      try {
        source.close()
      }
      catch {
        case t: Throwable => Logger.error("Cannot close stream.", t)
      }
    }

    bytesResult(byteArray, contentType, fileName)
  }

  def bytesResult(byteArray: Array[Byte], contentType: String, fileName: Option[String]): Result = {
    fileName match {
      case None =>
        Ok(byteArray).as(contentType).withHeaders(
          CACHE_CONTROL -> "max-age=0",
          EXPIRES -> "Mon, 26 Jul 1997 05:00:00 GMT",
          LAST_MODIFIED -> CacheDateFormat.get.format(new java.util.Date(System.currentTimeMillis))
        )

      case Some(fname) =>
        Ok(byteArray).as(contentType).withHeaders(
          CACHE_CONTROL -> "max-age=0",
          EXPIRES -> "Mon, 26 Jul 1997 05:00:00 GMT",
          LAST_MODIFIED -> CacheDateFormat.get.format(new java.util.Date(System.currentTimeMillis)),
          CONTENT_DISPOSITION -> ("attachment; filename=" + fname)
        )
    }
  }

  def getPicture(id: Long, no: Int) = optAuthenticated { request: MessagesRequest[AnyContent] =>
    val path = getPath(id, no)
    if (Files.isReadable(path)) {
      if (isModified(path, request)) readFile(path) else NotModified
    }
    else {
      onPictureNotFound(id, no)
    }
  }

  def onPictureNotFound(id: Long, no: Int): Result
}
