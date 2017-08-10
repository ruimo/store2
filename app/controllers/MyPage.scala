package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}
import models.ShoppingCartItemRepo
import play.api.db.Database

@Singleton
class MyPage @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val db: Database,
) extends MessagesAbstractController(cc) {
  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      Ok(views.html.myPage())
  }
}


