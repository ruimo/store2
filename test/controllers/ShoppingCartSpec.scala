package controllers

import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import collection.immutable
import models._
import org.specs2.mock.Mockito
import com.ruimo.scoins.Scoping._
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.{Application => PlayApp}

class ShoppingCartSpec extends Specification with Mockito with InjectorSupport {
  "ShoppingCart" should {
    "getItemInfo can extract items in shopping cart" in {
      implicit val app: PlayApp = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      val cart: immutable.Seq[ShoppingCartTotalEntry] = List(
        ShoppingCartTotalEntry(
          ShoppingCartItem(
            id = mock[Option[Long]],
            storeUserId = 0L,
            sequenceNumber = 0,
            siteId = 1,
            itemId = 2,
            quantity = 3
          ),
          itemName = mock[ItemName],
          itemDescription = mock[ItemDescription],
          site = Site(Some(1L), 0L, "site1"),
          itemPriceHistory = mock[ItemPriceHistory],
          taxHistory = mock[TaxHistory],
          itemPriceStrategy = UnitPriceStrategy
        ),

        ShoppingCartTotalEntry(
          ShoppingCartItem(
            id = mock[Option[Long]],
            storeUserId = 0L,
            sequenceNumber = 1,
            siteId = 1,
            itemId = 3,
            quantity = 2
          ),
          itemName = mock[ItemName],
          itemDescription = mock[ItemDescription],
          site = Site(Some(1L), 0L, "site1"),
          itemPriceHistory = mock[ItemPriceHistory],
          taxHistory = mock[TaxHistory],
          itemPriceStrategy = UnitPriceStrategy
        )
      )

      doWith(
        inject[controllers.ShoppingCart].getItemInfo(
          Map(), 
          cart
        )
      ) { newCart =>
        newCart.size === 0
      }

      doWith(
        inject[controllers.ShoppingCart].getItemInfo(
          Map((1L, 2L) -> 2), 
          cart
        )
      ) { newCart =>
        newCart.size === 1
        doWith(newCart.head.shoppingCartItem) { item =>
          item.siteId === 1L
          item.itemId === 2L
          item.quantity === 2
        }
        newCart.head.itemName === cart(0).itemName
      }

      doWith(
        inject[controllers.ShoppingCart].getItemInfo(
          Map((1L, 3L) -> 1), 
          cart
        )
      ) { newCart =>
        newCart.size === 1
        doWith(newCart.head.shoppingCartItem) { item =>
          item.siteId === 1L
          item.itemId === 3L
          item.quantity === 1
        }
        newCart.head.itemName === cart(1).itemName
      }
    }
  }
}
