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
import org.openqa.selenium.By
import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import play.api.i18n.{Lang, Messages}
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import java.sql.Connection
import models._
import helpers.Helper._
import com.ruimo.scoins.Scoping._

class RecommendationByAdminSpec extends Specification with InjectorSupport {
  "recommendation by admin maintenance" should {
    "Can create record" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        val tax = inject[TaxRepo].createNew
        val sites = Vector(inject[SiteRepo].createNew(Ja, "商店1"), inject[SiteRepo].createNew(Ja, "商店2"))
        val cat1 = inject[CategoryRepo].createNew(
          Map(Ja -> "植木", En -> "Plant")
        )
        val items = Vector(
          inject[ItemRepo].createNew(cat1), inject[ItemRepo].createNew(cat1), inject[ItemRepo].createNew(cat1)
        )
        inject[SiteItemRepo].createNew(sites(0), items(0))
        inject[SiteItemRepo].createNew(sites(1), items(1))
        inject[SiteItemRepo].createNew(sites(0), items(2))

        inject[SiteItemNumericMetadataRepo].createNew(sites(0).id.get, items(2).id.get, SiteItemNumericMetadataType.HIDE, 1)
        val itemNames = Vector(
          inject[ItemNameRepo].createNew(items(0), Map(Ja -> "植木1")),
          inject[ItemNameRepo].createNew(items(1), Map(Ja -> "植木2")),
          inject[ItemNameRepo].createNew(items(2), Map(Ja -> "植木3"))
        )
        val itemPrice1 = inject[ItemPriceRepo].createNew(items(0), sites(0))
        val itemPrice2 = inject[ItemPriceRepo].createNew(items(1), sites(1))
        val itemPrice3 = inject[ItemPriceRepo].createNew(items(2), sites(0))
        val itemPriceHistories = Vector(
          inject[ItemPriceHistoryRepo].createNew(
            itemPrice1, tax, currencyInfo.Jpy, BigDecimal("100"), None, BigDecimal("90"), date("9999-12-31")
          ),
          inject[ItemPriceHistoryRepo].createNew(
            itemPrice2, tax, currencyInfo.Jpy, BigDecimal("200"), None, BigDecimal("190"), date("9999-12-31")
          ),
          inject[ItemPriceHistoryRepo].createNew(
            itemPrice3, tax, currencyInfo.Jpy, BigDecimal("300"), None, BigDecimal("290"), date("9999-12-31")
          )
        )

        browser.goTo(
          controllers.routes.RecommendationByAdmin.selectItem(List()) + "?lang=" + lang.code
        )

        // Hidden item should be shown in maintenance functions.
        browser.find(".itemTableBody").size === 3

        // Sorted by item name.
        browser.find(".itemTableItemId").index(0).text === items(0).id.get.toString
        browser.find(".itemTableItemId").index(1).text === items(1).id.get.toString
        browser.find(".itemTableItemId").index(2).text === items(2).id.get.toString

        browser.find(".itemName").index(0).text === "植木1"
        browser.find(".itemName").index(1).text === "植木2"
        browser.find(".itemName").index(2).text === "植木3"

        browser.find(".itemTableSiteName").index(0).text === "商店1"
        browser.find(".itemTableSiteName").index(1).text === "商店2"
        browser.find(".itemTableSiteName").index(2).text === "商店1"

        browser.find(".itemTablePrice").index(0).text === "100円"
        browser.find(".itemTablePrice").index(1).text === "200円"
        browser.find(".itemTablePrice").index(2).text === "300円"

        inject[RecommendByAdminRepo].listByScore(showDisabled = true, locale = Ja).records.size === 0

        browser.find(".addRecommendationByAdminForm input[type='submit']").index(0).click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".addRecommendationByAdminForm input[type='submit']").index(1).click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".addRecommendationByAdminForm input[type='submit']").index(2).click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        var recoId1: Long = 0
        var recoId2: Long = 0
        // Hidden item should not be listed.
        doWith(inject[RecommendByAdminRepo].listByScore(showDisabled = true, locale = Ja).records) { rec =>
          rec.size === 2
          rec(0)._1.siteId === sites(0).id.get
          rec(0)._1.itemId === items(0).id.get.id
          rec(0)._1.score === 1
          rec(0)._1.enabled === true

          rec(0)._2 === Some(itemNames(0)(Ja))
          rec(0)._3 === Some(sites(0))

          rec(1)._1.siteId === sites(1).id.get
          rec(1)._1.itemId === items(1).id.get.id
          rec(1)._1.score === 1
          rec(1)._1.enabled === true

          rec(1)._2 === Some(itemNames(1)(Ja))
          rec(1)._3 === Some(sites(1))
        }

        browser.goTo(
          controllers.routes.RecommendationByAdmin.startUpdate() + "?lang=" + lang.code
        )

        browser.find(".recommendationByAdminTable.body").size === 2
        recoId1 = browser.find(".idInput").index(0).attribute("value").toLong
        recoId2 = browser.find(".idInput").index(1).attribute("value").toLong

        // Check validation
        browser.find(".scoreInput").index(0).fill().`with`("")
        browser.find(".updateButton").index(0).click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".recommendationByAdminTable.body")
          .find(".error").text === Messages("error.number")

        browser.find(".scoreInput").index(0).fill().`with`("ABC")
        browser.find(".updateButton").index(0).click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".recommendationByAdminTable.body")
          .find(".error").text === Messages("error.number")

        browser.find(".scoreInput").index(0).fill().`with`("-1")
        browser.find(".updateButton").index(0).click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".recommendationByAdminTable.body")
          .find(".error").text === Messages("error.min", 0)

        browser.find(".scoreInput").index(0).fill().`with`("123")
        browser.find(".recommendationByAdminTable.body").index(0).find("input[type='checkbox']").click()
        browser.find(".updateButton").index(0).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(inject[RecommendByAdminRepo].apply(recoId1)) { rec =>
          rec.score === 123
          rec.enabled === false
        }

        browser.find(".scoreInput").index(0).attribute("value") === "123"
        browser.webDriver.findElements(
          By.cssSelector(".recommendationByAdminTable.body input[type='checkbox']")
        ).get(0).isSelected === false

        browser.find(".removeRecommendationByAdminForm").index(0).find("input[type='submit']").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".removeRecommendationByAdminForm").size === 1
        browser.find(".idInput").attribute("value").toLong === recoId2

        doWith(inject[RecommendByAdminRepo].listByScore(showDisabled = true, locale = Ja).records) { rec =>
          rec.size === 1

          rec(0)._1.siteId === sites(1).id.get
          rec(0)._1.itemId === items(1).id.get.id
          rec(0)._1.score === 1
          rec(0)._1.enabled === true

          rec(0)._2 === Some(itemNames(1)(Ja))
          rec(0)._3 === Some(sites(1))
        }
      }
    }
  }
}
