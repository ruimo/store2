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
import controllers.ItemPictures
import java.nio.file.Files
import java.nio.charset.Charset
import java.net.{HttpURLConnection, URL}
import java.io.{BufferedReader, InputStreamReader}
import java.text.SimpleDateFormat
import helpers.{ViewHelpers, QueryString}
import com.ruimo.scoins.Scoping._

class ItemDetailSpec extends Specification with InjectorSupport {
  "Item detail" should {
    "Can show list price with memo" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
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
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))

        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        browser.goTo(
          controllers.routes.ItemDetail.show(item.id.get.id, site.id.get).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".memo").size === 0

        // add price memo
        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(item.id.get.id).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find("#addSiteItemTextMetadataForm")) { form =>
          form.find(
            "option[value=\"" + SiteItemTextMetadataType.PRICE_MEMO.ordinal + "\"]"
          ).click()

          browser.find("#addSiteItemTextMetadataForm #metadata").fill().`with`("Price memo")

          form.find("input[type='submit']").click()
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.goTo(
          controllers.routes.ItemDetail.show(item.id.get.id, site.id.get).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".itemDetailItemPrice .value .memo").text === "Price memo"

        // add list price
        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(item.id.get.id).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#itemPrices_0_listPrice").fill().`with`("3000")
        browser.find("#changeItemPriceButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("changeItemTitle"))

        // add list price memo
        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(item.id.get.id).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find("#addSiteItemTextMetadataForm")) { form =>
          form.find(
            "option[value=\"" + SiteItemTextMetadataType.LIST_PRICE_MEMO.ordinal + "\"]"
          ).click()

          browser.find("#addSiteItemTextMetadataForm #metadata").fill().`with`("List price memo")

          form.find("input[type='submit']").click()
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.goTo(
          controllers.routes.ItemDetail.show(item.id.get.id, site.id.get).url + "&lang=" + lang.code
        )
        browser.waitUntil(
          failFalse(browser.find(".itemDetailListPrice .value .memo").first().displayed())
        )

        browser.find(".itemDetailListPrice .value .memo").text === "List price memo"
      }
    }

    "Can show not found error." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
    ){
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))

        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        browser.goTo(
          controllers.routes.ItemDetail.show(item.id.get.id + 1, site.id.get).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".itemDetailNotFound").text === Messages("itemDetailNotFound")
      }
    }

    "Can show not found error for hidden item." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
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
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))

        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.HIDE, 1
        )

        browser.goTo(
          controllers.routes.ItemDetail.show(item.id.get.id, site.id.get).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".itemDetailNotFound").text === Messages("itemDetailNotFound")
      }
    }
  }
}
