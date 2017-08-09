package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import helpers.ViewHelpers
import models._
import play.api.Play.current
import play.api.db.Database
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.mvc.{AnyContent, Controller, MessagesAbstractController, MessagesControllerComponents}

import scala.collection.immutable

@Singleton
class ShoppingCart @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val transactionLogItemRepo: TransactionLogItemRepo,
  implicit val taxRepo: TaxRepo
) extends MessagesAbstractController(cc) {
  def quantityInShoppingCartJson = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      Ok(
        Json.toJson(
          JsObject(
            Seq(("quantity", JsNumber(BigDecimal(shoppingCartItemRepo.quantityForUser(login.userId)))))
          )
        )
      )
    }
  }

  def addToCartJson = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    request.body.asJson.map { json =>
      val siteId = (json \ "siteId").as[Long]
      val itemId = (json \ "itemId").as[Long]
      val quantity = (json \ "quantity").as[Int]

      db.withConnection { implicit conn => {
        shoppingCartItemRepo.addItem(login.userId, siteId, itemId, quantity)
        val (cart: ShoppingCartTotal, errors: Seq[ItemExpiredException]) = shoppingCartItemRepo.listItemsForUser(
          localeInfoRepo.getDefault(request.acceptLanguages.toList), login
        )
        implicit val lang = request.acceptLanguages.head

        Ok(
          Json.toJson(
            toJson(errors) ++
            toJson(
              getItemInfo(Map((siteId, itemId) -> quantity), cart.table),
              cart
            )
          )
        )
      }}
    }.get
  }

  def addOrderHistoryJson = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    request.body.asJson.map { json =>
      val siteId = (json \ "siteId").as[Long]
      val tranSiteId = (json \ "tranSiteId").as[Long]

      db.withConnection { implicit conn => {
        val itemsInTran: Seq[TransactionLogItem] = transactionLogItemRepo.listBySite(tranSiteId)
        itemsInTran.foreach { e =>
          shoppingCartItemRepo.addItem(login.userId, siteId, e.itemId, e.quantity.toInt)
        }

        val (cart: ShoppingCartTotal, errors: Seq[ItemExpiredException]) = shoppingCartItemRepo.listItemsForUser(
          localeInfoRepo.getDefault(request.acceptLanguages.toList), login
        )
        implicit val lang = request.acceptLanguages.head

        Ok(
          Json.toJson(
            toJson(errors) ++
            toJson(
              getItemInfo(
                itemsInTran.map {
                  e => (siteId, e.itemId) -> e.quantity.toInt
                }.toMap,
                cart.table
              ),
              cart
            )
          )
        )
      }}
    }.get
  }

  def toJson(errors: Seq[ItemExpiredException]): JsObject = JsObject(
    Seq(
      "expiredItemExists" -> JsBoolean(!errors.isEmpty)
    )
  )

  def toJson(
    addedItems: immutable.Seq[ShoppingCartTotalEntry],
    cart: ShoppingCartTotal
  )(implicit lang: Lang): JsObject = JsObject(
    Seq(
      "added" -> toJson(addedItems),
      "current" -> toJson(cart.table)
    )
  )

  def toJson(
    table: immutable.Seq[ShoppingCartTotalEntry]
  )(implicit lang: Lang): JsArray = JsArray(
    table.map { e =>
      JsObject(
        Seq(
          "itemName" -> JsString(e.itemName.name),
          "siteName" -> JsString(e.site.name),
          "unitPrice" -> JsString(ViewHelpers.toAmount(e.unitPrice)),
          "quantity" -> JsString(e.shoppingCartItem.quantity.toString),
          "price" -> JsString(ViewHelpers.toAmount(e.itemPrice))
        )
      )
    }
  )

  /**
   * @param keys Tupple of siteId, itemId, quantity.
   * @param cart Shopping cart information.
   * @return Shopping cart information.
   * */
  def getItemInfo(
    keys: immutable.Map[(Long, Long), Int], 
    cart: immutable.Seq[ShoppingCartTotalEntry]
  ): immutable.Seq[ShoppingCartTotalEntry] = {
    def getItemInfo(
      quantities: immutable.Map[(Long, Long), Int],
      cart: immutable.Seq[ShoppingCartTotalEntry],
      result: immutable.Vector[ShoppingCartTotalEntry]
    ): immutable.Seq[ShoppingCartTotalEntry] = 
      if (cart.isEmpty) {
        result
      }
      else {
        val cartHead = cart.head
        val keyToDrop = (cartHead.site.id.get, cartHead.itemId)

        quantities.get(keyToDrop) match {
          case None => getItemInfo(quantities, cart.tail, result)
          case Some(quantity) => getItemInfo(
            quantities - keyToDrop,
            cart.tail,
            result :+ cartHead.withNewQuantity(quantity)
          )
        }
      }

    getItemInfo(keys, cart, Vector[ShoppingCartTotalEntry]())
  }

  def removeExpiredItems = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      shoppingCartItemRepo.removeExpiredItems(request.login.storeUser.id.get)
    }
    Redirect(routes.Purchase.showShoppingCart)
  }
}
