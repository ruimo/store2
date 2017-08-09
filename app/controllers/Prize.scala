package controllers

import java.util.Locale
import javax.inject.{Inject, Singleton}

import play.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc._
import play.api.i18n.{Lang, Messages, MessagesProvider}
import models.{Address, CountryCode, CreatePrize, JapanPrefecture, StoreUser, UserAddress, ShoppingCartItemRepo}
import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import models.Sex
import helpers.Enums
import helpers.PrizeMail
import play.api.db.Database

@Singleton
class Prize @Inject() (
  cc: MessagesControllerComponents,
  fc: FormConstraints,
  authenticated: Authenticated,
  prizeMail: PrizeMail,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def sexForDropdown(implicit mp: MessagesProvider): Seq[(String, String)] = Seq(
    (Sex.MALE.ordinal.toString, Messages("sex." + Sex.MALE)),
    (Sex.FEMALE.ordinal.toString, Messages("sex." + Sex.FEMALE))
  )

  def prizeFormJa(implicit mp: MessagesProvider) = Form(
    mapping(
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "firstNameKana" -> text.verifying(fc.firstNameConstraint: _*),
      "lastNameKana" -> text.verifying(fc.lastNameConstraint: _*),
      "zip" -> tuple(
        "zip1" -> text.verifying(z => fc.zip1Pattern.matcher(z).matches),
        "zip2" -> text.verifying(z => fc.zip2Pattern.matcher(z).matches)
      ),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "address4" -> text.verifying(maxLength(256)),
      "address5" -> text.verifying(maxLength(256)),
      "tel" -> text.verifying(Messages("error.number"), z => fc.telPattern.matcher(z).matches),
      "comment" -> text.verifying(maxLength(2048)),
      "command" -> text,
      "age" -> text,
      "sex" -> number
    )(CreatePrize.apply4Japan)(CreatePrize.unapply4Japan)
  )

  def entry(itemName: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    val user: StoreUser = login.storeUser
    val addr: Option[Address] = db.withConnection{ implicit conn =>
      UserAddress.getByUserId(user.id.get).map {
        ua => Address.byId(ua.addressId)
      }
    }

    val (countryCode, prefectures, lookupPref) = request.acceptLanguages.head match {
      case japanese =>
        (CountryCode.JPN, Address.JapanPrefectures, JapanPrefecture.byIndex _)
      case japan =>
        (CountryCode.JPN, Address.JapanPrefectures, JapanPrefecture.byIndex _)
      case _ =>
        (CountryCode.JPN, Address.JapanPrefectures, JapanPrefecture.byIndex _)
    }

    val model = CreatePrize(
      countryCode,
      user.firstName, user.middleName.getOrElse(""), user.lastName,
      addr.map(_.firstNameKana).getOrElse(""), addr.map(_.lastNameKana).getOrElse(""),
      (addr.map(_.zip1).getOrElse(""), addr.map(_.zip2).getOrElse(""), addr.map(_.zip3).getOrElse("")),
      lookupPref(addr.map(_.prefecture.code).getOrElse(1)),
      addr.map(_.address1).getOrElse(""),
      addr.map(_.address2).getOrElse(""),
      addr.map(_.address3).getOrElse(""),
      addr.map(_.address4).getOrElse(""),
      addr.map(_.address5).getOrElse(""),
      addr.map(_.tel1).getOrElse(""),
      "", "", "", Sex.MALE
    )

    Ok(
      request.acceptLanguages.head match {
        case japanese =>
          views.html.prizeJa(itemName, user, prefectures, prizeFormJa.fill(model), sexForDropdown)
        case japan =>
          views.html.prizeJa(itemName, user, prefectures, prizeFormJa.fill(model), sexForDropdown)
        case _ =>
          views.html.prizeJa(itemName, user, prefectures, prizeFormJa.fill(model), sexForDropdown)
      }
    )
  }

  def confirm(itemName: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    Ok("") // TODO T.B.D.
  }

  def confirmJa(itemName: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    val user: StoreUser = login.storeUser

    prizeFormJa.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in Prize.confirmJa. " + formWithErrors)
        BadRequest(
          views.html.prizeJa(
            itemName,
            user,
            Address.JapanPrefectures,
            formWithErrors,
            sexForDropdown
          )
        )
      },
      info => {
        Ok(
          views.html.prizeConfirmJa(
            itemName,
            user,
            info
          )
        )
      }
    )
  }

  def submit(itemName: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    Ok("") // T.B.D.
  }

  def submitJa(itemName: String) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    val user: StoreUser = login.storeUser

    prizeFormJa.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in Prize.submitJa. " + formWithErrors)
        BadRequest(
          views.html.prizeJa(
            itemName,
            user,
            Address.JapanPrefectures,
            formWithErrors,
            sexForDropdown
          )
        )
      },
      info => {
        if (info.command == "amend") {
          Ok(
            views.html.prizeJa(
              itemName,
              user,
              Address.JapanPrefectures,
              prizeFormJa.fill(info),
              sexForDropdown
            )
          )
        }
        else {
          prizeMail.send(itemName, user, info)
          Ok(
            views.html.prizeCompleted(
              itemName,
              user,
              info
            )
          )
        }
      }
    )
  }
}
