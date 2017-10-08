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

class EmployeeMaintenanceSpec extends Specification with InjectorSupport {
  "Employee" should {
    "When no employment." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val site0 = inject[SiteRepo].createNew(Ja, "店舗0")
        val site1 = inject[SiteRepo].createNew(Ja, "店舗1")
        val adminUser = loginWithTestUser(browser)

        browser.goTo(
          controllers.routes.EmployeeMaintenance.startModify(adminUser.id.get).url.addParm("lang", lang.code).toString
        )
        browser.waitUntil(browser.find(".noRecord")).text === Messages("recordEmpty")
        browser.find("#siteId").find("option").index(0).text === "店舗0"
        browser.find("#siteId").find("option").index(1).text === "店舗1"
      }
    }

    "Can add employment." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val site2 = inject[SiteRepo].createNew(Ja, "店舗2")
        val site0 = inject[SiteRepo].createNew(Ja, "店舗0")
        val site1 = inject[SiteRepo].createNew(Ja, "店舗1")
        val adminUser = loginWithTestUser(browser)

        browser.goTo(
          controllers.routes.EmployeeMaintenance.startModify(adminUser.id.get).url.addParm("lang", lang.code).toString
        )
        browser.waitUntil(browser.find(".noRecord")).text === Messages("recordEmpty")
        browser.find("#siteId").find("option").index(0).text === "店舗0"
        browser.find("#siteId").find("option").index(1).text === "店舗1"
        browser.find("#siteId").find("option").index(2).text === "店舗2"

        browser.find("#siteId").find("option").index(1).click()
        browser.find("#createEmployeeBtn").click()

        browser.waitUntil(browser.find(".employeeTable")).find(".row.site").text === "店舗1"
        browser.find("#siteId").find("option").index(0).text === "店舗0"
        browser.find("#siteId").find("option").index(1).text === "店舗2"

        browser.find("#siteId").find("option").index(1).click()
        browser.find("#createEmployeeBtn").click()

        browser.waitUntil(browser.find(".employeeTable")).find(".row.site").index(0).text === "店舗1"
        browser.find(".employeeTable").find(".row.site").index(1).text === "店舗2"

        browser.find(".row.exchange .exchangeBtn").click()

        browser.waitUntil(browser.find(".employeeTable")).find(".row.site").index(0).text === "店舗2"
        browser.find(".employeeTable").find(".row.site").index(1).text === "店舗1"

        browser.find(".removeBtn").index(0).click()
        browser.waitUntil(browser.find(".employeeTable")).find(".row.site").size === 1
        browser.find(".employeeTable .row.site").text === "店舗1"

        browser.find("#siteId").find("option").size === 2
        browser.find("#siteId").find("option").index(0).text === "店舗0"
        browser.find("#siteId").find("option").index(1).text === "店舗2"
      }
    }
  }
}
