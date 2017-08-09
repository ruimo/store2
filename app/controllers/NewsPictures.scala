package controllers

import java.text.{ParseException, SimpleDateFormat}
import java.util.{Locale, TimeZone}

import play.api.Configuration
import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import play.Logger
import play.api.mvc._
import play.api.i18n.Messages
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.{Authenticated, OptAuthenticated}
import models.{LoginSessionRepo, StoreUserRepo}
import play.api.db.Database

@Singleton
class NewsPictures @Inject() (
  cc: MessagesControllerComponents,
  val configForTesting: Configuration,
  val authenticated: Authenticated,
  val optAuthenticated: OptAuthenticated,
  implicit val db: Database,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val loginSessionRepo: LoginSessionRepo
) extends MessagesAbstractController(cc) with Pictures {
  def isTesting = configForTesting.getOptional[Boolean]("news.picture.fortest").getOrElse(false)
  def picturePathForTesting: Path = {
    val ret = config.getOptional[String]("news.picture.path").map {
      s => Paths.get(s)
    }.getOrElse {
      Paths.get(System.getProperty("user.home"), "newsPictures")
    }

    Logger.info("Using news.picture.path = '" + ret + "'")
    ret
  }
  def onPictureNotFound(id: Long, no: Int): Result = Results.NotFound

  def upload(id: Long, no: Int) = uploadPicture(id, no, routes.NewsMaintenance.modifyNewsStart(_))
  def remove(id: Long, no: Int) = authenticated {implicit request: AuthMessagesRequest[AnyContent] =>
    removePicture(id, no, routes.NewsMaintenance.modifyNewsStart(_), Messages("newsIsUpdated"), NeedLogin.assumeAdmin)
  }
}
