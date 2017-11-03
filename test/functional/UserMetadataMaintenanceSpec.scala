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

class UserMetadataMaintenanceSpec extends Specification with InjectorSupport {
  "User metadata" should {
    "Can create metadata if it does not exists" in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase())
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
          controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil(
          failFalse(browser.find(".userTableBody").size == 1)
        )

        browser.find(".modifyUserMetadataButton").click()

        browser.waitUntil {
          failFalse(browser.webDriver.getTitle == Messages("commonTitle", Messages("modifyUserMetadataTitle")))
        }

        browser.find("#submitUserMetadata").click()

        browser.waitUntil(
          failFalse(browser.find(".userTableBody").size == 1)
        )

        doWith(UserMetadata.getByStoreUserId(adminUser.id.get).get) { um =>
          um.storeUserId === adminUser.id.get
          um.photoUrl === None
          um.firstNameKana === None
          um.middleNameKana === None
          um.lastNameKana === None
          um.telNo0 === None
          um.telNo1 === None
          um.telNo2 === None
          um.joinedDate === None
          um.birthMonthDay === None
          um.profileComment === None
        }

        browser.find(".modifyUserMetadataButton").click()

        browser.waitUntil {
          failFalse(browser.webDriver.getTitle == Messages("commonTitle", Messages("modifyUserMetadataTitle")))
        }

        browser.find("#firstNameKana").fill().`with`("first name kana")
        browser.find("#middleNameKana").fill().`with`("middle name kana")
        browser.find("#lastNameKana").fill().`with`("last name kana")
        browser.find("#telNo0").fill().`with`("00000000")
        browser.find("#telNo1").fill().`with`("11111111")
        browser.find("#telNo2").fill().`with`("22222222")
        browser.find("#joinedDateTextBox").fill().`with`("2222")
        browser.find("#photoUrl").fill().`with`("URL000")
        browser.find("#birthMonthDay").fill().`with`("ABC")
        browser.find("#profileComment").fill().`with`("PROFILE COMMENT")

        browser.find("#submitUserMetadata").click()

        browser.waitUntil {
          failFalse(browser.find(".globalErrorMessage").text == Messages("inputError"))
        }

        browser.find("#joinedDateTextBox_field .error").text === Messages("error.localDateTime")
        browser.find("#joinedDateTextBox_field .info").text === Messages("format.localDateTime", Messages("joind.date.format"))

        browser.find("#birthMonthDay_field .error").text === Messages("birthMonthDay.error")
        browser.find("#birthMonthDay_field .info").text === Messages("birthMonthDay.info")

        browser.find("#joinedDateTextBox").fill().`with`("2000年1月22日")
        browser.find("#birthMonthDay").fill().`with`("012")

        browser.find("#submitUserMetadata").click()

        browser.waitUntil {
          failFalse(browser.find(".globalErrorMessage").text == Messages("inputError"))
        }

        browser.find("#birthMonthDay_field .error").text === Messages("birthMonthDay.error")
        browser.find("#birthMonthDay_field .info").text === Messages("birthMonthDay.info")

        browser.find("#birthMonthDay").fill().`with`("0123")
        browser.find("#submitUserMetadata").click()

        browser.waitUntil {
          failFalse(browser.find(".message").text === Messages("userIsUpdated"))
        }

        doWith(UserMetadata.getByStoreUserId(adminUser.id.get).get) { um =>
          um.storeUserId === adminUser.id.get
          um.photoUrl === Some("URL000")
          um.firstNameKana === Some("first name kana")
          um.middleNameKana === Some("middle name kana")
          um.lastNameKana === Some("last name kana")
          um.telNo0 === Some("00000000")
          um.telNo1 === Some("11111111")
          um.telNo2 === Some("22222222")
          um.joinedDate === Some(Instant.ofEpochMilli(date("2000-01-22").getTime))
          um.birthMonthDay === Some(MonthDay(123))
          um.profileComment === Some("PROFILE COMMENT")
        }

        browser.find(".modifyUserMetadataButton").click()

        browser.waitUntil {
          failFalse(browser.webDriver.getTitle == Messages("commonTitle", Messages("modifyUserMetadataTitle")))
        }

        browser.find("#firstNameKana").attribute("value") === "first name kana"
        browser.find("#middleNameKana").attribute("value") === "middle name kana"
        browser.find("#lastNameKana").attribute("value") === "last name kana"
        browser.find("#telNo0").attribute("value") === "00000000"
        browser.find("#telNo1").attribute("value") === "11111111"
        browser.find("#telNo2").attribute("value") === "22222222"
        browser.find("#joinedDateTextBox").attribute("value") === "2000年01月22日"
        browser.find("#photoUrl").attribute("value") === "URL000"
        browser.find("#birthMonthDay").attribute("value") === "123"
        browser.find("#profileComment").text === "PROFILE COMMENT"
      }
    }
  }
}
