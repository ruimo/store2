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
import helpers.UrlHelper
import helpers.UrlHelper._
import controllers.EntryUserEntry
import java.util.concurrent.TimeUnit
import models._
import play.api.test.Helpers._
import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import helpers.Helper.disableMailer

class EntryUserEntrySpec extends Specification with SalesSpecBase with InjectorSupport {
  "Entry user entry" should {
    "Anonymous user can be promoted to normal user after transaction end." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(
        inMemoryDatabase() ++ defaultConf ++ disableMailer +
          ("acceptableTenders.ANONYMOUS_BUYER" -> List("PAYPAL_WEB_PAYMENT_PLUS")) +
          ("paypalWebPaymentPlus.paypalId" -> "paypal_id") +
          ("anonymousUserPurchase" -> true)
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
        val site = inject[SiteRepo].createNew(Ja, "店舗1")
        createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        logoff(browser)
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass)

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100), date("9999-12-31"))

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".purchaseButton").click()
        browser.waitUntil(
          failFalse(browser.find("#doAnonymousLoginButton").first().displayed())
        )
        browser.find("#doAnonymousLoginButton").click()
        browser.waitUntil(
          failFalse(browser.find(".toEnterShippingAddressInner").first().displayed())
        )
        browser.find(".toEnterShippingAddressInner a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#firstName").fill().`with`("firstName1")
        browser.find("#lastName").fill().`with`("lastName1")
        browser.find("#firstNameKana").fill().`with`("firstName1")
        browser.find("#lastNameKana").fill().`with`("lastName1")
        browser.find("#email").fill().`with`("null@ruimo.com")
        browser.find("input[name='zip1']").fill().`with`("111")
        browser.find("input[name='zip2']").fill().`with`("2222")
        browser.find("#prefecture option[value='13']").click()
        browser.find("#address1").fill().`with`("address1")
        browser.find("#address2").fill().`with`("address2")
        browser.find("#address3").fill().`with`("address3")
        browser.find("#tel1").fill().`with`("11111111")
        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#submitBtn").click()

        browser.waitUntil(
          failFalse(browser.find("#doPaypalWebPayment").first().displayed())
        )
        browser.find("#doPaypalWebPayment").click()

        browser.waitUntil(
          failFalse(browser.find("#paypalWebPaymentSkip").first().displayed())
        )

        val (paypalTran, tranHeader) = {
          val headers: Seq[TransactionLogHeader] = TransactionLogHeader.list()
          headers.size === 1
          headers(0).transactionType === TransactionTypeCode.PAYPAL_WEB_PAYMENT_PLUS

          val paypalTran: TransactionLogPaypalStatus = TransactionLogPaypalStatus.byTransactionId(headers(0).id.get)
          paypalTran.transactionId === headers(0).id.get
          paypalTran.status === PaypalStatus.START
          (paypalTran, headers(0))
        }

        browser.webDriver.getTitle === Messages("commonTitle", Messages("paypalWebPaymentStartTitle"))
        browser.goTo(
          controllers.routes.Paypal.onWebPaymentPlusSuccess(
            tranHeader.id.get, paypalTran.token
          ).url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          failFalse(browser.find("#anonymousRegistrationBtn").first().displayed())
        )
        browser.find("#anonymousRegistrationBtn").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#firstName").attribute("value") === "firstName1"
        browser.find("#lastName").attribute("value") === "lastName1"
        browser.find("#email").attribute("value") === "null@ruimo.com"

        browser.find("#firstName").fill().`with`("firstName2")
        browser.find("#lastName").fill().`with`("lastName2")
        browser.find("#email").fill().`with`("null2@ruimo.com")

        browser.waitUntil(
          failFalse(browser.find("#submitUserEntry").first().displayed())
        )
        browser.find("#submitUserEntry").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")

        browser.find("#userName_field.error input").size !== 0
        browser.find("#password_main_field.error input").size !== 0

        browser.find("#userName").fill().`with`("12345678")
        browser.find("#submitUserEntry").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")

        browser.find("#userName_field.error input").size === 0
        browser.find("#password_main_field.error input").size !== 0

        browser.find("#userName").fill().`with`("12345678")
        browser.find("#password_main").fill().`with`("password1234")
        browser.find("#submitUserEntry").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")

        browser.find("#userName_field.error input").size === 0
        browser.find("#password_main_field.error input").size === 0
        browser.find("#password_confirm_field.error .help-inline").text === Messages("confirmPasswordDoesNotMatch")

        browser.find("#userName").fill().`with`("11111111")
        browser.find("#password_main").fill().`with`("password1234")
        browser.find("#password_confirm").fill().`with`("password1234")
        browser.find("#submitUserEntry").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#userName_field .help-inline").text === Messages("userNameIsTaken")

        browser.find("#userName").fill().`with`("12345678")
        browser.find("#password_main").fill().`with`("password1234")
        browser.find("#password_confirm").fill().`with`("password1234")
        browser.find("#submitUserEntry").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val user = inject[StoreUserRepo].findByUserName("12345678").get
        user.email === "null2@ruimo.com"
        user.firstName === "firstName2"
        user.lastName === "lastName2"
        user.userRole === UserRole.ENTRY_USER
      }
    }
  }
}
