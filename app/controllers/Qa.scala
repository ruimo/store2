package controllers

import helpers.Sanitize.{forUrl => sanitize}
import java.util.Locale

import play.Logger
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models._
import helpers.{QaMail, QaSiteMail}
import constraints.FormConstraints
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.{Authenticated, OptAuthenticated}
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}

@Singleton
class Qa @Inject() (
  cc: MessagesControllerComponents,
  fc: FormConstraints,
  optAuthenticated: OptAuthenticated,
  authenticated: Authenticated,
  qaMail: QaMail,
  qaSiteMail: QaSiteMail,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val db: Database,
  implicit val siteRepo: SiteRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) with I18n {
  def qaSiteForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "command" -> text,
      "companyName" -> text.verifying(nonEmpty, maxLength(64)),
      "name" -> text.verifying(nonEmpty, maxLength(128)),
      "tel" -> text.verifying(nonEmpty).verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches),
      "email" -> text.verifying(fc.emailConstraint: _*),
      "inquiryBody" -> text.verifying(nonEmpty, maxLength(8192))
    )(CreateQaSite.apply)(CreateQaSite.unapply)
  )

  def jaForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "qaType" -> text,
      "comment" -> text.verifying(nonEmpty, maxLength(8192)),
      "companyName" -> text.verifying(nonEmpty, maxLength(64)),
      "firstName" -> text.verifying(nonEmpty, maxLength(64)),
      "lastName" -> text.verifying(nonEmpty, maxLength(64)),
      "tel" -> text.verifying(nonEmpty).verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches),
      "email" -> text.verifying(fc.emailConstraint: _*)
    )(QaEntry.apply4Japan)(QaEntry.unapply4Japan)
  )

  def index() = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    val form = optLogin.map { login =>
      val optAddr: Option[Address] = UserAddress.getByUserId(login.storeUser.id.get).map { ua =>
        Address.byId(ua.addressId)
      }

      jaForm.fill(
        QaEntry(
          qaType = "",
          comment = "",
          companyName = login.storeUser.companyName.getOrElse(""),
          firstName = login.storeUser.firstName,
          middleName = login.storeUser.middleName.getOrElse(""),
          lastName = login.storeUser.lastName,
          tel = optAddr.map(_.tel1).getOrElse(""),
          email = login.storeUser.email
        )
      ).discardingErrors
    }.getOrElse(jaForm)

    request.acceptLanguages.head match {
      case `japanese` =>
        Ok(views.html.qaJa(form))
      case `japan` =>
        Ok(views.html.qaJa(form))
        
      case _ =>
        Ok(views.html.qaJa(form))
    }
  }}}

  def submitQaJa() = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    jaForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.qaJa(formWithErrors))
      },
      qa => {
        qaMail.send(qa)
        Ok(views.html.qaCompleted())
      }
    )
  }}}

  def qaSiteStart(siteId: Long, backLink: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    val user = login.storeUser

    db.withConnection { implicit conn => {
      val addr: Option[Address] = UserAddress.getByUserId(user.id.get).map {
        ua => Address.byId(ua.addressId)
      }

      val site: Site = siteRepo(siteId)
      val form = qaSiteForm.fill(
        CreateQaSite(
          "",
          user.companyName.getOrElse(""),
          user.fullName,
          addr.map(_.tel1).getOrElse(""),
          user.email,
          ""
        )
      ).discardingErrors

      request.acceptLanguages.head match {
        case `japanese` =>
          Ok(views.html.qaSiteJa(site, form, sanitize(backLink)))
        case `japan` =>
          Ok(views.html.qaSiteJa(site, form, sanitize(backLink)))
        
        case _ =>
          Ok(views.html.qaSiteJa(site, form, sanitize(backLink)))
      }
    }}
  }

  def submitQaSiteJa(siteId: Long, backLink: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val site: Site = siteRepo(siteId)

      qaSiteForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in Qa.submitQaSiteJa " + formWithErrors)
          BadRequest(views.html.qaSiteJa(site, formWithErrors, sanitize(backLink)))
        },
        info => {
          if (info.command == "amend") {
            Ok(views.html.qaSiteJa(site, qaSiteForm.fill(info), sanitize(backLink)))
          }
          else if (info.command == "submit") {
            qaSiteMail.send(info, login.storeUser, site)
            Ok(views.html.qaSiteJaCompleted(site, info, sanitize(backLink)))
          }
          else {
            Ok(views.html.qaSiteJaConfirm(site, info, sanitize(backLink)))
          }
        }
      )
    }
  }
}
