package controllers

import javax.inject.{Inject, Singleton}

import helpers.PasswordHash
import play.api.Logger
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models._
import helpers.UserEntryMail
import constraints.FormConstraints
import play.twirl.api.Html
import java.sql.Connection
import javax.inject.Inject

import helpers.NotificationMail

import scala.concurrent.duration._
import scala.language.postfixOps
import helpers.Cache
import play.api.data.Forms._
import play.api.db.Database

@Singleton
class UserEntry @Inject() (
  cc: MessagesControllerComponents,
  val cache: Cache,
  db: Database,
  userEntryMail: UserEntryMail,
  notificationMail: NotificationMail,
  fc: FormConstraints,
  registerUserInfoRepo: RegisterUserInfoRepo,
  authenticated: NeedLogin.Authenticated,
  implicit val storeUserRepo: StoreUserRepo,
  loginSessionRepo: LoginSessionRepo,
  resetPasswordRepo: ResetPasswordRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) with I18n {
  def ResetPasswordTimeout: () => Long = cache.config(_.getOptional[Duration]("resetPassword.timeout").getOrElse(30 minutes).toMillis)
  def AutoLoginAfterRegistration: () => Boolean = cache.cacheOnProd(
    () => cache.Conf.getOptional[Boolean]("auto.login.after.registration").getOrElse(false)
  )

  def jaForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "companyName" -> text.verifying(nonEmpty, maxLength(64)),
      "zip1" -> text.verifying(z => fc.zip1Pattern.matcher(z).matches),
      "zip2" -> text.verifying(z => fc.zip2Pattern.matcher(z).matches),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "tel" -> text.verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches),
      "fax" -> text.verifying(Messages("error.number"), z => fc.telOptionPattern.matcher(z).matches),
      "title" -> text.verifying(maxLength(256)),
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "email" -> text.verifying(fc.emailConstraint: _*)
    )(UserRegistration.apply4Japan)(UserRegistration.unapply4Japan)
  )

  val resetPasswordForm = Form(
    mapping(
      "companyId" -> optional(text),
      "userName" -> text.verifying(nonEmpty)
    )(PasswordReset.apply)(PasswordReset.unapply)
  )

  def changePasswordForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "currentPassword" -> text.verifying(nonEmpty),
      "newPassword" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text.verifying(fc.passwordConstraint: _*)
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      )
    )(ChangePassword.apply)(ChangePassword.unapply)
  )

  def resetWithNewPasswordForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "userId" -> longNumber,
      "token" -> longNumber,
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text.verifying(fc.passwordConstraint: _*)
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      )
    )(ResetWithNewPassword.apply)(ResetWithNewPassword.unapply)
  )

  def index = Action { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      implicit val login: Option[LoginSession] = loginSessionRepo.fromRequest(request)
      supportedLangs.preferred(langs) match {
        case japanese =>
          Ok(views.html.userEntryJa(jaForm, Address.JapanPrefectures))
        case japan =>
          Ok(views.html.userEntryJa(jaForm, Address.JapanPrefectures))
        
        case _ =>
          Ok(views.html.userEntryJa(jaForm, Address.JapanPrefectures))
      }
    }
  }

  def submitUserJa = Action { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      implicit val login: Option[LoginSession] = loginSessionRepo.fromRequest(request)
      jaForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.userEntryJa(formWithErrors, Address.JapanPrefectures))
        },
        newUser => {
          userEntryMail.sendUserRegistration(newUser)
          Ok(views.html.userEntryCompleted())
        }
      )
    }
  }

  def updateUserInfoForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "firstNameKana" -> text.verifying(fc.firstNameKanaConstraint: _*),
      "lastNameKana" -> text.verifying(fc.lastNameKanaConstraint: _*),
      "email" -> email.verifying(fc.emailConstraint: _*),
      "currentPassword" -> text.verifying(nonEmpty, maxLength(24)),
      "country" -> number,
      "zip1" -> text.verifying(z => fc.zip1Pattern.matcher(z).matches),
      "zip2" -> text.verifying(z => fc.zip2Pattern.matcher(z).matches),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "tel1" -> text.verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches)
    )(ChangeUserInfo.apply)(ChangeUserInfo.unapply)
  )
    

  def createRegistrationForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "firstNameKana" -> text.verifying(fc.firstNameKanaConstraint: _*),
      "lastNameKana" -> text.verifying(fc.lastNameKanaConstraint: _*),
      "email" -> email.verifying(fc.emailConstraint: _*),
      "currentPassword" -> text.verifying(nonEmpty, maxLength(24)),
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "country" -> number,
      "zip1" -> text.verifying(z => fc.zip1Pattern.matcher(z).matches),
      "zip2" -> text.verifying(z => fc.zip2Pattern.matcher(z).matches),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "tel1" -> text.verifying(nonEmpty).verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches)
    )(registerUserInfoRepo.apply4Japan)(registerUserInfoRepo.unapply4Japan)
  )

  def registerUserInformation(userId: Long) = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(
      supportedLangs.preferred(langs) match {
        case japanese =>
          registerUserInformationView(userId, createRegistrationForm)
        case japan =>
          registerUserInformationView(userId, createRegistrationForm)

        case _ =>
          registerUserInformationView(userId, createRegistrationForm)
      }
    )
  }

  def submitUserInfo(userId: Long) = Action { implicit request: MessagesRequest[AnyContent] =>
    createRegistrationForm.bindFromRequest.fold(
      formWithErrors =>
        BadRequest(registerUserInformationView(userId, formWithErrors)),
      newInfo =>
        db.withConnection { implicit conn =>
          if (! newInfo.currentPasswordNotMatch(userId)) {
            BadRequest(
              registerUserInformationView(
                userId,
                createRegistrationForm.fill(newInfo).withError("currentPassword", Messages("currentPasswordNotMatch"))
              )
            )
          }
          else if (newInfo.isNaivePassword) {
            BadRequest(
              registerUserInformationView(
                userId,
                createRegistrationForm.fill(newInfo).withError("password.main", Messages("naivePassword"))
              )
            )
          }
          else {
            val u = storeUserRepo(userId)
            storeUserRepo.update(
              userId,
              u.userName,
              newInfo.firstName,
              newInfo.middleName,
              newInfo.lastName,
              newInfo.email,
              PasswordHash.generate(newInfo.passwords._1, u.salt),
              u.salt,
              u.companyName
            )

            val address = Address.createNew(
              countryCode = newInfo.countryCode,
              firstName = newInfo.firstName,
              middleName = newInfo.middleName.getOrElse(""),
              lastName = newInfo.lastName,
              firstNameKana = newInfo.firstNameKana,
              lastNameKana = newInfo.lastNameKana,
              zip1 = newInfo.zip1,
              zip2 = newInfo.zip2,
              prefecture = newInfo.prefecture,
              address1 = newInfo.address1,
              address2 = newInfo.address2,
              address3 = newInfo.address3,
              tel1 = newInfo.tel1
            )

            UserAddress.createNew(userId, address.id.get)
            val result = Redirect(
              routes.Application.index
            ).flashing(
              "message" -> Messages("userInfoIsUpdated")
            )

            if (AutoLoginAfterRegistration()) {
              result.withSession {
                (loginSessionRepo.loginUserKey, loginSessionRepo.serialize(userId, System.currentTimeMillis + loginSessionRepo.sessionTimeout))
              }
            }
            else result
          }
        }
    )
  }

  def registerUserInformationView(
    userId: Long,
    form: Form[RegisterUserInfo]
  )(
    implicit request: MessagesRequest[AnyContent]
  ): Html = supportedLangs.preferred(langs) match {
    case japanese =>
      views.html.registerUserInformationJa(userId, form, Address.JapanPrefectures)
    case japan =>
      views.html.registerUserInformationJa(userId, form, Address.JapanPrefectures)

    case _ =>
      views.html.registerUserInformationJa(userId, form, Address.JapanPrefectures)
  }

  def updateUserInfoStart = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn =>
      val currentInfo: Option[ChangeUserInfo] = UserAddress.getByUserId(login.storeUser.id.get) map { ua =>
        val user = login.storeUser
        val addr = Address.byId(ua.addressId)
        ChangeUserInfo(
          firstName = user.firstName,
          middleName = user.middleName,
          lastName = user.lastName,
          firstNameKana = addr.firstNameKana,
          lastNameKana = addr.lastNameKana,
          email = user.email, 
          currentPassword = "",
          countryIndex = addr.countryCode.ordinal,
          zip1 = addr.zip1,
          zip2 = addr.zip2,
          prefectureIndex = addr.prefecture.code,
          address1 = addr.address1,
          address2 = addr.address2,
          address3 = addr.address3,
          tel1 = addr.tel1
        )
      }

      val form = currentInfo match {
        case Some(info) => updateUserInfoForm.fill(info)
        case None => updateUserInfoForm
      }

      Ok(
        supportedLangs.preferred(langs) match {
          case japanese =>
            views.html.updateUserInfoJa(form, Address.JapanPrefectures)
          case japan =>
            views.html.updateUserInfoJa(form, Address.JapanPrefectures)

          case _ =>
            views.html.updateUserInfoJa(form, Address.JapanPrefectures)
        }
      )
    }
  }

  def updateUserInfo = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    updateUserInfoForm.bindFromRequest.fold(
      formWithErrors => BadRequest(
        supportedLangs.preferred(langs) match {
          case japanese =>
            views.html.updateUserInfoJa(formWithErrors, Address.JapanPrefectures)
          case japan =>
            views.html.updateUserInfoJa(formWithErrors, Address.JapanPrefectures)

          case _ =>
            views.html.updateUserInfoJa(formWithErrors, Address.JapanPrefectures)
        }
      ),
      newInfo => {
        val prefTable = supportedLangs.preferred(langs) match {
          case japanese =>
            i: Int => JapanPrefecture.byIndex(i)
          case japan =>
            i: Int => JapanPrefecture.byIndex(i)

          case _ =>
            i: Int => JapanPrefecture.byIndex(i)
        }

        db.withConnection { implicit conn =>
          if (login.storeUser.passwordMatch(newInfo.currentPassword)) {
            updateUser(newInfo, login.storeUser)

            UserAddress.getByUserId(login.storeUser.id.get) match {
              case Some(ua: UserAddress) =>
                updateAddress(Address.byId(ua.addressId), newInfo, prefTable)
              case None =>
                val address = createAddress(newInfo, prefTable)
                UserAddress.createNew(login.storeUser.id.get, address.id.get)
            }

            Redirect(routes.Application.index).flashing("message" -> Messages("userInfoIsUpdated"))
          }
          else {
            val form = updateUserInfoForm.fill(newInfo).withError("currentPassword", "confirmPasswordDoesNotMatch")
            BadRequest(
              supportedLangs.preferred(langs).toLocale match {
                case japanese =>
                  views.html.updateUserInfoJa(form, Address.JapanPrefectures)
                case japan =>
                  views.html.updateUserInfoJa(form, Address.JapanPrefectures)

                case _ =>
                  views.html.updateUserInfoJa(form, Address.JapanPrefectures)
              }
            )
          }
        }
      }
    )
  }

  def updateUser(userInfo: ChangeUserInfo, user: StoreUser)(implicit conn: Connection) {
    storeUserRepo.update(
      user.id.get,
      user.userName, 
      userInfo.firstName, userInfo.middleName, userInfo.lastName,
      userInfo.email, user.passwordHash, user.salt, user.companyName
    )
  }

  def updateAddress(
    address: Address, userInfo: ChangeUserInfo, prefectureTable: Int => Prefecture
  )(implicit conn: Connection) {
    Address.update(
      address.copy(
        countryCode = CountryCode.byIndex(userInfo.countryIndex),
        firstName = userInfo.firstName,
        middleName = userInfo.middleName.getOrElse(""),
        lastName = userInfo.lastName,
        firstNameKana = userInfo.firstNameKana,
        lastNameKana = userInfo.lastNameKana,
        zip1 = userInfo.zip1,
        zip2 = userInfo.zip2,
        prefecture = prefectureTable(userInfo.prefectureIndex),
        address1 = userInfo.address1,
        address2 = userInfo.address2,
        address3 = userInfo.address3,
        tel1 = userInfo.tel1
      )
    )
  }

  def createAddress(
    userInfo: ChangeUserInfo, prefectureTable: Int => Prefecture
  )(implicit conn: Connection): Address = {
    Address.createNew(
      countryCode = CountryCode.byIndex(userInfo.countryIndex),
      firstName = userInfo.firstName,
      middleName = userInfo.middleName.getOrElse(""),
      lastName = userInfo.lastName,
      firstNameKana = userInfo.firstNameKana,
      lastNameKana = userInfo.lastNameKana,
      zip1 = userInfo.zip1,
      zip2 = userInfo.zip2,
      prefecture = prefectureTable(userInfo.prefectureIndex),
      address1 = userInfo.address1,
      address2 = userInfo.address2,
      address3 = userInfo.address3,
      tel1 = userInfo.tel1
    )
  }

  def resetPasswordStart = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.resetPassword(resetPasswordForm))
  }

  def resetPassword = Action { implicit request: MessagesRequest[AnyContent] =>
    resetPasswordForm.bindFromRequest.fold(
      formWithErrors =>
        BadRequest(views.html.resetPassword(formWithErrors)),
      newInfo => {
        db.withConnection { implicit conn =>
          storeUserRepo.findByUserName(newInfo.compoundUserName) match {
            case Some(user) => {
              resetPasswordRepo.removeByStoreUserId(user.id.get)
              val rec = resetPasswordRepo.createNew(user.id.get)
              notificationMail.sendResetPasswordConfirmation(user, rec)
              Ok(views.html.resetPasswordMailSent(rec))
            }

            case None =>
              BadRequest(
                views.html.resetPassword(
                  resetPasswordForm.fill(newInfo).withError("userName", "error.value")
                )
              )
          }
        }
      }
    )
  }

  def resetPasswordConfirm(userId: Long, token: Long) = Action { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      Logger.info("Password reset confirmation ok = " + userId + ", token = " + token)
      if (resetPasswordRepo.isValid(userId, token, System.currentTimeMillis - ResetPasswordTimeout())) {
        Ok(
          views.html.resetWithNewPassword(
            resetWithNewPasswordForm.bind(
              Map(
                "userId" -> userId.toString,
                "token" -> token.toString
              )
            ).discardingErrors
          )
        )
      }
      else {
        Logger.error("Invalid password reset confirmation userId = " + userId + ", token = " + token)
        Redirect(routes.Application.index).flashing("message" -> Messages("general.error"))
      }
    }
  }

  def resetWithNewPassword = Action { implicit request: MessagesRequest[AnyContent] =>
    resetWithNewPasswordForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.resetWithNewPassword(formWithErrors))
      },
      newInfo => {
        db.withConnection { implicit conn =>
          if (
            resetPasswordRepo.changePassword(
              newInfo.userId,
              newInfo.token, 
              System.currentTimeMillis - ResetPasswordTimeout(),
              newInfo.passwords._1
            )
          ) {
            Redirect(routes.UserEntry.resetPasswordCompleted)
          }
          else {
            Logger.error(
              "Cannot change password: userId = " + newInfo.userId + ", token = " + newInfo.token
            )
            Redirect(routes.Application.index)
              .flashing("message" -> Messages("general.error"))
          }
        }
      }
    )
  }

  def resetPasswordCompleted = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.resetPasswordCompleted())
  }

  def changePasswordStart = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    Ok(views.html.changePassword(changePasswordForm))
  }

  def changePassword = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    changePasswordForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in changing password. '" + login.storeUser.userName + "'")
        BadRequest(views.html.changePassword(formWithErrors))
      },
      newInfo => {
        if (! login.storeUser.passwordMatch(newInfo.currentPassword)) {
          Logger.error("Current password does not match. '" + login.storeUser.userName + "'")
          BadRequest(
            views.html.changePassword(
              changePasswordForm.withError("currentPassword", Messages("currentPasswordNotMatch"))
            )
          )
        }
        else {
          db.withConnection { implicit conn =>
            if (newInfo.changePassword(login.storeUser.id.get)) {
              Redirect(routes.Application.index)
                .flashing("message" -> Messages("passwordIsUpdated"))
            }
            else {
              BadRequest(
                views.html.changePassword(
                  changePasswordForm.withGlobalError("general.error")
                )
              )
            }
          }
        }
      }
    )
  }
}
