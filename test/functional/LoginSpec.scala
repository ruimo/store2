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
import java.util.concurrent.TimeUnit
import helpers.Helper._

class LoginSpec extends Specification with InjectorSupport {
  "Login" should {
    "Login screen is shown if not logged in." in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ disableMailer)
    ) {
println("setting = " + System.getProperty("webdriver.gecko.driver"))
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = createTestUser
        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("loginTitle"))
        browser.find("#loginWelcomeMessage").size === 0
      }
    }

    "Login empty error." in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ disableMailer)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = createTestUser
        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("loginTitle"))

        browser.$("#doLoginButton").click()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("loginTitle"))

        browser.$(".globalErrorMessage").text === Messages("inputError")
        browser.$("#userName_field dd.error").text === Messages("error.required")
        browser.$("#password_field dd.error").text === Messages("error.required")
      }
    }

    "Login success." in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ disableMailer)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = createTestUser
        browser.goTo(controllers.routes.Admin.index.url + "?lang=" + lang.code)
        browser.webDriver.getTitle === Messages("commonTitle", Messages("loginTitle"))

        browser.find("#userName").fill().`with`("administrator")
        browser.find("#password").fill().`with`("password")
        browser.find("#doLoginButton").click()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("adminTitle"))

        browser.find("#loginWelcomeMessage").text === 
          String.format(Messages("login.welcome"), "Admin", "", "Manager")
      }
    }
  }
}
