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
import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import play.api.i18n.{Lang, Messages}
import models._
import play.api.Play
import constraints.FormConstraints
import helpers.Helper._

class FirstSetupSpec extends Specification with InjectorSupport {
  "FirstSetup" should {
    "First setup screen is shown if no user found." in new WithBrowser(
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

        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))
      }
    }

    "First setup create user." in new WithBrowser(
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

        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))
        browser.find("#userName").fill().`with`("username")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("companyname")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345678")

        browser.submit("#registerFirstUser")
        browser.waitUntil(
          failFalse(browser.webDriver.getTitle == Messages("commonTitle", Messages("loginTitle")))
        )

        val list = inject[StoreUserRepo].all
        list.size === 1
        val user = list.head

        user.deleted === false
        user.email === "ruimo@ruimo.com"
        user.firstName === "firstname"
        user.lastName === "lastname"
        user.userName === "username"
        user.companyName === Some("companyname")
        user.userRole === UserRole.ADMIN
      }
    }

    "Minimum length error." in new WithBrowser(
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

        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))
        browser.find("#userName").fill().`with`("usern")
        browser.find("#firstName").fill().`with`("")
        browser.find("#lastName").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#password_main").fill().`with`("")
        browser.find("#password_confirm").fill().`with`("12345678")
        browser.find("#companyName").fill().`with`("")

        browser.submit("#registerFirstUser")
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.$("#userName_field dd.error").text === Messages("error.minLength", 6)
        browser.$("#companyName_field dd.error").text === Messages("error.required")
        browser.$("#firstName_field dd.error").text === Messages("error.required")
        browser.$("#email_field dd.error").text === Messages("error.email")
        browser.$("#password_main_field dd.error").text === Messages("error.minLength", inject[FormConstraints].passwordMinLength())
      }
    }

    "Invalid email error." in new WithBrowser(
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

        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))
        browser.find("#userName").fill().`with`("userName")
        browser.find("#firstName").fill().`with`("firstName")
        browser.find("#lastName").fill().`with`("lastName")
        browser.find("#email").fill().`with`("ruimo")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")

        browser.submit("#registerFirstUser")
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.$("#email_field dd.error").text === Messages("error.email")
      }
    }

    "Confirmation password does not match." in new WithBrowser(
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

        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))
        browser.find("#userName").fill().`with`("username")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345679")

        browser.submit("#registerFirstUser")
        browser.webDriver.getTitle === Messages("commonTitle", Messages("firstSetupTitle"))

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.$("#password_confirm_field dd.error").text === Messages("confirmPasswordDoesNotMatch")
      }
    }
  }
}

