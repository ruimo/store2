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
import play.api.test.TestBrowser
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.By
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import com.google.common.base.{Function => Func}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.By
import java.util.concurrent.TimeUnit
import org.specs2.mutable.Specification
import helpers.UrlHelper
import helpers.UrlHelper._
import play.api.test.Helpers._
import models._
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import helpers.Helper._
import org.openqa.selenium.WebDriverException

class ItemQueryAdvancedSpec extends Specification with InjectorSupport {
  def click(browser: TestBrowser, ele: WebElement) {
    def click(count: Int) {
      try {
        (new Actions(browser.webDriver)).moveToElement(ele).click().perform()
      }
      catch {
        case e: WebDriverException =>
          if (count > 0) {
            Thread.sleep(1000)
            click(count - 1)
          }
          else throw e
        case e: Throwable => throw e
      }
    }

    click(5)
  }

  def itemNameMatcher(idx: Int, expectedItemName: String): ExpectedCondition[Boolean] = new ExpectedCondition[Boolean] {
    def apply(d: WebDriver): Boolean = try {
      d.findElements(By.cssSelector("#queryBody .qthumItem_name"))
        .get(idx).findElement(By.cssSelector("a")).getText.trim().equals(expectedItemName)
    }
    catch {
      case e: StaleElementReferenceException => false
      case e: IndexOutOfBoundsException => false
    }
  }

  "Item query advanced" should {
    "All items should be orderd." in new WithBrowser(
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

        val adminUser = loginWithTestUser(browser)

        val site = inject[SiteRepo].createNew(Ja, "商店111")
        val cats = 0 to 4 map {i => inject[CategoryRepo].createNew(Map(Ja -> ("Cat" + i)))}
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val items = 0 to 4 map {i => inject[ItemRepo].createNew(cats(i))}
        items.foreach {it => inject[SiteItemRepo].createNew(site, it)}
        val itemNames = 0 to 4 map {i => inject[ItemNameRepo].createNew(items(i), Map(Ja -> ("item" + i)))}
        val itemDescs = 0 to 4 map {i => inject[ItemDescriptionRepo].createNew(items(i), site, "説明" + i)}
        val itemPrices = items.map {it => inject[ItemPriceRepo].createNew(it, site)}
        val itemPriceHistories = 0 to 4 map {i =>
          inject[ItemPriceHistoryRepo].createNew(
            itemPrices(i), tax, currencyInfo.Jpy, BigDecimal(100 * i), None,
            BigDecimal(90 * i), date("9999-12-31")
          )
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryAdvanced(
            qs = List(), cs = "", ccs = "", sid = None, page = 0, pageSize = 10,
            orderBySpec = "item.item_id", templateNo = 0
          ).url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          failFalse(browser.find(".qthumItem_name").first().displayed())
        )
        0 to 4 foreach { i =>
          browser.find("#queryBody .qthumItem").index(i).find(".qthumItem_name").text === "item" + i
        }

        browser.find("#orderBySelect option[value='item.item_id DESC']").click()
        browser.await().atMost(10, TimeUnit.SECONDS).untilPage().isLoaded()

        0 to 4 foreach { i =>
          new WebDriverWait(browser.webDriver, 30).until(itemNameMatcher(i, "item" + (4 - i)))
        }

        browser.find("#orderBySelect option[value='item_price_history.unit_price ASC']").click()
        browser.await().atMost(10, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.waitUntil(
          failFalse(browser.find("#queryBody .qthumItem").first().displayed())
        )
        0 to 4 foreach { i =>
          new WebDriverWait(browser.webDriver, 30).until(itemNameMatcher(i, "item" + i))
        }
      }
    }

    "All items should be paged." in new WithBrowser(
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

        val adminUser = loginWithTestUser(browser)

        val site = inject[SiteRepo].createNew(Ja, "商店111")
        val range = 0 to 10
        val cats = range map {i => inject[CategoryRepo].createNew(Map(Ja -> ("Cat" + i)))}
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val items = range map {i => inject[ItemRepo].createNew(cats(i))}
        items.foreach {it => inject[SiteItemRepo].createNew(site, it)}
        val itemNames = range map {i => inject[ItemNameRepo].createNew(items(i), Map(Ja -> ("item" + i)))}
        val itemDescs = range map {i => inject[ItemDescriptionRepo].createNew(items(i), site, "説明" + i)}
        val itemPrices = items.map {it => inject[ItemPriceRepo].createNew(it, site)}
        val itemPriceHistories = range map {i =>
          inject[ItemPriceHistoryRepo].createNew(
            itemPrices(i), tax, currencyInfo.Jpy, BigDecimal(100 * i), None,
            BigDecimal(90 * i), date("9999-12-31")
          )
        }

        browser.goTo(
          controllers.routes.ItemQuery.queryAdvanced(
            qs = List(), cs = "", ccs = "", sid = None, page = 0, pageSize = 10,
            orderBySpec = "item.item_id", templateNo = 0
          ).url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          failFalse(browser.find(".qthumItem_name").first().displayed())
        )
        0 to 9 foreach { i =>
          browser.find("#queryBody .qthumItem").index(i).find(".qthumItem_name").text === "item" + i
        }

        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination button.prevPageButton[disabled='disabled']").first().displayed())
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").text == "1/2")
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination button.nextPageButton").first().displayed())
        )
        browser.find("#pagingPaneDestination .pageSizes").index(0).find(".currentSize").text === "10"
        browser.find("#pagingPaneDestination .pageSizes a").index(0).text === "25"
        browser.find("#pagingPaneDestination .pageSizes a").index(1).text === "50"
        browser.find("#pagingPaneDestination button.nextPageButton").attribute("disabled") === null

        browser.find("#pagingPaneDestination button.nextPageButton").click()
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination button.nextPageButton[disabled='disabled']").first().displayed())
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").text == "2/2")
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination button.prevPageButton").first().displayed())
        )
        browser.find("#pagingPaneDestination .pageSizes").index(0).find(".currentSize").text === "10"
        browser.find("#pagingPaneDestination .pageSizes a").index(0).text === "25"
        browser.find("#pagingPaneDestination .pageSizes a").index(1).text === "50"
        browser.find("#pagingPaneDestination button.prevPageButton").attribute("disabled") === null

