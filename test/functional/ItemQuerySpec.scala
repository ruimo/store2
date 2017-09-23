package functional

import play.api.Configuration
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import org.openqa.selenium.By;
import java.util.concurrent.TimeUnit
import helpers.UrlHelper
import helpers.UrlHelper._
import anorm._
import helpers.UrlHelper
import helpers.UrlHelper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Messages, Lang}
import play.api.test.Helpers._
import helpers.Helper._
import models._
import play.api.test.TestServer
import scala.Some
import controllers.NeedLogin
import com.ruimo.scoins.Scoping._

class ItemQuerySpec extends Specification with InjectorSupport {
  "Query" should {
    "Item empty" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
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
      
        browser.goTo(
          controllers.routes.ItemQuery.query(List(""), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        browser.find(".itemNotFound").text === Messages("itemNotFound")
      }
    }

    "Query with single item" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        
        val site = inject[SiteRepo].createNew(Ja, "商店1")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        
        val needAuthenticationEntirely = inject[Configuration].getOptional[Boolean]("need.authentication.entirely").getOrElse(false)

        if (needAuthenticationEntirely) {
          loginWithTestUser(browser)
        }

        browser.goTo(
          controllers.routes.ItemQuery.query(List(""), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "かえで"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(""), Some(cat.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "かえで"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(""), Some(cat.id.get + 1), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        browser.$("tr.queryItemTableBody").size === 0

        // Search by item name
        browser.goTo(
          controllers.routes.ItemQuery.query(List("かえで"), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "かえで"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        // Search by item description
        browser.goTo(
          controllers.routes.ItemQuery.query(List("かえで説明"), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "かえで"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.query(List("もみじ"), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        browser.$("tr.queryItemTableBody").size === 0

        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List("もみじ"), Some(cat.id.get), 0, 10).url
          + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        browser.$("tr.queryItemTableBody").size === 0
      }
    }

    "Query with two conditions" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        
        val site = inject[SiteRepo].createNew(Ja, "商店1")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "松"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "松 常緑")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        
        // Search by two conditions name
        browser.goTo(
          controllers.routes.ItemQuery.query(List("松", "常緑"), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        val body2 = browser.$("tr.queryItemTableBody")
        body2.size() === 1
        body2.find("td.queryItemItemName").find("a").text === "松"
        body2.find("td.queryItemSite").text === "商店1"
        body2.find("td.queryItemUnitPrice").text === "999円"

        // Search by conditions where that include no match
        browser.goTo(
          controllers.routes.ItemQuery.query(List("松", "常緑", "落葉"), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        val body4 = browser.$("tr.queryItemTableBody")
        body4.size() === 0
      }
    }

    "Query with category" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        
        val site = inject[SiteRepo].createNew(Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val cat2 = inject[CategoryRepo].createNew(Map(Ja -> "植木2"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat2)
        inject[SiteItemRepo].createNew(site, item1)
        inject[SiteItemRepo].createNew(site, item2)
        inject[ItemNameRepo].createNew(item1, Map(Ja -> "松"))
        inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))
        inject[ItemDescriptionRepo].createNew(item1, site, "松 常緑")
        inject[ItemDescriptionRepo].createNew(item2, site, "梅 常緑")
        val itemPrice1 = inject[ItemPriceRepo].createNew(item1, site)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice1, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val itemPrice2 = inject[ItemPriceRepo].createNew(item2, site)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice2, tax, currencyInfo.Jpy, BigDecimal(333), None, BigDecimal("222"), date("9999-12-31")
        )
        
        // Search by category
        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(), Some(cat1.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "松"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(), Some(cat2.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "梅"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "333円"
        }
      }
    }

    "Query with site" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item1 = inject[ItemRepo].createNew(cat)
        val item2 = inject[ItemRepo].createNew(cat)
        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)
        inject[ItemNameRepo].createNew(item1, Map(Ja -> "松"))
        inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))
        inject[ItemDescriptionRepo].createNew(item1, site1, "松 常緑")
        inject[ItemDescriptionRepo].createNew(item2, site2, "梅 常緑")
        val itemPrice1 = inject[ItemPriceRepo].createNew(item1, site1)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice1, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val itemPrice2 = inject[ItemPriceRepo].createNew(item2, site2)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice2, tax, currencyInfo.Jpy, BigDecimal(333), None, BigDecimal("222"), date("9999-12-31")
        )
        
        // Search by site
        browser.goTo(
          controllers.routes.ItemQuery.queryBySite(List(), Some(site1.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "松"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryBySite(List(), Some(site2.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "梅"
          body.find("td.queryItemSite").text === "商店2"
          body.find("td.queryItemUnitPrice").text === "333円"
        }
      }
    }

    "Query with site and category" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val cat2 = inject[CategoryRepo].createNew(Map(Ja -> "植木2"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)
        val item3 = inject[ItemRepo].createNew(cat2)
        val item4 = inject[ItemRepo].createNew(cat2)
        val item5 = inject[ItemRepo].createNew(cat2)
        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)
        inject[SiteItemRepo].createNew(site1, item3)
        inject[SiteItemRepo].createNew(site2, item4)
        inject[SiteItemRepo].createNew(site2, item5)
        inject[ItemNameRepo].createNew(item1, Map(Ja -> "松"))
        inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))
        inject[ItemNameRepo].createNew(item3, Map(Ja -> "桜"))
        inject[ItemNameRepo].createNew(item4, Map(Ja -> "あやめ"))
        inject[ItemNameRepo].createNew(item5, Map(Ja -> "藤"))
        inject[ItemDescriptionRepo].createNew(item1, site1, "松 常緑")
        inject[ItemDescriptionRepo].createNew(item2, site2, "梅 常緑")
        inject[ItemDescriptionRepo].createNew(item3, site1, "桜 常緑")
        inject[ItemDescriptionRepo].createNew(item4, site2, "あやめ 常緑")
        inject[ItemDescriptionRepo].createNew(item5, site2, "藤 常緑")
        val itemPrice1 = inject[ItemPriceRepo].createNew(item1, site1)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice1, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val itemPrice2 = inject[ItemPriceRepo].createNew(item2, site2)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice2, tax, currencyInfo.Jpy, BigDecimal(333), None, BigDecimal("222"), date("9999-12-31")
        )
        val itemPrice3 = inject[ItemPriceRepo].createNew(item3, site1)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice3, tax, currencyInfo.Jpy, BigDecimal(222), None, BigDecimal("444"), date("9999-12-31")
        )
        val itemPrice4 = inject[ItemPriceRepo].createNew(item4, site2)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice4, tax, currencyInfo.Jpy, BigDecimal(111), None, BigDecimal("999"), date("9999-12-31")
        )
        val itemPrice5 = inject[ItemPriceRepo].createNew(item5, site2)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice5, tax, currencyInfo.Jpy, BigDecimal(123), None, BigDecimal("987"), date("9999-12-31")
        )
        
        // Search by site1 and cat1
        browser.goTo(
          controllers.routes.ItemQuery.queryBySiteAndCategory(
            List(), Some(site1.id.get), Some(cat1.id.get), 0, 10
          ).url.addParm("lang", lang.code).toString
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "松"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        // Search by site1 and cat2
        browser.goTo(
          controllers.routes.ItemQuery.queryBySiteAndCategory(
            List(), Some(site1.id.get), Some(cat2.id.get), 0, 10
          ).url.addParm("lang", lang.code).toString
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "桜"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "222円"
        }

        // Search by site2 and cat1
        browser.goTo(
          controllers.routes.ItemQuery.queryBySiteAndCategory(
            List(), Some(site2.id.get), Some(cat1.id.get), 0, 10
          ).url.addParm("lang", lang.code).toString
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "梅"
          body.find("td.queryItemSite").text === "商店2"
          body.find("td.queryItemUnitPrice").text === "333円"
        }

        // Search by site2 and cat2
        browser.goTo(
          controllers.routes.ItemQuery.queryBySiteAndCategory(
            List(), Some(site2.id.get), Some(cat2.id.get), 0, 10
          ).url.addParm("lang", lang.code).toString
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 2
          body.find("td.queryItemItemName").index(0).find("a").text === "あやめ"
          body.find("td.queryItemSite").index(0).text === "商店2"
          body.find("td.queryItemUnitPrice").index(0).text === "111円"
          body.find("td.queryItemItemName").index(1).find("a").text === "藤"
          body.find("td.queryItemSite").index(1).text === "商店2"
          body.find("td.queryItemUnitPrice").index(1).text === "123円"
        }
      }
    }

    "Query with supplemental category" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        
        val site = inject[SiteRepo].createNew(Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val cat2 = inject[CategoryRepo].createNew(Map(Ja -> "植木2"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        inject[SiteItemRepo].createNew(site, item1)
        inject[ItemNameRepo].createNew(item1, Map(Ja -> "松"))
        inject[ItemDescriptionRepo].createNew(item1, site, "松 常緑")
        val itemPrice1 = inject[ItemPriceRepo].createNew(item1, site)
        inject[ItemPriceHistoryRepo].createNew(
          itemPrice1, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        
        // Search by category
        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(), Some(cat1.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "松"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(), Some(cat2.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        browser.find("tr.queryItemTableBody").size === 0

        inject[SupplementalCategoryRepo].createNew(item1.id.get, cat2.id.get)

        // Search by category
        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(), Some(cat1.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "松"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryByCategory(List(), Some(cat2.id.get), 0, 10).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.$("tr.queryItemTableBody")) { body =>
          body.size() === 1
          body.find("td.queryItemItemName").find("a").text === "松"
          body.find("td.queryItemSite").text === "商店1"
          body.find("td.queryItemUnitPrice").text === "999円"
        }
      }
    }

    "Order by drop down." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
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

        val site = inject[SiteRepo].createNew(Ja, "商店1")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item01 = inject[ItemRepo].createNew(cat)
        val item02 = inject[ItemRepo].createNew(cat)
        val siteItem01 = inject[SiteItemRepo].createNew(site, item01)
        val siteItem02 = inject[SiteItemRepo].createNew(site, item02)
        val itemName01 = inject[ItemNameRepo].createNew(item01, Map(Ja -> "item 01"))
        val itemName02 = inject[ItemNameRepo].createNew(item02, Map(Ja -> "item 02"))
        val itemDesc01 = inject[ItemDescriptionRepo].createNew(item01, site, "desc01")
        val itemDesc02 = inject[ItemDescriptionRepo].createNew(item02, site, "desc02")
        val itemPrice01 = inject[ItemPriceRepo].createNew(item01, site)
        val itemPrice02 = inject[ItemPriceRepo].createNew(item02, site)
        val itemPriceHistory01 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice01, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val itemPriceHistory02 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice02, tax, currencyInfo.Jpy, BigDecimal(222), None, BigDecimal("111"), date("9999-12-31")
        )
        SQL(
          "update site_item set created = {date} where item_id = {id}"
        ).on(
          'date -> new java.sql.Timestamp(siteItem02.created.toEpochMilli + 1000),
          'id -> item02.id.get.id
        ).executeUpdate()

        browser.goTo(
          controllers.routes.ItemQuery.query(
            q = List(), page = 0, pageSize = 10, templateNo = 0
          ).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#sortDropDown option[value='older']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.findElement(By.id("sortDropDown"))
          .findElement(By.cssSelector("option[value=\"older\"]")).isSelected === true

        browser.find(".queryItemItemName a").index(0).text === "item 01"
        browser.find(".queryItemItemName a").index(1).text === "item 02"

        browser.find("#sortDropDown option[value='newer']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.findElement(By.id("sortDropDown"))
          .findElement(By.cssSelector("option[value=\"newer\"]")).isSelected === true

        browser.find(".queryItemItemName a").index(0).text === "item 02"
        browser.find(".queryItemItemName a").index(1).text === "item 01"

        browser.find("#sortDropDown option[value='name']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.findElement(By.id("sortDropDown"))
          .findElement(By.cssSelector("option[value=\"name\"]")).isSelected === true

        browser.find(".queryItemItemName a").index(0).text === "item 01"
        browser.find(".queryItemItemName a").index(1).text === "item 02"

        browser.find("#sortDropDown option[value='nameReverse']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.findElement(By.id("sortDropDown"))
          .findElement(By.cssSelector("option[value=\"nameReverse\"]")).isSelected === true

        browser.find(".queryItemItemName a").index(0).text === "item 02"
        browser.find(".queryItemItemName a").index(1).text === "item 01"

        browser.find("#sortDropDown option[value='price']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.findElement(By.id("sortDropDown"))
          .findElement(By.cssSelector("option[value=\"price\"]")).isSelected === true

        browser.find(".queryItemItemName a").index(0).text === "item 02"
        browser.find(".queryItemItemName a").index(1).text === "item 01"

        browser.find("#sortDropDown option[value='priceReverse']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.findElement(By.id("sortDropDown"))
          .findElement(By.cssSelector("option[value=\"priceReverse\"]")).isSelected === true

        browser.find(".queryItemItemName a").index(0).text === "item 01"
        browser.find(".queryItemItemName a").index(1).text === "item 02"
      }
    }
  }
}
