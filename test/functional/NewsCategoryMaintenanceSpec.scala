package functional

import java.util.Arrays
import org.specs2.runner._
import java.text.SimpleDateFormat
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import play.api.http.Status
import org.openqa.selenium.By
import java.nio.file.{Paths, Files}
import org.openqa.selenium.JavascriptExecutor
import java.util.concurrent.TimeUnit
import helpers.UrlHelper
import helpers.UrlHelper._
import anorm._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class NewsCategoryMaintenanceSpec extends Specification with InjectorSupport {
  lazy val avoidLogin = Map(
    "need.authentication.entirely" -> false
  )

  "News category maintenace" should {
    "Create category" in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ avoidLogin)
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

        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNewsCategory().url.addParm("lang", lang.code).toString
        )
        browser.find("input.createNewsCategoryButton").click()
        browser.waitUntil(
          failFalse(browser.find(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.find("#categoryName_field dd[class='info']").index(0).text === Messages("constraint.required")
        browser.find("#categoryName_field dd[class='info']").index(1).text === Messages("constraint.maxLength", 64)
        browser.find("#categoryName_field dd[class='error']").index(0).text === Messages("error.required")

        browser.find("#iconUrl_field dd[class='info']").index(0).text === Messages("constraint.required")
        browser.find("#iconUrl_field dd[class='info']").index(1).text === Messages("constraint.maxLength", 1024)
        browser.find("#iconUrl_field dd[class='error']").index(0).text === Messages("error.required")

        browser.find("#categoryName").fill().`with`("category002")
        browser.find("#iconUrl").fill().`with`("iconUrl001")

        browser.find("input.createNewsCategoryButton").click()
        browser.waitUntil(
          failFalse(browser.find(".message").text == Messages("newsCategoryIsUpdated"))
        )

        browser.find("#categoryName").fill().`with`("category001")
        browser.find("#iconUrl").fill().`with`("iconUrl002")

        browser.find("input.createNewsCategoryButton").click()
        browser.waitUntil(
          failFalse(browser.find(".message").text == Messages("newsCategoryIsUpdated"))
        )

        browser.find("#categoryName").fill().`with`("category003")
        browser.find("#iconUrl").fill().`with`("iconUrl003")

        browser.find("input.createNewsCategoryButton").click()
        browser.waitUntil(
          failFalse(browser.find(".message").text == Messages("newsCategoryIsUpdated"))
        )

        browser.goTo(
          controllers.routes.NewsMaintenance.listNewsCategory(
            page = 0l page = 10, orderBySpec = "news_category.category_name"
          ).url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          failFalse(browser.find(".newsCategoryTableBody").size == 1)
        )

        browser.find(".newsCategoryTableBody .id").index(0).text === "1001"

Thread.sleep(1000000)
      }
    }
  }
}
