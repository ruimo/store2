package controllers

import javax.inject.{Inject, Singleton}

import play.Logger
import controllers.NeedLogin.Authenticated
import play.api.mvc._
import models._
import play.api.db.Database

@Singleton
class Purchase @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val taxRepo: TaxRepo
) extends MessagesAbstractController(cc) {
  def addToCart(siteId: Long, itemId: Long, quantity: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn => {
      val cartItem = shoppingCartItemRepo.addItem(login.userId, siteId, itemId, quantity)
      Redirect(routes.Purchase.showShoppingCart())
    }}
  }

  def showShoppingCart = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    val (total: ShoppingCartTotal, errors: Seq[ItemExpiredException]) = db.withConnection { implicit conn =>
      shoppingCartItemRepo.listItemsForUser(
        localeInfoRepo.getDefault(request.acceptLanguages.toList), login
      )
    }

    if (errors.isEmpty) Ok(views.html.shoppingCart(total)) else Ok(views.html.itemExpired(errors))
  }

  def changeItemQuantity(cartId: Long, quantity: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn => {
      val updateCount = shoppingCartItemRepo.changeQuantity(cartId, login.userId, quantity)
      Logger.info("Purchase.changeItemQuantity() updateCount = " + updateCount)

      Results.Redirect(routes.Purchase.showShoppingCart())
    }}
  }

  def deleteItemFromCart(cartId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn => {
      val updateCount = shoppingCartItemRepo.remove(cartId, login.userId)
      Logger.info("Purchase.deleteItemFromCart() updateCount = " + updateCount)

      Results.Redirect(routes.Purchase.showShoppingCart())
    }}
  }

  def clear = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn => {
      val updateCount = shoppingCartItemRepo.removeForUser(login.userId)
      Logger.info("Purchase.clear() updateCount = " + updateCount)

      Results.Redirect(routes.Purchase.showShoppingCart())
    }}
  }
}
