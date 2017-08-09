package controllers

import scala.collection.immutable
import helpers.Cache
import play.api.i18n.{Lang, Messages}
import play.api.data.Forms._
import models._
import play.api.data.Form

import collection.immutable.LongMap
import play.api.mvc._
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import com.ruimo.recoeng.RecoEngApi
import controllers.NeedLogin.Authenticated
import helpers.{Enums, NotificationMail, RecommendEngine}
import play.api.db.Database

@Singleton
class Paypal @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  notificationMail: NotificationMail,
  recoEng: RecommendEngine,
  shipping: Shipping,
  admin: Admin,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val transactionPersister: TransactionPersister
) extends MessagesAbstractController(cc) {
  def onSuccess(transactionId: Long, token: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      if (TransactionLogPaypalStatus.onSuccess(transactionId, token) == 0) {
        Redirect(routes.Application.index())
      }
      else {
        shoppingCartItemRepo.removeForUser(login.userId)
        ShoppingCartShipping.removeForUser(login.userId)
        val tran = transactionPersister.load(transactionId, localeInfoRepo.getDefault(request.acceptLanguages.toList))
        val address = Address.byId(tran.shippingTable.head._2.head.addressId)
        notificationMail.orderCompleted(login, tran, Some(address))
        recoEng.onSales(login, tran, Some(address))
        Ok(
          views.html.showTransactionJa(
            tran, Some(address), shipping.textMetadata(tran), shipping.siteItemMetadata(tran),
            admin.anonymousCanPurchase() && login.isAnonymousBuyer
          )
        )
      }
    }
  }

  def onCancel(transactionId: Long, token: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      if (TransactionLogPaypalStatus.onCancel(transactionId, token) == 0) {
        Redirect(routes.Application.index())
      }
      else {
        Ok(views.html.cancelPaypal())
      }
    }
  }

  def fakePaypal(cmd: String, token: String) = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(
      views.html.fakePaypal(cmd, token)
    )
  }

  def onWebPaymentPlusSuccess(transactionId: Long, token: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      if (TransactionLogPaypalStatus.onWebPaymentPlusSuccess(transactionId, token) == 0) {
        Redirect(routes.Application.index())
      }
      else {
        shoppingCartItemRepo.removeForUser(login.userId)
        ShoppingCartShipping.removeForUser(login.userId)
        val tran = transactionPersister.load(transactionId, localeInfoRepo.getDefault(request.acceptLanguages.toList))
        val address = Address.byId(tran.shippingTable.head._2.head.addressId)
        notificationMail.orderCompleted(login, tran, Some(address))
        recoEng.onSales(login, tran, Some(address))
        Ok(
          views.html.showTransactionJa(
            tran, Some(address), shipping.textMetadata(tran), shipping.siteItemMetadata(tran),
            admin.anonymousCanPurchase() && login.isAnonymousBuyer
          )
        )
      }
    }
  }

  def onWebPaymentPlusCancel(transactionId: Long, token: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      if (TransactionLogPaypalStatus.onWebPaymentPlusCancel(transactionId, token) == 0) {
        Redirect(routes.Application.index())
      }
      else {
        Ok(views.html.cancelPaypal())
      }
    }
  }
}
