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
import views.Titles
import helpers.UrlHelper
import helpers.UrlHelper._
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection
import models._
import helpers.ViewHelpers

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.test.TestServer
import java.util.concurrent.TimeUnit
import helpers.Helper._
import play.api.i18n.{Lang, Messages}
import com.ruimo.scoins.Scoping._

class PaypalSpec extends Specification with SalesSpecBase with InjectorSupport {
  lazy val baseUrl = "http://localhost:" + play.api.test.Helpers.testServerPort

  "Paypal" should {
    "Normal paypal transaction." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(
        inMemoryDatabase() ++ defaultConf ++ disableMailer +
          ("anonymousUserPurchase" -> true) +
          ("fakePaypalRespons.enabled" -> true) +
          ("acceptableTenders.ANONYMOUS_BUYER" -> List("PAYPAL")) +
          ("fakePaypalRespons.body" -> "TOKEN=PAYPALTOKEN&CORRELATIONID=AAA&ACK=Success&VERSION=124.0&BUILD=18316154") +
          ("fakePaypalRespons.statusCode" -> "200") +
          (
            "paypal.redirectUrl" -> (
              baseUrl + controllers.routes.Paypal.fakePaypal("", "").url.takeWhile(_ != '?')
            )
          )
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
        val tax2 = inject[TaxRepo].createNew
        val taxName2 = inject[TaxNameRepo].createNew(tax2, Ja, "内税")
        val taxHis2 = inject[TaxHistoryRepo].createNew(tax2, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        browser.goTo(itemQueryUrl())
        browser.waitUntil(
          failFalse(browser.find(".addToCartButton").first().displayed())
        )
        browser.find(".addToCartButton").click()
        browser.waitUntil(
          failFalse(browser.find("#doAnonymousLoginButton").first().displayed())
        )

        browser.find("#doAnonymousLoginButton").size === 1
        browser.find("#doAnonymousLoginButton").click()

        browser.find(".toEnterShippingAddress a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(
          fee.id.get, tax2.id.get, BigDecimal(123), Some(100), date("9999-12-31")
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("#email").fill().`with`("foo@bar.com")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.waitUntil(30, TimeUnit.SECONDS) {
          failFalse {
            browser.find("#address1").attribute("value") == "大田区" &&
            browser.find("#address2").attribute("value") == "池上"
          }
        }
        browser.find("#tel1").fill().`with`("11111111")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#paypalimg").size === 1
        browser.find("#paypalimg").click()

        browser.find("#cmd").text === "_express-checkout"
        browser.find("#token").text === "PAYPALTOKEN"

        val headers: Seq[TransactionLogHeader] = TransactionLogHeader.list()
        headers.size === 1
        headers(0).transactionType === TransactionTypeCode.PAYPAL_EXPRESS_CHECKOUT

        val paypalTran: TransactionLogPaypalStatus = TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)
        paypalTran.transactionId === headers(0).id.get
        paypalTran.status === PaypalStatus.PREPARED

        browser.goTo(
          controllers.routes.Paypal.onSuccess(
            paypalTran.transactionId + 1, paypalTran.token
          ).url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Titles.top).trim
        doWith(TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)) { paypal =>
          paypal.status === PaypalStatus.PREPARED
        }

        browser.goTo(
          controllers.routes.Paypal.onSuccess(
            paypalTran.transactionId, paypalTran.token + 1
          ).url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Titles.top).trim
        doWith(TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)) { paypal =>
          paypal.status === PaypalStatus.PREPARED
        }

        browser.goTo(
          controllers.routes.Paypal.onSuccess(
            paypalTran.transactionId, paypalTran.token
          ).url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("end.transaction"))

        browser.find(".itemTableBody").index(0).find(".itemNameBody").text === "かえで"
        browser.find(".itemTableBody").index(0).find(".siteName").text === "Store01"
        browser.find(".itemTableBody").index(0).find(".quantity").text === "1"
        browser.find(".itemTableBody").index(0).find(".itemPrice").text === "999円"
        browser.find(".itemTableBody").index(1).find(".subtotal").text === "999円"

        browser.find(".itemTableBody").index(2).find(".outerTaxAmount").text ===
        ViewHelpers.toAmount(BigDecimal((999 * 0.05).asInstanceOf[Int]))
        browser.find(".itemTableBody").index(3).find(".grandTotal").text ===
        ViewHelpers.toAmount(BigDecimal((999 * 1.05).asInstanceOf[Int]))

        browser.find(".salesTotalBody").index(0).find(".itemQuantity").text === "1"
        browser.find(".salesTotalBody").index(0).find(".itemPrice").text === "1,048円"
        browser.find(".salesTotalBody").index(1).find(".itemQuantity").text === "1 箱"
        browser.find(".salesTotalBody").index(1).find(".itemPrice").text === "123円"
        browser.find(".salesTotalBody").index(2).find(".itemPrice").text === "1,171円"

        browser.find(".shippingAddress").find(".name").text === "firstName01 lastName01"
        browser.find(".shippingAddress").find(".nameKana").text === "firstNameKana01 lastNameKana01"
        browser.find(".shippingAddress").find(".zip").text === "146 - 0082"
        browser.find(".shippingAddress").find(".prefecture").text === "東京都"
        browser.find(".shippingAddress").find(".address1").text === "大田区"
        browser.find(".shippingAddress").find(".address2").text === "池上"
        browser.find(".shippingAddress").find(".tel1").text === "11111111"

        doWith(TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)) { paypal =>
          paypal.transactionId === headers(0).id.get
          paypal.status === PaypalStatus.COMPLETED
        }
      }
    }

    "Paypal transaction error." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(
        inMemoryDatabase() ++ defaultConf ++ disableMailer +
          ("anonymousUserPurchase" -> true) +
          ("fakePaypalRespons.enabled" -> true) +
          ("acceptableTenders.ANONYMOUS_BUYER" -> List("PAYPAL")) +
          ("fakePaypalRespons.body" -> "ACK=Fail&VERSION=124.0&BUILD=18316154") +
          ("fakePaypalRespons.statusCode" -> "200") +
          (
            "paypal.redirectUrl" -> (
              baseUrl + controllers.routes.Paypal.fakePaypal("", "").url.takeWhile(_ != '?')
            )
          )
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
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        browser.goTo(itemQueryUrl())
        browser.waitUntil(
          failFalse(browser.find(".addToCartButton").first().displayed())
        )
        browser.find(".addToCartButton").click()
        browser.waitUntil(
          failFalse(browser.find("#doAnonymousLoginButton").first().displayed())
        )

        browser.find("#doAnonymousLoginButton").size === 1
        browser.find("#doAnonymousLoginButton").click()

        browser.find(".toEnterShippingAddress a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100), date("9999-12-31"))

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("#email").fill().`with`("foo@bar.com")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.waitUntil(30, TimeUnit.SECONDS) {
          failFalse {
            browser.find("#address1").attribute("value") == "大田区" &&
            browser.find("#address2").attribute("value") == "池上"
          }
        }
        browser.find("#tel1").fill().`with`("11111111")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#paypalimg").size === 1
        browser.find("#paypalimg").click()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("paypalErrorTitle"))

        val headers: Seq[TransactionLogHeader] = TransactionLogHeader.list()
        headers.size === 1
        headers(0).transactionType === TransactionTypeCode.PAYPAL_EXPRESS_CHECKOUT

        val paypalTran: TransactionLogPaypalStatus = TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)
        paypalTran.transactionId === headers(0).id.get
        paypalTran.status === PaypalStatus.ERROR

        // on success should be rejected.
        browser.goTo(
          controllers.routes.Paypal.onSuccess(
            paypalTran.transactionId, paypalTran.token
          ).url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Titles.top).trim
        doWith(TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)) { paypal =>
          paypal.status === PaypalStatus.ERROR
        }
      }
    }

    "Paypal cancel transaction." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(
        inMemoryDatabase() ++ defaultConf ++ disableMailer +
          ("anonymousUserPurchase" -> true) +
          ("fakePaypalRespons.enabled" -> true) +
          ("fakePaypalRespons.body" -> "TOKEN=PAYPALTOKEN&CORRELATIONID=AAA&ACK=Success&VERSION=124.0&BUILD=18316154") +
          ("acceptableTenders.ANONYMOUS_BUYER" -> List("PAYPAL")) +
          ("fakePaypalRespons.statusCode" -> "200") +
          (
            "paypal.redirectUrl" -> (
              baseUrl + controllers.routes.Paypal.fakePaypal("", "").url.takeWhile(_ != '?')
            )
          )
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
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        browser.goTo(itemQueryUrl())
        browser.waitUntil(
          failFalse(browser.find(".addToCartButton").first().displayed())
        )
        browser.find(".addToCartButton").click()
        browser.waitUntil(
          failFalse(browser.find("#doAnonymousLoginButton").first().displayed())
        )

        browser.find("#doAnonymousLoginButton").size === 1
        browser.find("#doAnonymousLoginButton").click()

        browser.find(".toEnterShippingAddress a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100), date("9999-12-31"))

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("#email").fill().`with`("foo@bar.com")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.waitUntil(30, TimeUnit.SECONDS) {
          failFalse {
            browser.find("#address1").attribute("value") == "大田区" &&
            browser.find("#address2").attribute("value") == "池上"
          }
        }
        browser.find("#tel1").fill().`with`("11111111")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#paypalimg").size === 1
        browser.find("#paypalimg").click()

        browser.find("#cmd").text === "_express-checkout"
        browser.find("#token").text === "PAYPALTOKEN"

        val headers: Seq[TransactionLogHeader] = TransactionLogHeader.list()
        headers.size === 1
        headers(0).transactionType === TransactionTypeCode.PAYPAL_EXPRESS_CHECKOUT

        val paypalTran: TransactionLogPaypalStatus = TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)
        paypalTran.transactionId === headers(0).id.get
        paypalTran.status === PaypalStatus.PREPARED

        browser.goTo(
          controllers.routes.Paypal.onCancel(
            paypalTran.transactionId, paypalTran.token
          ).url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("cancelPayaplTitle"))
        doWith(TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)) { paypal =>
          paypal.status === PaypalStatus.CANCELED
        }
      }
    }
  }
}


