package models

import scala.concurrent.Future
import javax.inject.Inject
import javax.inject.Singleton
import play.api.{Configuration, Logger}
import scala.collection.{immutable => imm}
import play.api.libs.ws._
import scala.concurrent.ExecutionContext.Implicits.global

import java.net.URLEncoder

sealed trait LoginAgent {
  def loginUrl: String
}

case class Office365TokenResponse(
  expiresInMillis: Long,
  extExpiresInMillis: Long,
  expiresOnInMillis: Long,
  accessToken: String,
  refreshToken: String,
  idToken: String
)

case class Office365LoginAgent(
  clientId: String, redirectUri: String, clientSecret: String, alwaysLogin: Boolean
) extends LoginAgent {
  val escapedRedirectUri: String = URLEncoder.encode(redirectUri, "UTF-8");

  val loginUrl = "https://login.windows.net/common/oauth2/authorize?response_type=code&client_id=" +
    clientId + "&redirect_uri=" +
    escapedRedirectUri + "&resource=https%3a%2f%2fgraph%2emicrosoft%2ecom%2f" +
    (if (alwaysLogin) "&prompt=login" else "")

  def aquireToken(ws: WSClient, authCode: String): Future[Office365TokenResponse] = {
    ws.url("https://login.windows.net/common/oauth2/token/").post(
      Map(
        "grant_type" -> Seq("authorization_code"),
        "code" -> Seq(authCode),
        "redirect_uri" -> Seq(redirectUri),
        "client_id" -> Seq(clientId),
        "client_secret" -> Seq(clientSecret)
      )
    ).map { resp =>
      val json = resp.json

      Office365TokenResponse(
        expiresInMillis = (json \ "expires_in").as[String].toLong,
        extExpiresInMillis = (json \ "ext_expires_in").as[String].toLong,
        expiresOnInMillis = (json \ "expires_on").as[String].toLong,
        accessToken = (json \ "access_token").as[String],
        refreshToken = (json \ "refresh_token").as[String],
        idToken = (json \ "id_token").as[String]
      )
    }
  }

  def retrieveUserEmail(ws: WSClient, token: String): Future[String] = {
    ws.url("https://graph.microsoft.com/v1.0/me/")
      .addHttpHeaders("Authorization" -> ("Bearer " + token))
      .get()
      .map { resp => (resp.json \ "userPrincipalName").as[String] }
  }
}

@Singleton
class LoginAgentTable @Inject() (conf: Configuration) {
  private[this] var _office365Agent: Option[Office365LoginAgent] = None

  val candidates: imm.Seq[LoginAgent] = conf.get[Seq[Configuration]]("loginAgents").map { e =>
    e.get[String]("type") match {
      case "office365" =>
        _office365Agent = Some(
          Office365LoginAgent(
            e.get[String]("clientId"),
            e.get[String]("redirectUri"),
            e.get[String]("clientSecret"),
            e.get[Boolean]("alwaysLogin")
          )
        )
        _office365Agent.get
      case unknownType: String =>
        throw new RuntimeException("Unknown login agent type is specified: '" + unknownType + "'")
    }
  }.toList

  val office365Agent: Option[Office365LoginAgent] = _office365Agent
}
