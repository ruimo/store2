package controllers

import play.api.data.validation.{Constraint, Valid, Invalid, ValidationError}
import play.Logger
import helpers.Forms._
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import models._
import javax.inject.{Inject, Singleton}
import controllers.NeedLogin.Authenticated
import play.api.db.Database
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.data.validation.Constraints._

@Singleton
class UserMetadataMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val birthMonthDayConstraint: Constraint[String] = Constraint("birthMonthDay.info") { mmdd =>
    try {
      val mmddValue = mmdd.toInt
      val m = mmddValue / 100
      val d = mmddValue % 100
      if (1 <= m && m <= 12 && 1 <= d && d <= 31) Valid else Invalid(Seq(ValidationError("birthMonthDay.error")))
    }
    catch {
      case e: NumberFormatException =>
        Invalid(Seq(ValidationError("birthMonthDay.error")))
    }
  }

  def updateForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "photoUrl" -> optional(text.verifying(maxLength(1024))),
      "firstNameKana" -> optional(text.verifying(maxLength(64))),
      "middleNameKana" -> optional(text.verifying(maxLength(64))),
      "lastNameKana" -> optional(text.verifying(maxLength(64))),
      "telNo0" -> optional(text.verifying(maxLength(64))),
      "telNo1" -> optional(text.verifying(maxLength(64))),
      "telNo2" -> optional(text.verifying(maxLength(64))),
      "joinedDate" -> optional(instant(Messages("joind.date.format"))),
      "birthMonthDay" -> optional(text.verifying(birthMonthDayConstraint)),
      "profileComment" -> optional(text.verifying(maxLength(8192)))
    )(UpdateUserMetadata.apply)(UpdateUserMetadata.unapply)
  )

  def startModify(storeUserId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val form = db.withConnection { implicit conn =>
        UserMetadata.getByStoreUserId(storeUserId) match {
          case None => updateForm
          case Some(um) => updateForm.fill(
            UpdateUserMetadata(
              um.photoUrl,
              um.firstNameKana, um.middleNameKana, um.lastNameKana,
              um.telNo0, um.telNo1, um.telNo2,
              um.joinedDate,
              um.birthMonthDay.map(_.toString),
              um.profileComment
            )
          )
        }
      }

      Ok(views.html.admin.modifyUserMetadata(storeUserId, form))
    }
  }

  def modify(storeUserId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      updateForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMetadataMaintenance.modify(" + storeUserId + ") " + formWithErrors)
          BadRequest(
            views.html.admin.modifyUserMetadata(storeUserId, formWithErrors)
          )
        },
        um => {
          db.withConnection { implicit conn =>
            um.update(storeUserId)
          }
          Redirect(
            routes.UserMaintenance.editUser()
          ).flashing("message" -> Messages("userIsUpdated"))
        }
      )
    }
  }
}

  
