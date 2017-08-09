package controllers

import play.Logger
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form
import models._
import play.api.db.Database
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}


@Singleton
class TaxMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val taxRepo: TaxRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val taxNameRepo: TaxNameRepo,
  implicit val taxHistoryRepo: TaxHistoryRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createTaxForm = Form(
    mapping(
      "taxType" -> number,
      "langId" -> longNumber,
      "taxName" -> text.verifying(nonEmpty, maxLength(32)),
      "rate" -> bigDecimal(5, 3)
    ) (CreateTax.apply)(CreateTax.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.taxMaintenance())
    }
  }

  def startCreateNewTax = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.createNewTax(createTaxForm, taxRepo.taxTypeTable, localeInfoRepo.localeTable))
    }
  }

  def createNewTax = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      createTaxForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in TaxMaintenance.createNewTax.")
          BadRequest(views.html.admin.createNewTax(formWithErrors, taxRepo.taxTypeTable, localeInfoRepo.localeTable))
        },
        newTax => {
          db.withConnection { implicit conn =>
            newTax.save()
          }
          Redirect(
            routes.TaxMaintenance.startCreateNewTax
          ).flashing("message" -> Messages("taxIsCreated"))
        }
      )
    }
  }

  def editTax = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.editTax())
    }
  }
}

