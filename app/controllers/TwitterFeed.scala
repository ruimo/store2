package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.OptAuthenticated
import helpers.{Cache, TwitterAdapter, TwitterAdapterRepo}
import models.LoginSessionRepo
import play.api.db.Database
import play.api.mvc._
import play.api.{Configuration, Play}
import play.api.libs.json.{JsNumber, JsObject, JsString, Json}

@Singleton
class TwitterFeed @Inject() (
  cc: MessagesControllerComponents,
  cache: Cache,
  conf: Configuration,
  optAuthenticated: OptAuthenticated,
  val twitterAdapterRepo: TwitterAdapterRepo,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val db: Database
) extends MessagesAbstractController(cc) {
  val twitter: () => TwitterAdapter = cache.cacheOnProd(
    () => twitterAdapterRepo(
      conf.get[String]("twitter.consumerKey"),
      conf.get[String]("twitter.secretKey"),
      conf.get[String]("twitter.accessToken"),
      conf.get[String]("twitter.accessTokenSecret")
    )
  )

  def latestTweet(screenName: String) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin = db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) }
    Ok(twitter().getLatestTweetEmbed(screenName)().map(_._1)getOrElse("NONE"))
  }

  def latestTweetJson(
    screenName: String,
    omitScript: Boolean,
    maxWidth: Option[Int]
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin = db.withConnection{ implicit conn => loginSessionRepo.fromRequest(request) }
    twitter().getLatestTweetEmbed(
      screenName = screenName,
      omitScript = omitScript,
      maxWidth = maxWidth
    )() match {
      case None => NotFound("No tweet for '" + screenName + "' found.")
      case Some(t) => Ok(
        Json.toJson(
          JsObject(
            Seq(
              "html" -> JsString(t._1),
              // 64bit double float has 52 bit width fraction.
              // 2^52 / 1000 / 60 / 60 / 24 / 365 = 142808.207362.
              // For about 142,808 years since epoch, there should be
              // no error to conver long to double float.
              "lastUpdate" -> JsNumber(t._2.getEpochSecond)
            )
          )
        )
      )
    }
  }
}
