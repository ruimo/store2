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
import play.api.test.Helpers._
import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.test.TestServer
import java.sql.Date.{valueOf => date}
import java.util.concurrent.TimeUnit
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxProfile}
import com.ruimo.scoins.Scoping._

class ShoppingCartSpec extends Specification with InjectorSupport {
  implicit def date2milli(d: java.sql.Date) = d.getTime

  "ShoppingCart" should {
    "Show cart dialog" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)

        val site = inject[SiteRepo].createNew(Ja, "商店1")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        val item2 = inject[ItemRepo].createNew(cat)
        val siteItem2 = inject[SiteItemRepo].createNew(site, item2)
        val itemName2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "松"))
        val itemDesc2 = inject[ItemDescriptionRepo].createNew(item2, site, "松説明")
        val itemPrice2 = inject[ItemPriceRepo].createNew(item2, site)
        val itemPriceHistory2 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice2, tax, currencyInfo.Jpy, BigDecimal(777), None, BigDecimal("666"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        inject[RecommendByAdminRepo].createNew(site.id.get, item.id.get.id, 10)
        inject[RecommendByAdminRepo].createNew(site.id.get, item2.id.get.id, 20)

        browser.goTo(
          controllers.routes.ItemQuery.query(List()) + "?lang=" + lang.code
        )
        browser.waitUntil {
          failFalse(browser.find(".addToCartButton").size > 0)
        }
        browser.find(".addToCartButton").index(0).click()

        browser.waitUntil {
          failFalse(browser.find("#cartDialogAddedContent tr .body.itemName").size > 0)
        }

        browser.find("#cartDialogAddedContent tr .body.itemName").text === "かえで"
        browser.find("#cartDialogAddedContent tr .body.siteName").text === "商店1"
        browser.find("#cartDialogAddedContent tr .body.unitPrice").text === "999円"
        browser.find("#cartDialogAddedContent tr .body.quantity").text === "1"
        browser.find("#cartDialogAddedContent tr .body.price").text === "999円"

        browser.find("#cartDialogCurrentContent tr .body.itemName").text === "かえで"
        browser.find("#cartDialogCurrentContent tr .body.siteName").text === "商店1"
        browser.find("#cartDialogCurrentContent tr .body.unitPrice").text === "999円"
        browser.find("#cartDialogCurrentContent tr .body.quantity").text === "1"
        browser.find("#cartDialogCurrentContent tr .body.price").text === "999円"

        doWith(browser.find(".recommendedItem")) { e =>
          e.find("a").attribute("href").endsWith(controllers.routes.ItemDetail.show(item2.id.get.id, site.id.get).url) === true
          doWith(e.find("a")) { a =>
            a.find("img").attribute("src").endsWith(controllers.routes.ItemPictures.getPicture(item2.id.get.id, 0).url) === true
            a.find("div.itemName").text === "松"
            a.find("div.price").text === "777円"
          }
        }

        // Close button
        browser.find(".ui-dialog-buttonset button").get(0).click()

        browser.find(".addToCartButton").index(0).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.waitUntil {
          failFalse(browser.find("#cartDialogCurrentContent tr .body.quantity").text == "2")
        }
        browser.find("#cartDialogAddedContent tr .body.itemName").text === "かえで"
        browser.find("#cartDialogAddedContent tr .body.siteName").text === "商店1"
        browser.find("#cartDialogAddedContent tr .body.unitPrice").text === "999円"
        browser.find("#cartDialogAddedContent tr .body.quantity").text === "1"
        browser.find("#cartDialogAddedContent tr .body.price").text === "999円"

        browser.find("#cartDialogCurrentContent tr .body.itemName").text === "かえで"
        browser.find("#cartDialogCurrentContent tr .body.siteName").text === "商店1"
        browser.find("#cartDialogCurrentContent tr .body.unitPrice").text === "999円"
        browser.find("#cartDialogCurrentContent tr .body.quantity").text === "2"
        browser.find("#cartDialogCurrentContent tr .body.price").text === "1,998円"

        // Cart button
        browser.find(".ui-dialog-buttonset button").get(1).click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("shopping.cart"))
      }
    }
  }
}
