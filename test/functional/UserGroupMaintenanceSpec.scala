package functional

import java.sql.Date.{valueOf => date}
import play.api.{Application => PlayApp}
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import views.Titles
import helpers.Formatter
import helpers.UrlHelper
import helpers.UrlHelper._
import helpers.PasswordHash
import constraints.FormConstraints
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection
import java.util.concurrent.TimeUnit

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import play.api.test.TestServer
import org.openqa.selenium.By
import models._
import com.ruimo.scoins.Scoping._
import SeleniumHelpers.htmlUnit
import SeleniumHelpers.FirefoxJa

class UserGroupMaintenanceSpec extends Specification with InjectorSupport {
  "User group maintenance" should {
    "Empty messages should be shown when no user group exists" in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase())
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
          controllers.routes.UserGroupMaintenance.edit().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          browser.find(".emptyMessage")
        ).text === Messages("recordEmpty")
      }
    }

    "Create group" in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase())
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
          controllers.routes.UserGroupMaintenance.startCreate().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(browser.find("#groupName")).fill().`with`("group001")
        browser.find(".createButton").click()

        doWith(browser.waitUntil(browser.find(".userGroupTable"))) { tbl =>
          tbl.find("tr.body").size === 1
          tbl.find("tr.body .groupName a").text === "group001"
        }

        browser.goTo(
          controllers.routes.UserGroupMaintenance.startCreate().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(browser.find("#groupName")).fill().`with`("group002")
        browser.find(".createButton").click()

        doWith(browser.waitUntil(browser.find(".userGroupTable"))) { tbl =>
          tbl.find("tr.body").size === 2
          tbl.find("tr.body .groupName a").index(0).text === "group001"
          tbl.find("tr.body .groupName a").index(1).text === "group002"
        }

        // remove group002
        browser.find(".removeUserGroupBtn").index(1).click()

        // cancel button
        browser.waitUntil(browser.find(".ui-dialog-buttonset .ui-button.ui-widget").index(1)).click()

        // remove group002
        browser.waitUntil(browser.find(".removeUserGroupBtn")).index(1).click()

        // delete button
        browser.waitUntil(browser.find(".ui-dialog-buttonset .ui-button.ui-widget").index(0)).click()

        doWith(browser.waitUntil(browser.find(".userGroupTable"))) { tbl =>
          browser.waitUntil(tbl.find("tr.body").size == 1)
          tbl.find("tr.body .groupName a").index(0).text === "group001"
        }
      }
    }

    "Create group" in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase())
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
          controllers.routes.UserGroupMaintenance.startCreate().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(browser.find("#groupName")).fill().`with`("group001")
        browser.find(".createButton").click()

        doWith(browser.waitUntil(browser.find(".userGroupTable"))) { tbl =>
          tbl.find("tr.body").size === 1
          tbl.find("tr.body .groupName a").text === "group001"
        }

        browser.goTo(
          controllers.routes.UserGroupMaintenance.startCreate().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(browser.find("#groupName")).fill().`with`("group002")
        browser.find(".createButton").click()

        doWith(browser.waitUntil(browser.find(".userGroupTable"))) { tbl =>
          tbl.find("tr.body").size === 2
          tbl.find("tr.body .groupName a").index(0).text === "group001"
          tbl.find("tr.body .groupName a").index(1).text === "group002"
        }

        val user1 = createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        val user2 = createNormalUser(
          browser, "22222222", "password02", "user02@mail.xxx", "firstName02", "lastName02", "company02"
        )
        val user3 = createNormalUser(
          browser, "33333333", "password03", "user03@mail.xxx", "firstName03", "lastName03", "company03"
        )
        
        browser.goTo(
          controllers.routes.UserGroupMaintenance.edit().url.addParm("lang", lang.code).toString
        )

        doWith(browser.waitUntil(browser.find(".userGroupTable"))) { tbl =>
          tbl.find("tr.body .groupName a").index(0).click()
        }

        browser.waitUntil(browser.find(".userGroup .groupName")).text === "group001"
        browser.waitUntil(browser.find(".emptyMessage")).text === Messages("recordEmpty")

        browser.switchTo(browser.waitUntil(browser.find(".userListForMemberFrame")))
        browser.find("tr.body .userName").index(0).text === "11111111"
        browser.find("tr.body button").index(0).click()

        browser.waitUntil(browser.find(".userGroupMemberTable tr.body").size == 1)
        browser.find(".userGroupMemberTable tr.body .userName").text === "11111111"

        browser.switchTo(browser.waitUntil(browser.find(".userListForMemberFrame")))
        browser.find("tr.body .userName").index(1).text === "22222222"
        browser.find("tr.body button").index(1).click()

        browser.waitUntil(browser.find(".userGroupMemberTable tr.body").size == 2)
        browser.find(".userGroupMemberTable tr.body .userName").index(0).text === "11111111"
        browser.find(".userGroupMemberTable tr.body .userName").index(1).text === "22222222"

        browser.find(".userGroupMemberTable .removeUserGroupMemberBtn").index(0).click()

        browser.waitUntil(browser.find(".userGroupMemberTable tr.body").size == 1)
        browser.find(".userGroupMemberTable tr.body .userName").index(0).text === "22222222"
      }
    }
  }
}
