package controllers

import javax.inject.{Inject, Singleton}

import models.{LoginSession, WebSnippet, WebSnippetRepo}
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}
import play.api.db.Database

@Singleton
class WebSnippetsMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: NeedLogin.Authenticated,
  db: Database,
  implicit val webSnippetRepo: WebSnippetRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    db.withConnection { implicit conn =>
      val records = webSnippetRepo.list()

      NeedLogin.assumeAdmin(login) {
        Ok(views.html.admin.webSnippetsMaintenance(records))
      }
    }
  }
}
