package controllers

import helpers.Cache
import play.api.i18n.Lang
import models._

import collection.immutable
import play.api.data.Form
import play.api.data.Forms._
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.db.Database
import play.api.mvc._

@Singleton
class UserQuery @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  cache: Cache,
  val storeUserRepo: StoreUserRepo,
  val siteRepo: SiteRepo,
  val employeeRepo: EmployeeRepo,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def usersCanViewProfiles: Boolean = cache.config(_.get[Boolean]("usersCanViewProfiles"))()

  def index(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (! usersCanViewProfiles) Redirect(routes.Application.index)
    else {
      db.withConnection { implicit conn =>
        Ok(
          views.html.listUsers(
            storeUserRepo.listUsers(page, pageSize, OrderBy(orderBySpec)).map { rec =>
              (
                rec._1,
                rec._2,
                employeeRepo.list(rec._1.user.id.get)
              )
            }
          )
        )
      }
    }
  }
}
