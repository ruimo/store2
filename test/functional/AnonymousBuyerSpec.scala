package functional

import java.time.ZoneId
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import anorm._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import java.sql.Connection
import com.ruimo.scoins.Scoping._
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.format.DateTimeFormatter
import play.api.test.{WebDriverFactory, WithBrowser}
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class AnonymousBuyerSpec extends Specification with SalesSpecBase with InjectorSupport {
  "Anonymous buyer" should {
    "If anonymousUserPurchase is false, purchase by anonymous should not be shown." in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase() ++ defaultConf + ("anonymousUserPurchase" -> false))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val adminUser = loginWithTestUser(browser)
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        logoff(browser)

        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()
        browser.find(".addToCartButton").click()

        browser.find("#doAnonymousLoginButton").size === 0
      }
    }

    "If anonymousUserPurchase is true, purchase by anonymous is permitted." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(
        inMemoryDatabase() ++ defaultConf +
          ("anonymousUserPurchase" -> true) +
          ("acceptableTenders.ANONYMOUS_BUYER" -> List("PAYPAL"))
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val adminUser = loginWithTestUser(browser)
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
        logoff(browser)

        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(
          fee.id.get, tax.id.get, BigDecimal(123), Some(100), date("9999-12-31")
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()

        browser.find(".addToCartButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#doAnonymousLoginButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shopping.cart"))
        browser.find(".shoppingCartTable tr").index(1).find("td").index(0).text === "かえで"

        // Anonymou user should be registered.
        val users: Seq[StoreUser] = inject[StoreUserRepo].all
        users.size === 2
        doWith(users.filter(_.userName != "administrator").head) { u =>
          u.userName.startsWith("anon") === true
          u.firstName === Messages("guest")
          u.middleName === None
          u.lastName === ""
          u.email === ""
          u.userRole === UserRole.ANONYMOUS
          u.companyName === None
        }

        // Order history link should not be shown.
        browser.find(".orderHistoryLink").size === 0

        browser.find(".toEnterShippingAddressInner a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#loginWelcomeMessage").text() === WelcomeMessage.welcomeMessage

        browser.webDriver.getTitle === Messages("commonTitle", Messages("enter.shipping.address"))
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#firstNameKana").fill().`with`("firstnamekana")
        browser.find("#lastNameKana").fill().`with`("lastnamekana")
        browser.find("#email").fill().`with`("null@aaa.com")
        browser.find("input[name='zip1']").fill().`with`("123")
        browser.find("input[name='zip2']").fill().`with`("2345")
        browser.find("#prefecture option[value='13']").click()
        browser.find("#address1").fill().`with`("address01")
        browser.find("#address2").fill().`with`("address02")
        browser.find("#address3").fill().`with`("address03")
        browser.find("#tel1").fill().`with`("12345678")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#submitBtn").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("button.payByAccountingBill").size === 0
        browser.find("#paypalimg").size === 1
      }
    }
  }
}

