package functional

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import org.joda.time.format.DateTimeFormat
import helpers.ViewHelpers
import java.util.concurrent.TimeUnit
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection

import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.test.TestServer
import com.ruimo.scoins.Scoping._

class CouponSalesSpec extends Specification with InjectorSupport {
  "Coupon sale" should {
    "If the all of items are coupon, shipping address should be skipped." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(inMemoryDatabase(options = Map("MVCC" -> "true")))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val tax = inject[TaxRepo].createNew
        val his = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("8"), date("9999-12-31"))
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木", En -> "Plant"))

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)
        val item3 = inject[ItemRepo].createNew(cat1)
        
        Coupon.updateAsCoupon(item1.id.get)
        Coupon.updateAsCoupon(item2.id.get)
        Coupon.updateAsCoupon(item3.id.get)

        val name1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "クーポン1", En -> "Coupon1"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "クーポン2", En -> "Coupon2"))
        val name3 = inject[ItemNameRepo].createNew(item3, Map(Ja -> "クーポン3", En -> "Coupon3"))

        val desc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "クーポン1説明")
        val desc2 = inject[ItemDescriptionRepo].createNew(item2, site2, "クーポン2説明")
        val desc3 = inject[ItemDescriptionRepo].createNew(item3, site2, "クーポン3説明")

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)
        inject[SiteItemRepo].createNew(site2, item3)

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)
        val price3 = inject[ItemPriceRepo].createNew(item3, site2)

        val ph1 = inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(101), None, BigDecimal(90), date("9999-12-31")
        )
        val ph2 = inject[ItemPriceHistoryRepo].createNew(
          price2, tax, currencyInfo.Jpy, BigDecimal(301), None, BigDecimal(200), date("9999-12-31")
        )
        val ph3 = inject[ItemPriceHistoryRepo].createNew(
          price3, tax, currencyInfo.Jpy, BigDecimal(401), None, BigDecimal(390), date("9999-12-31")
        )

        val cart1 = inject[ShoppingCartItemRepo].addItem(user.id.get, site1.id.get, item1.id.get.id, 15)
        val cart2 = inject[ShoppingCartItemRepo].addItem(user.id.get, site2.id.get, item2.id.get.id, 28)
        val cart3 = inject[ShoppingCartItemRepo].addItem(user.id.get, site2.id.get, item3.id.get.id, 40)

        browser.goTo(
          controllers.routes.Shipping.startEnterShippingAddress().url + "?lang=" + lang.code
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("confirm.shipping.address"))

        doWith(browser.find(".itemTableBody").index(0)) { b =>
          b.find(".itemName").text === "クーポン1"
          b.find(".siteName").text === "商店1"
          b.find(".itemQuantity").text === "15"
          b.find(".itemPrice").text === ViewHelpers.toAmount(BigDecimal(101 * 15))
        }

        doWith(browser.find(".itemTableBody").index(1)) { b =>
          b.find(".itemName").text === "クーポン2"
          b.find(".siteName").text === "商店2"
          b.find(".itemQuantity").text === "28"
          b.find(".itemPrice").text === ViewHelpers.toAmount(BigDecimal(301 * 28))
        }

        doWith(browser.find(".itemTableBody").index(2)) { b =>
          b.find(".itemName").text === "クーポン3"
          b.find(".siteName").text === "商店2"
          b.find(".itemQuantity").text === "40"
          b.find(".itemPrice").text === ViewHelpers.toAmount(BigDecimal(401 * 40))
        }

        browser.find(".subtotal").text === ViewHelpers.toAmount(
          BigDecimal(101 * 15 + 301 * 28 + 401 * 40)
        )

        val now = System.currentTimeMillis
        browser.find("#paypalimg").size === 0
        browser.find(".payByAccountingBill").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find(".itemTableBody").index(0)) { b =>
          b.find(".itemName .itemNameBody").text === "クーポン1"
          b.find(".siteName").text === "商店1"
          b.find(".quantity").text === "15"
          b.find(".itemPrice").text === ViewHelpers.toAmount(BigDecimal(1515))
        }

        doWith(browser.find(".itemTableBody").index(1)) { b =>
          b.find(".itemName .itemNameBody").text === "クーポン2"
          b.find(".siteName").text === "商店2"
          b.find(".quantity").text === "28"
          b.find(".itemPrice").text === ViewHelpers.toAmount(BigDecimal(8428))
        }

        doWith(browser.find(".itemTableBody").index(2)) { b =>
          b.find(".itemName .itemNameBody").text === "クーポン3"
          b.find(".siteName").text === "商店2"
          b.find(".quantity").text === "40"
          b.find(".itemPrice").text === ViewHelpers.toAmount(BigDecimal(16040))
        }

        browser.find(".itemTableBody").index(2).find(".itemName input").click()

        val currentWindow = browser.webDriver.getWindowHandle
        val allWindows = browser.webDriver.getWindowHandles
        allWindows.remove(currentWindow)
        browser.webDriver.switchTo().window(allWindows.iterator.next)

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".date").find("span").index(1).text() === 
          DateTimeFormat.forPattern(Messages("published.date.format")).print(now)
        browser.find(".siteName").text() === Messages("coupon.user.company.name", "Company1")
        browser.find(".name").text() === justOneSpace(
          Messages("coupon.user.name", "Admin", "", "Manager")
        )
      }
    }
  }
}
