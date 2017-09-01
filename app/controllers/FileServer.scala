package controllers

import controllers.NeedLogin.{Authenticated, OptAuthenticated}
import play.api.Configuration
import java.nio.file.{Files, NoSuchFileException, Path, Paths}
import play.Logger
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import models.{LoginSessionRepo, StoreUserRepo}
import play.api.db.Database
import helpers.Cache

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
) extends MessagesAbstractController(cc) with Pictures {
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

}

