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
import helpers.Retry._
import java.util.concurrent.TimeUnit
import helpers.Helper.disableMailer
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.joda.time.format.DateTimeFormat
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Date.{valueOf => date}
import LocaleInfo._
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class ShowCouponSpec extends Specification with InjectorSupport {
  implicit def date2milli(d: java.sql.Date) = d.getTime
  val conf = inMemoryDatabase() ++ disableMailer

  "ShowCoupon" should {
    "Can show coupon." in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
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
        val site = inject[SiteRepo].createNew(Ja, "商店111")
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
        inject[SiteItemNumericMetadataRepo].createNew(site.id.get, item.id.get, SiteItemNumericMetadataType.INSTANT_COUPON, 1)

        browser.goTo(
          controllers.routes.CouponHistory.showInstantCoupon(site.id.get, item.id.get.id).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("coupon.title"))
        browser.find("td.siteName").text.indexOf(user.companyName.get) !== -1
        browser.find("td.name").text.indexOf(user.fullName) !== -1
        browser.find("td.tranId").size === 0
      }
    }

    "If thw item is not flaged as instant coupon, page should redirect to top." in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
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
        val site = inject[SiteRepo].createNew(Ja, "商店111")
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
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.goTo(
          controllers.routes.CouponHistory.showInstantCoupon(site.id.get, item.id.get.id).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("company.name"))
      }
    }
  }
}
