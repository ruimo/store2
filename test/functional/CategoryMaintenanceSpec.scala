package functional

import models._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.util.concurrent.TimeUnit
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection
import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import play.api.test.TestServer
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class CategoryMaintenanceSpec extends Specification with InjectorSupport {
  "Category maintenance" should {
    "List nothing when there are no categories." in new WithBrowser(
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
          controllers.routes.CategoryMaintenance.editCategory(None).url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("editCategoryTitle"))
        
        browser.find("#langSpec option").size === localeInfo.registry.size
        browser.find(".categoryTableBody").size === 0
      }
    }

    "Can query all categories in order." in new WithBrowser(
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
          controllers.routes.CategoryMaintenance.startCreateNewCategory().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewCategoryTitle"))

        browser.find("#langId option").size === localeInfo.registry.size
        browser.find("#langId option[value='" + localeInfo.Ja.id + "']").click()
        browser.find("#categoryName").fill().`with`("カテゴリ001")
        browser.find("#createNewCategoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        11 to 2 by -1 foreach { i =>
          browser.find("#langId option[value='" + localeInfo.Ja.id + "']").click()
          browser.find("#categoryName").fill().`with`(f"カテゴリ$i%03d")
          browser.find("#createNewCategoryButton").click()
          browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        }

        browser.goTo(
          controllers.routes.CategoryMaintenance.editCategory(None).url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("tr.categoryTableBody").size === 10

        browser.find(".categoryTableName").index(0).text === "カテゴリ001"
        browser.find(".categoryTableName").index(1).text === "カテゴリ011"
        browser.find(".categoryTableName").index(2).text === "カテゴリ010"
        browser.find(".categoryTableName").index(3).text === "カテゴリ009"
        browser.find(".categoryTableName").index(4).text === "カテゴリ008"
        browser.find(".categoryTableName").index(5).text === "カテゴリ007"
        browser.find(".categoryTableName").index(6).text === "カテゴリ006"
        browser.find(".categoryTableName").index(7).text === "カテゴリ005"
        browser.find(".categoryTableName").index(8).text === "カテゴリ004"
        browser.find(".categoryTableName").index(9).text === "カテゴリ003"
        browser.find(".nextPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("tr.categoryTableBody").size === 1
        browser.find(".categoryTableName").text === "カテゴリ002"

        browser.find(".categoryTableHeaderId .orderColumn").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // Reverse order by id
        browser.find(".categoryTableName").index(0).text === "カテゴリ002"
        browser.find(".categoryTableName").index(1).text === "カテゴリ003"
        browser.find(".categoryTableName").index(2).text === "カテゴリ004"
        browser.find(".categoryTableName").index(3).text === "カテゴリ005"
        browser.find(".categoryTableName").index(4).text === "カテゴリ006"
        browser.find(".categoryTableName").index(5).text === "カテゴリ007"
        browser.find(".categoryTableName").index(6).text === "カテゴリ008"
        browser.find(".categoryTableName").index(7).text === "カテゴリ009"
        browser.find(".categoryTableName").index(8).text === "カテゴリ010"
        browser.find(".categoryTableName").index(9).text === "カテゴリ011"
        browser.find(".nextPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("tr.categoryTableBody").size === 1
        browser.find(".categoryTableName").text === "カテゴリ001"

        browser.find(".categoryTableHeaderName .orderColumn").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".categoryTableName").index(0).text === "カテゴリ001"
        browser.find(".categoryTableName").index(1).text === "カテゴリ002"
        browser.find(".categoryTableName").index(2).text === "カテゴリ003"
        browser.find(".categoryTableName").index(3).text === "カテゴリ004"
        browser.find(".categoryTableName").index(4).text === "カテゴリ005"
        browser.find(".categoryTableName").index(5).text === "カテゴリ006"
        browser.find(".categoryTableName").index(6).text === "カテゴリ007"
        browser.find(".categoryTableName").index(7).text === "カテゴリ008"
        browser.find(".categoryTableName").index(8).text === "カテゴリ009"
        browser.find(".categoryTableName").index(9).text === "カテゴリ010"

        browser.find(".nextPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("tr.categoryTableBody").size === 1
        browser.find(".categoryTableName").text === "カテゴリ011"
      }
    }

    "Can change category name." in new WithBrowser(
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
          controllers.routes.CategoryMaintenance.startCreateNewCategory().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewCategoryTitle"))

        browser.find("#langId option").size === localeInfo.registry.size
        browser.find("#langId option[value='" + localeInfo.Ja.id + "']").click()
        browser.find("#categoryName").fill().`with`("カテゴリ001")
        browser.find("#createNewCategoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        val page: Page[(Category, CategoryName)] = inject[CategoryRepo].list(page = 0, pageSize = 10, locale = Ja)
        page.total === 1
        page.list.head._1.id.get.toString === page.list.head._1.categoryCode

        browser.goTo(
          controllers.routes.CategoryMaintenance.editCategory(None).url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".categoryTableName").index(0).text === "カテゴリ001"

        browser.find("#langSpec option[value='" + localeInfo.En.id + "']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".categoryTableName").index(0).text === "-"

        browser.find(".editCategoryNameLink").index(0).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("editCategoryNameTitle"))
        browser.find(".langName").text === Messages("lang.ja")
        browser.find("#categoryNames_0_name").attribute("value") === "カテゴリ001"

        browser.find("#categoryNames_0_name").fill().`with`("カテゴリ999")
        browser.find("#submitCategoryNameUpdate").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("editCategoryTitle"))
        browser.find(".editCategoryNameLink").index(0).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        
        browser.webDriver.getTitle === Messages("commonTitle", Messages("editCategoryNameTitle"))
        browser.find("#createCategoryNameForm #localeId option[value='" + localeInfo.En.id + "']").click()
        browser.find("#createCategoryNameForm #name").fill().`with`("category999")
        browser.find("#submitCategoryNameCreate").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("editCategoryNameTitle"))
        if (browser.find(".langName").index(0).text == Messages("lang.ja")) {
          browser.find("#categoryNames_0_name").attribute("value") === "カテゴリ999"
          browser.find("#categoryNames_1_name").attribute("value") === "category999"
          browser.find(".updateCategoryName button").index(0).click()
        }
        else {
          browser.find("#categoryNames_0_name").attribute("value") === "category999"
          browser.find("#categoryNames_1_name").attribute("value") === "カテゴリ999"
          browser.find(".updateCategoryName button").index(1).click()
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".langName").size === 1
        browser.find(".langName").text == Messages("lang.en")
      }
    }

    "Can change category code." in new WithBrowser(
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

        val cat01 = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val cat02 = inject[CategoryRepo].createNew(Map(Ja -> "Cat02"))

        browser.goTo(
          controllers.routes.CategoryMaintenance.editCategory(None).url + "?lang=" + lang.code
        )

        browser.find(".editCategoryNameLink").index(0).text === cat01.id.get.toString
        browser.find(".editCategoryNameLink").index(1).text === cat02.id.get.toString

        browser.find(".editCategoryCodeLink").index(0).text === cat01.categoryCode
        browser.find(".editCategoryCodeLink").index(1).text === cat02.categoryCode

        browser.find(".editCategoryCodeLink").index(0).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#categoryCode_field dd[class='info']").index(0).text === Messages("constraint.required")
        browser.find("#categoryCode_field dd[class='info']").index(1).text === Messages("constraint.maxLength", 20)
        browser.find("#categoryCode_field dd[class='info']").index(2).text === Messages("categoryCodePattern")

        browser.find("#submitCategoryCodeUpdate").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#categoryCode_field dd[class='error']").index(0).text === Messages("error.required")
        browser.find("#categoryCode_field dd[class='error']").index(1).text === Messages("categoryCodePatternError")

        browser.find("#categoryCode").fill().`with`("#")
        browser.find("#submitCategoryCodeUpdate").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#categoryCode_field dd[class='error']").index(0).text === Messages("categoryCodePatternError")

        browser.find("#categoryCode").fill().`with`("123456789012345678901")
        browser.find("#submitCategoryCodeUpdate").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#categoryCode_field dd[class='error']").index(0).text === Messages("error.maxLength", 20)

        browser.find("#categoryCode").fill().`with`("ABCD")
        browser.find("#submitCategoryCodeUpdate").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".editCategoryCodeLink").index(0).text === "ABCD"
      }
    }
  }
}
