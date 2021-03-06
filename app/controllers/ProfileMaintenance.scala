package controllers

import play.api.i18n.{Lang, Messages, MessagesProvider}
import helpers.Forms._
import java.time.Instant
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
import play.api.data.validation.Constraints._

@Singleton
class ProfileMaintenance @Inject() (
  cc: MessagesControllerComponents,
  fc: FormConstraints,
  authenticated: Authenticated,
  userMetadataMaintenance: UserMetadataMaintenance,
  implicit val modifyUserProfileRepo: ModifyUserProfileRepo,
  implicit val db: Database,
  implicit val entryUserRegistrationRepo: EntryUserRegistrationRepo,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) with I18n {
  def changeProfileForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "email" -> email.verifying(fc.emailConstraint: _*),
      "password" -> text.verifying(fc.passwordConstraint: _*),
      "photoUrl" -> optional(text.verifying(maxLength(1024))),
      "firstNameKana" -> optional(text.verifying(maxLength(64))),
      "middleNameKana" -> optional(text.verifying(maxLength(64))),
      "lastNameKana" -> optional(text.verifying(maxLength(64))),
      "telNo0" -> optional(text.verifying(maxLength(64))),
      "telNo1" -> optional(text.verifying(maxLength(64))),
      "telNo2" -> optional(text.verifying(maxLength(64))),
      "joinedDate" -> optional(instant(Messages("joind.date.format"))),
      "birthMonthDay" -> optional(text.verifying(userMetadataMaintenance.birthMonthDayConstraint)),
      "profileComment" -> optional(text.verifying(maxLength(8192)))
    )(ModifyUserProfile.apply)(ModifyUserProfile.unapply)
  )

  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    Ok(views.html.profileMaintenance())
  }

  def changeProfile() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    val form = changeProfileForm.fill(modifyUserProfileRepo(login))
    request.acceptLanguages.head match {
      case `japanese` =>
        Ok(views.html.changeUserProfileJa(form))
      case `japan` =>
        Ok(views.html.changeUserProfileJa(form))
      case _ =>
        Ok(views.html.changeUserProfileJa(form))
    }
  }

  def doChangeProfile() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    changeProfileForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.changeUserProfileJa(formWithErrors))
      },
      updated => {
        if (login.storeUser.passwordMatch(updated.password)) {
          db.withConnection { implicit conn =>
            updated.save(login)
          }
          Redirect(routes.Application.index).flashing("message" -> Messages("userProfileUpdated"))
        }
        else {
          BadRequest(
            views.html.changeUserProfileJa(
              changeProfileForm.fill(updated).withError(
                "password", "currentPasswordNotMatch"
              )
            )
          )
        }
      }
    )
  }
}
