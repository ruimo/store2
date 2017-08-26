package controllers

import scala.collection.JavaConverters._
import scala.annotation.tailrec
import scala.collection.immutable
import java.io.InputStream
import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import play.api.mvc._
import play.api.i18n.{Lang, Messages}
import java.text.{ParseException, SimpleDateFormat}
import java.util.{Locale, TimeZone}
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.{Authenticated, OptAuthenticated}
import models.{LoginSessionRepo, StoreUserRepo}

import collection.immutable.IntMap
import play.api.Configuration
import play.Logger
import play.api.db.Database
import play.api.libs.Files.TemporaryFile

@Singleton
class ItemPictures @Inject() (
  cc: MessagesControllerComponents,
  val optAuthenticated: OptAuthenticated,
  val loginSessionRepo: LoginSessionRepo,
  val configForTesting: Configuration,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val db: Database,
  val authenticated: Authenticated
) extends MessagesAbstractController(cc) with Pictures {
  def isTesting = configForTesting.getOptional[Boolean]("item.picture.fortest").getOrElse(false)
  def picturePathForTesting: Path = {
    val ret = config.getOptional[String]("item.picture.path").map {
      s => Paths.get(s)
    }.getOrElse {
      Paths.get(System.getProperty("user.home"), "itemPictures")
    }

    Logger.info("Using item.picture.path = '" + ret + "'")
    ret
  }
  def detailNotfoundPath = picturePath.resolve("detailnotfound.jpg")
  lazy val attachmentCount = config.getOptional[Int]("item.attached.file.count").getOrElse(5)

  def upload(itemId: Long, no: Int) = uploadPicture(itemId, no, routes.ItemMaintenance.startChangeItem(_))

  def uploadDetail(itemId: Long) = authenticated(
    parse.multipartFormData
  ) { implicit request: MessagesRequest[MultipartFormData[TemporaryFile]] =>
    request.body.file("picture").map { picture =>
      val filename = picture.filename
      val contentType = picture.contentType
      if (contentType != Some("image/jpeg")) {
        Redirect(
          routes.ItemMaintenance.startChangeItem(itemId)
        ).flashing("errorMessage" -> Messages("jpeg.needed"))
      }
      else {
        picture.ref.moveTo(toDetailPath(itemId).toFile, true)
        Redirect(
          routes.ItemMaintenance.startChangeItem(itemId)
        ).flashing("message" -> Messages("itemIsUpdated"))
      }
    }.getOrElse {
      Redirect(routes.ItemMaintenance.startChangeItem(itemId)).flashing(
        "errorMessage" -> Messages("file.not.found")
      )
    }
  }

  def onPictureNotFound(id: Long, no: Int): Result = readPictureFromClasspath(id, no)

  def readPictureFromClasspath(itemId: Long, no: Int, contentType: String = "image/jpeg"): Result = {
    val result = if (config.getOptional[Boolean]("item.picture.for.demo").getOrElse(false)) {
      val fileName = "public/images/itemPictures/" + pictureName(itemId, no)
      readFileFromClasspath(fileName, contentType)
    }
    else Results.NotFound

    if (result == Results.NotFound) {
      readFileFromClasspath("public/images/notfound.jpg", contentType)
    }
    else {
      result
    }
  }

  def readDetailPictureFromClasspath(itemId: Long, contentType: String = "image/jpeg"): Result = {
    val result = if (config.getOptional[Boolean]("item.picture.for.demo").getOrElse(false)) {
      val fileName = "public/images/itemPictures/" + detailPictureName(itemId)
      readFileFromClasspath(fileName, contentType)
    }
    else Results.NotFound

    if (result == Results.NotFound) {
      readFileFromClasspath("public/images/detailnotfound.jpg", contentType)
    }
    else {
      result
    }
  }

  def readFileFromClasspath(fileName: String, contentType: String): Result = {
    Option(getClass.getClassLoader.getResourceAsStream(fileName)) match {
      case None => Results.NotFound
      case Some(is) => {
        val byteArray = try {
          readFully(is)
        }
        finally {
          try {
            is.close()
          }
          catch {
            case t: Throwable => Logger.error("Cannot close stream.", t)
          }
        }

        bytesResult(byteArray, contentType, None)

      }
    }
  }

  def readFully(is: InputStream): Array[Byte] = {
    @tailrec def readFully(size: Int, work: immutable.Vector[(Int, Array[Byte])]): Array[Byte] = {
      val buf = new Array[Byte](64 * 1024)
      val readLen = is.read(buf)
      if (readLen == -1) {
        var offset = 0
        work.foldLeft(new Array[Byte](size)) {
          (ary, e) => 
            System.arraycopy(e._2, 0, ary, offset, e._1)
            offset = offset + e._1
            ary
        }
      }
      else {
        readFully(size + readLen, work :+ (readLen, buf))
      }
    }

    readFully(0, immutable.Vector[(Int, Array[Byte])]())
  }

  def detailPictureExists(itemId: Long): Boolean = Files.isReadable(toDetailPath(itemId))

  def getDetailPicture(itemId: Long) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    val path = getDetailPath(itemId)
    if (Files.isReadable(path)) {
      if (isModified(path, request)) readFile(path) else NotModified
    }
    else {
      readDetailPictureFromClasspath(itemId)
    }
  }

  def getDetailPath(itemId: Long): Path = {
    val path = toDetailPath(itemId)
    if (Files.isReadable(path)) path
    else detailNotfoundPath
  }

  def removeDetail(itemId: Long) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    try {
      Files.delete(toDetailPath(itemId))
      detailNotfoundPath.toFile.setLastModified(System.currentTimeMillis)
    }
    catch {
      case e: NoSuchFileException =>
      case e: Throwable => throw e
    }
    Redirect(
      routes.ItemMaintenance.startChangeItem(itemId)
    ).flashing("message" -> Messages("itemIsUpdated"))
  }

  def detailPictureName(itemId: Long) = "detail" + itemId + ".jpg"
  def toDetailPath(itemId: Long) = picturePath.resolve(detailPictureName(itemId))

  def remove(id: Long, no: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val lang = request.acceptLanguages.head
    removePicture(id, no, routes.ItemMaintenance.startChangeItem(_), messagesApi("itemIsUpdated"), NeedLogin.assumeAdmin)
  }

  def uploadAttachment(
    id: Long, no: Int
  ) = uploadAttachmentFile(id, no, routes.ItemMaintenance.startChangeItem(_))

  def removeAttachment(
    id: Long, no: Int, fileName: String
  ) = removeAttachmentFile(id, no, fileName, routes.ItemMaintenance.startChangeItem(_))
}
