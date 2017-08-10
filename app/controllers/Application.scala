package controllers

import play.api.mvc._
import javax.inject.{Inject, Singleton}

import models.{LoginSessionRepo, ShoppingCartItemRepo}
import play.api.db.Database

@Singleton
class Application @Inject() (
  cc: MessagesControllerComponents,
  optAuthenticated: NeedLogin.OptAuthenticated,
  loginSessionRepo: LoginSessionRepo,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def index = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin = db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) }
    Ok(views.html.index())
  }

  def notFound(path: String) = Action {
    Results.NotFound
  }
}
