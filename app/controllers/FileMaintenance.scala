package controllers

import models._
import java.util.Locale

import play.api.i18n.{Lang, Messages}
import play.api.data.Forms._
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import play.api.data.Form
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class FileMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  fileCategories: FileCategories,
  implicit val db: Database,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) with I18n {
  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.fileMaintenance(fileCategories.values))
    }
  }
}
