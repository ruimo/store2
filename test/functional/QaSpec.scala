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
import play.api.test.Helpers._
import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import helpers.Helper.disableMailer
import controllers.NeedLogin
import java.util.concurrent.TimeUnit
import models._

class QaSpec extends Specification with InjectorSupport {
  val conf = inMemoryDatabase() ++ disableMailer

  "QA" should {
    "All field should be blank if no one is logged in." in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val optAuth = inject[NeedLogin.OptAuthenticated]

        if (optAuth.needAuthenticationEntirely) {
          1 === 1
        }
        else {
          browser.goTo(
            controllers.routes.Qa.index() + "?lang=" + lang.code
          )
          browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
          browser.webDriver.getTitle === Messages("commonTitle", Messages("qaTitle"))

          browser.find("#qaTypeGroup .help-block").text === Messages("constraint.required")
          browser.find("#comment").text === ""
          browser.find("#companyName").text === ""
          browser.find("#firstName").text === ""
          browser.find("#lastName").text === ""
          browser.find("#tel").text === ""
          browser.find("#email").text === ""
        }
      }
    }

    "If some one is logged in, some fields should be filled." in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user: StoreUser = loginWithTestUser(browser)

        browser.goTo(
          controllers.routes.Qa.index() + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaTitle"))

        browser.find("#comment").attribute("value") === ""
        browser.find("#firstName").attribute("value") === "Admin"
        browser.find("#lastName").attribute("value") === "Manager"
        browser.find("#tel").attribute("value") === ""
        browser.find("#email").attribute("value") === "admin@abc.com"
        browser.find("#companyName").attribute("value") === "Company1"
      }
    }

    "If address is available telephone number should be filled." in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user: StoreUser = loginWithTestUser(browser)
        val addr1 = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "firstName1",
          lastName = "lastName1",
          zip1 = "zip1",
          zip2 = "zip2",
          prefecture = JapanPrefecture.東京都,
          address1 = "address1-1",
          address2 = "address1-2",
          tel1 = "tel1-1",
          comment = "comment1"
        )
        val addr2 = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "firstName2",
          lastName = "lastName2",
          zip1 = "zip11",
          zip2 = "zip22",
          prefecture = JapanPrefecture.東京都,
          address1 = "address2-1",
          address2 = "address2-2",
          tel1 = "tel2-1",
          comment = "comment2"
        )
        UserAddress.createNew(user.id.get, addr2.id.get)

        browser.goTo(
          controllers.routes.Qa.index() + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaTitle"))

        browser.find("#comment").attribute("value") === ""
        browser.find("#firstName").attribute("value") === "Admin"
        browser.find("#lastName").attribute("value") === "Manager"
        browser.find("#tel").attribute("value") === "tel2-1"
        browser.find("#email").attribute("value") === "admin@abc.com"
        // Company information is taken from store_user record but address record.
        browser.find("#companyName").attribute("value") === "Company1"
      }
    }

    "Show error when nothing is entered" in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val optAuth = inject[NeedLogin.OptAuthenticated]

        if (optAuth.needAuthenticationEntirely) {
          loginWithTestUser(browser)
        }

        browser.goTo(
          controllers.routes.Qa.index() + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaTitle"))

        if (optAuth.needAuthenticationEntirely) {
          browser.find("#companyName").fill().`with`("")
          browser.find("#firstName").fill().`with`("")
          browser.find("#lastName").fill().`with`("")
          browser.find("#tel").fill().`with`("")
          browser.find("#email").fill().`with`("")
        }

        browser.find("#submitQa").click()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#qaType_field").find(".help-inline").text === Messages("error.required")
        browser.find("#comment_field").find(".help-inline").text === Messages("error.required")
        browser.find("#companyName_field").find(".help-inline").text === Messages("error.required")
        browser.find("#firstName_field").find(".help-inline").text === Messages("error.required")
        browser.find("#lastName_field").find(".help-inline").text === Messages("error.required")
        browser.find("#tel_field").find(".help-inline").text ===
          Messages("error.required") + ", " + Messages("error.number")
        browser.find("#email_field").find(".help-inline").text === Messages("error.required")
      }
    }

    "Enter invalid tel should result in error" in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val optAuth = inject[NeedLogin.OptAuthenticated]

        if (optAuth.needAuthenticationEntirely) {
          loginWithTestUser(browser)
        }
        browser.goTo(
          controllers.routes.Qa.index() + "?lang=" + lang.code
        )
        browser.find("#tel").fill().`with`("A")
        browser.find("#submitQa").click()
        browser.find("#tel_field").find(".help-inline").text === Messages("error.number")
      }
    }
  }
}
