package functional

import java.util.concurrent.TimeUnit
import java.time.ZoneId
import play.api.i18n.MessagesApi
import anorm._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.sql.Connection
import java.time.Instant
import java.time.format.DateTimeFormatter

import com.ruimo.scoins.Scoping._
import play.api.test.{WebDriverFactory, WithBrowser}
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ProfileMaintenanceSpec extends Specification with InjectorSupport {
  def appl: PlayApp = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

  "Profile maintenance" should {
    "Can create profile." in new WithBrowser(
      WebDriverFactory(CHROME), appl
    ) {
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val adminUser = loginWithTestUser(browser)
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        browser.goTo(
          controllers.routes.ProfileMaintenance.changeProfile() + "?lang=" + lang.code
        )
        browser.waitUntil {
          failFalse(browser.find("h1.title").text == Messages("changeUserProfileTitle"))
        }

        browser.find("#firstName").attribute("value") === "Admin"
        browser.find("#lastName").attribute("value") === "Manager"
        browser.find("#email").attribute("value") === "admin@abc.com"
        browser.find("#firstNameKana").attribute("value") === ""
        browser.find("#lastNameKana").attribute("value") === ""
        browser.find("#telNo0").attribute("value") === ""
        browser.find("#telNo1").attribute("value") === ""
        browser.find("#telNo2").attribute("value") === ""
        browser.find("#joinedDateTextBox").attribute("value") === ""
        browser.find("#photoUrl").attribute("value") === ""
        browser.find("#birthMonthDay").attribute("value") === ""
        browser.find("#profileComment").text === ""
        browser.find("#password").attribute("value") === ""
        browser.find("#updateButton").click()

        browser.waitUntil {
          failFalse(browser.find(".globalErrorMessage").text == Messages("inputError"))
        }
        doWith(browser.find("#password_field")) { f =>
          val passwordMinLength: Int = inject[constraints.FormConstraints].passwordMinLength()
          val passwordMaxLength: Int = inject[constraints.FormConstraints].passwordMaxLength
          f.find(".error").text === Messages("error.minLength", passwordMinLength)
          f.find(".info").index(0).text === Messages("constraint.minLength", passwordMinLength)
          f.find(".info").index(1).text === Messages("constraint.maxLength", passwordMaxLength)
          f.find(".info").index(2).text === Messages("constraint.password.char")
        }

      }
    }
  }
}

