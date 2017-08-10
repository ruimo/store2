package controllers

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import helpers.Sanitize.{forUrl => sanitize}
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.db.Database
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.mvc._

import scala.language.postfixOps

@Singleton
class EntryUserEntry @Inject() (
  cc: MessagesControllerComponents,
  fc: FormConstraints,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val entryUserRegistrationRepo: EntryUserRegistrationRepo,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) with I18n {

  def jaForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "userName" -> text.verifying(fc.normalUserNameConstraint(): _*),
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "zip1" -> text.verifying(z => fc.zip1Pattern.matcher(z).matches),
      "zip2" -> text.verifying(z => fc.zip2Pattern.matcher(z).matches),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "tel" -> text.verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches),
      "fax" -> text.verifying(Messages("error.number"), z => fc.telOptionPattern.matcher(z).matches),
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "firstNameKana" -> text.verifying(fc.firstNameConstraint: _*),
      "lastNameKana" -> text.verifying(fc.lastNameConstraint: _*),
      "email" -> text.verifying(fc.emailConstraint: _*)
    )(entryUserRegistrationRepo.apply4Japan)(entryUserRegistrationRepo.unapply4Japan)
  )

  def userForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "userName" -> text.verifying(fc.normalUserNameConstraint(): _*),
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "middleName" -> optional(text.verifying(fc.middleNameConstraint: _*)),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "email" -> text.verifying(fc.emailConstraint: _*),
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      )
    )(PromoteAnonymousUser.apply)(PromoteAnonymousUser.unapply)
  )

  def showForm(url: String)(
    implicit request: MessagesRequest[AnyContent]
  ): Result = {
    db.withConnection { implicit conn =>
      supportedLangs.preferred(langs) match {
        case `japanese` =>
          Ok(views.html.entryUserEntryJa(jaForm, Address.JapanPrefectures, sanitize(url)))
        case `japan` =>
          Ok(views.html.entryUserEntryJa(jaForm, Address.JapanPrefectures, sanitize(url)))

        case _ =>
          Ok(views.html.entryUserEntryJa(jaForm, Address.JapanPrefectures, sanitize(url)))
      }
    }
  }

  def startRegistrationAsEntryUser(url: String) = Action { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      if (loginSessionRepo.fromRequest(request).isDefined) {
        Redirect(url)
      }
      else showForm(url)
    }
  }

  def submitUserJa(url: String) = Action { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    if (loginSessionRepo.fromRequest(request).isDefined) {
      Redirect(url)
    }
    else {
      jaForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.entryUserEntryJa(formWithErrors, Address.JapanPrefectures, sanitize(url)))
        },
        newUser => db.withConnection { implicit conn: Connection =>
          if (newUser.isNaivePassword) {
            BadRequest(
              views.html.entryUserEntryJa(
                jaForm.fill(newUser).withError("password.main", "naivePassword"),
                Address.JapanPrefectures, sanitize(url)
              )
            )
          }
          else {
            try {
              val user = newUser.save(CountryCode.JPN, storeUserRepo.PasswordHashStretchCount())
              Redirect(url).flashing(
                "message" -> Messages("welcome")
              ).withSession {
                (loginSessionRepo.loginUserKey, loginSessionRepo.serialize(user.id.get, System.currentTimeMillis + loginSessionRepo.sessionTimeout))
              }
            }
            catch {
              case e: UniqueConstraintException =>
                BadRequest(
                  views.html.entryUserEntryJa(
                    jaForm.fill(newUser).withError("userName", "userNameIsTaken"),
                    Address.JapanPrefectures, sanitize(url)
                  )
                )
              case t: Throwable => throw t
            }
          }
        }
      )
    }
  }}}

  def startRegisterCurrentUserAsEntryUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    if (login.isAnonymousBuyer) Ok(
      views.html.promoteAnonymousUser(
        userForm.fill(
          PromoteAnonymousUser(
            "",
            login.storeUser.firstName,
            login.storeUser.middleName,
            login.storeUser.lastName,
            login.storeUser.email,
            ("", "")
          )
        ).discardingErrors
      )
    )
    else Redirect(routes.Application.index.url)
  }

  def promoteAnonymousUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (login.isAnonymousBuyer) {
      userForm.bindFromRequest.fold(
        formWithErrors =>
          BadRequest(views.html.promoteAnonymousUser(formWithErrors)),
        newUser => db.withConnection { implicit conn: Connection =>
          if (newUser.isNaivePassword) {
            BadRequest(
              views.html.promoteAnonymousUser(
                userForm.fill(newUser).withError("password.main", "naivePassword")
              )
            )
          }
          else {
            try {
              if (! newUser.update(login)) {
                throw new Error("Cannot update user " + login)
              }
              Redirect(routes.Application.index).flashing(
                "message" -> Messages("anonymousUserPromoted")
              )
            }
            catch {
              case e: UniqueConstraintException =>
                BadRequest(
                  views.html.promoteAnonymousUser(
                    userForm.fill(newUser).withError("userName", "userNameIsTaken")
                  )
                )
              case t: Throwable => throw t
            }
          }
        }
      )
    }
    else Redirect(routes.Application.index.url)
  }
}
