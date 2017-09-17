package controllers

import play.api.i18n.{Messages, MessagesProvider}
import scala.concurrent.ExecutionContext.Implicits.global
import javax.inject.{Inject, Singleton}
import controllers.NeedLogin.Authenticated
import models._
import play.api.mvc._
import play.api.libs.ws._
import play.api.db.Database

@Singleton
class LoginAgentController @Inject() (
  cc: MessagesControllerComponents,
  loginAgentTable: LoginAgentTable,
  ws: WSClient,
  storeUserRepo: StoreUserRepo,
  loginSessionRepo: LoginSessionRepo,
  db: Database
) extends MessagesAbstractController(cc) {
  def loginOffice365(code: String) = Action.async { implicit request: MessagesRequest[AnyContent] =>
    println("LoginAgent.loginOffice365() code: '" + code + "'")
    loginAgentTable.office365Agent match {
      case None => throw new RuntimeException("Office365 login agent is not defined.")
      case Some(office365: Office365LoginAgent) =>
        office365.aquireToken(ws, code).flatMap { resp =>
          office365.retrieveUserEmail(ws, resp.accessToken).map { email =>
            db.withConnection { implicit conn =>
              storeUserRepo.getByEmail(email) match {
                case None =>
                  Redirect(routes.Admin.startLogin("/")).flashing("errorMessage" -> Messages("unregisteredUserEmail", email))
                case Some(user) =>
                  Redirect("/").flashing(
                    "message" -> Messages("welcome")
                  ).withSession {
                    (loginSessionRepo.loginUserKey, loginSessionRepo.serialize(user.id.get, System.currentTimeMillis + loginSessionRepo.sessionTimeout))
                  }
              }
            }
          }
        }
    }
  }
}