        browser.find("#queryBody .qthumItem").index(0).find(".qthumItem_name").text === "item10"
        browser.find("#queryBody .qthumItem").size === 1

        browser.find("#pagingPaneDestination button.prevPageButton").click()
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination button.prevPageButton[disabled='disabled']").first().displayed())
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").text == "1/2")
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination button.nextPageButton").first().displayed())
        )
        browser.find("#pagingPaneDestination .pageSizes").index(0).find(".currentSize").text === "10"
        browser.find("#pagingPaneDestination .pageSizes a").index(0).text === "25"
        browser.find("#pagingPaneDestination .pageSizes a").index(1).text === "50"
        browser.find("#pagingPaneDestination button.nextPageButton").attribute("disabled") === null

        0 to 9 foreach { i =>
          browser.find("#queryBody .qthumItem").index(i).find(".qthumItem_name").text === "item" + i
        }
      }
    }

    "All items should be queried by category." in new WithBrowser(
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

        val adminUser = loginWithTestUser(browser)

        val site = inject[SiteRepo].createNew(Ja, "商店111")
        val range = 0 to 20
        val cats = range map {i => inject[CategoryRepo].createNew(Map(Ja -> ("Cat" + i)))}

        val oddCat = inject[CategoryRepo].createNew(Map(Ja -> ("OddCat")))
        inject[CategoryRepo].updateCategoryCode(oddCat.id.get, "10000000")
        val evenCat = inject[CategoryRepo].createNew(Map(Ja -> ("EvenCat")))
        inject[CategoryRepo].updateCategoryCode(evenCat.id.get, "10000001")

        val aboveFiveCat = inject[CategoryRepo].createNew(Map(Ja -> ("AboveTenCat")))
        inject[CategoryRepo].updateCategoryCode(aboveFiveCat.id.get, "2000000")

        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val items = range map {i => inject[ItemRepo].createNew(cats(i))}
        items.foreach {it => inject[SiteItemRepo].createNew(site, it)}
        val itemNames = range map {i => inject[ItemNameRepo].createNew(items(i), Map(Ja -> ("item" + i)))}
        val itemDescs = range map {i => inject[ItemDescriptionRepo].createNew(items(i), site, "説明" + i)}
        val itemPrices = items.map {it => inject[ItemPriceRepo].createNew(it, site)}
        val itemPriceHistories = range map {i =>
          inject[ItemPriceHistoryRepo].createNew(
            itemPrices(i), tax, currencyInfo.Jpy, BigDecimal(100 * i), None,
            BigDecimal(90 * i), date("9999-12-31")
          )
        }
        range.filter(_ % 2 != 0).map { i =>
          inject[SupplementalCategoryRepo].createNew(items(i).id.get, oddCat.id.get)
        }
        range.filter(_ > 5).map { i =>
          inject[SupplementalCategoryRepo].createNew(items(i).id.get, aboveFiveCat.id.get)
        }
        
        browser.goTo(
          controllers.routes.ItemQuery.queryAdvanced(
            qs = List(), cs = "", ccs = "", sid = None, page = 0, pageSize = 10,
            orderBySpec = "item.item_id", templateNo = 0
          ).url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          failFalse(browser.find(".qthumItem_name").first().displayed())
        )
        0 to 9 foreach { i =>
          browser.find("#queryBody .qthumItem").index(i).find(".qthumItem_name").text === "item" + i
        }

        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").text == "1/3")
        )

        // turn on odd cat
        browser.find("#categoryCondition .categoryConditionItem[data-category-code='10000000'] label").click()
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").text == "1/1")
        )
        0 to 9 foreach { i =>
          browser.find("#queryBody .qthumItem").index(i).find(".qthumItem_name").text === "item" + (i * 2 + 1)
        }

        browser.find(".category02").click()
        browser.waitUntil(
          failFalse(browser.find(".categoryConditionItem[data-category-code='2000000'] label").first().displayed())
        )

        // turn on above ten cat
        browser.find(".categoryConditionItem[data-category-code='2000000'] label").click()

        new WebDriverWait(browser.webDriver, 30).until(itemNameMatcher(0, "item7"))
        
        browser.find(".category01").click()
        browser.waitUntil(
          failFalse(browser.find("#categoryCondition .categoryConditionItem[data-category-code='10000000'] label").first().displayed())
        )
        // turn off odd cat
        browser.waitUntil(
          browser.find(".categoryConditionItem[data-category-code='10000000'] label")
        ).click()

        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").first().displayed())
        )
        browser.waitUntil(
          failFalse(browser.find("#pagingPaneDestination .pageCount").text == "1/2")
        )

        browser.find("#queryBody .qthumItem").size === 10
        0 to 9 foreach { i =>
          browser.find("#queryBody .qthumItem").index(i).find(".qthumItem_name").text === "item" + (i + 6)
        }
        browser.find("#pagingPaneDestination button.nextPageButton").click()

        new WebDriverWait(browser.webDriver, 30).until(itemNameMatcher(0, "item16"))
      }
    }
  }
}

