package controllers

import play.api.data.validation.Constraints._
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
class UserGroupMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createForm = Form(
    mapping(
      "groupName" -> text.verifying(nonEmpty, maxLength(256))
    ) (CreateUserGroup.apply)(CreateUserGroup.unapply)
  )

  def startCreate() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.createUserGroup())
    }
  }
}

