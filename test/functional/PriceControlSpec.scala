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

import helpers.Helper.disableMailer
import helpers.UrlHelper
import helpers.UrlHelper._
import org.openqa.selenium.By
import org.openqa.selenium.By._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.util.concurrent.TimeUnit
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class PriceControlSpec extends Specification with SalesSpecBase with InjectorSupport {
  "Item Price" should {
    "List price should be used for guest and anonymous users." in new WithBrowser(
      WebDriverFactory(CHROME), appl(
        inMemoryDatabase(options = Map("MVCC" -> "true")) +
          ("itemPriceStrategy.ANONYMOUS_BUYER.type" -> "models.ListPriceStrategy") +
          ("itemPriceStrategy.GUEST.type" -> "models.ListPriceStrategy") +
          ("anonymousUserPurchase" -> "true") ++
          disableMailer
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        logoff(browser)

        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100), date("9999-12-31"))

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // List price should be used.
        browser.find(".queryItemUnitPrice").text === "999円"
        browser.find(".addToCartButton.purchaseButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el("#doLoginButton")).displayed()

        // We are not logged in yet.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("login"))
        browser.find("#doAnonymousLoginButton").click()

        browser.find(".shoppingCartTable .tableBody .unitPrice").text === "999円"
        browser.find(".shoppingCartTable .tableBody .price").text === "999円"

        browser.find(".toEnterShippingAddressInner a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("#email").fill().`with`("foo@bar.zzz")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address01")
        browser.find("#address2").fill().`with`("address02")
        browser.find("#tel1").fill().`with`("11111111")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".itemTable .itemTableBody .itemPrice").text === "999円"
        browser.find(".itemTable .itemTableBody.subtotalWithoutTax .subtotal").text === "999円"
        browser.find(".itemTable .itemTableBody.outerTax .outerTaxAmount").text === "49円"
        browser.find(".itemTable .itemTableBody.total .grandTotal").text === "1,048円"
        browser.find(".shipping .shippingTableBody .boxUnitPrice").text === "123円"
        browser.find(".shipping .shippingTableBody .boxPrice").text === "123円"
        browser.find(".salesTotal .salesTotalBody").index(0).find(".itemPrice").text === "1,048円"
        browser.find(".salesTotal .salesTotalBody").index(1).find(".itemPrice").text === "123円"
        browser.find(".salesTotal .salesTotalBody").index(2).find(".itemPrice").text === "1,171円"
      }
    }
  }
}
