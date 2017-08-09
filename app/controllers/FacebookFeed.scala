package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.OptAuthenticated
import helpers.{Cache, Facebook, FacebookRepo}
import play.api.mvc._
import play.api.libs.json.{JsNumber, JsObject, JsString, Json}
import play.api.Configuration

@Singleton
class FacebookFeed @Inject() (
  cc: MessagesControllerComponents,
  cache: Cache,
  facebookRepo: FacebookRepo,
  conf: Configuration,
  optAuthenticated: OptAuthenticated
) extends MessagesAbstractController(cc) {
  val facebook: () => Facebook = cache.cacheOnProd(
    () => facebookRepo(
      conf.get[String]("facebook.appId"),
      conf.get[String]("facebook.appSecret")
    )
  )

  def latestPostId(pageId: String) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    facebook().feedsV25(pageId).headOption.map {
      feed => (feed.postId.toString, feed.createdTime)
    } match {
      case None => NotFound("No feed for page '" + pageId + "' found.")
      case Some(t) => Ok(
        Json.toJson(
          JsObject(
            Seq(
              "postId" -> JsString(t._1),
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
