package functional

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import helpers.InjectorSupport
import play.api.db.Database
import anorm._
import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import play.api.i18n.{Lang, Messages}
import models._
import play.api.Play
import helpers.Helper._
import constraints.FormConstraints
import java.util.concurrent.TimeUnit

class CreateNewSuperUserSpec extends Specification with InjectorSupport {
  "CreateNewSuperUser" should {
    "Can create new user" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
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
        browser.goTo(controllers.routes.UserMaintenance.startCreateNewSuperUser.url + "?lang=" + lang.code)
        
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))
        browser.find("#userName").fill().`with`("username")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("companyname")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345678")
        browser.submit("#registerSuperUser")

        // Waiting next super user to create.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))
        browser.waitUntil(
          failFalse(browser.find(".message").text == Messages("userIsCreated"))
        )
        val user2 = inject[StoreUserRepo].findByUserName("username").get

        user2.deleted === false
        user2.email === "ruimo@ruimo.com"
        user2.firstName === "firstname"
        user2.lastName === "lastname"
        user2.userName === "username"
        user2.companyName === Some("companyname")
        user2.userRole === UserRole.ADMIN
      }
    }

    "Minimum length error." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
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
        browser.goTo(controllers.routes.UserMaintenance.startCreateNewSuperUser.url + "?lang=" + lang.code)

        browser.webDriver.getTitle === Messages("commonTitle",  Messages("createSuperUserTitle"))
        browser.find("#userName").fill().`with`("usern")
        browser.find("#firstName").fill().`with`("")
        browser.find("#lastName").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#password_main").fill().`with`("")
        browser.find("#password_confirm").fill().`with`("12345678")
        browser.find("#companyName").fill().`with`("")

        browser.submit("#registerSuperUser")
        // Waiting next super user to create.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))

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
      WebDriverFactory(FIREFOX), appl()
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
        browser.goTo(controllers.routes.UserMaintenance.startCreateNewSuperUser.url + "?lang=" + lang.code)

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))
        browser.find("#userName").fill().`with`("userName")
        browser.find("#firstName").fill().`with`("firstName")
        browser.find("#lastName").fill().`with`("lastName")
        browser.find("#email").fill().`with`("ruimo")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")

        browser.submit("#registerSuperUser")
        // Waiting next super user to create.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.$("#email_field dd.error").text === Messages("error.email")
      }
    }

    "Confirmation password does not match." in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() + ("maxCountOfSupplementalEmail" -> 0))
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
        browser.goTo(controllers.routes.UserMaintenance.startCreateNewSuperUser.url + "?lang=" + lang.code)

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))
        browser.find("#supplementalEmails_0_field").size === 0
        browser.find("#userName").fill().`with`("username")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("companyname")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345679")
        browser.submit("#registerSuperUser")

        // Waiting next super user to create.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createSuperUserTitle"))

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.$("#password_confirm_field dd.error").text === Messages("confirmPasswordDoesNotMatch")
      }
    }

    "Supplemental email fields should be shown." in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() + ("maxCountOfSupplementalEmail" -> 3))
    ) {
      // User name should be 6 digit string.
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)

        browser.goTo(controllers.routes.UserMaintenance.startCreateNewSuperUser.url + "?lang=" + lang.code)

        browser.find("#supplementalEmails_0_field").size === 1
        browser.find("#supplementalEmails_1_field").size === 1
        browser.find("#supplementalEmails_2_field").size === 1
        browser.find("#supplementalEmails_3_field").size === 0

        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("site01")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#userName").fill().`with`("123456")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")

        browser.find("#supplementalEmails_0").fill().`with`("null@ruimo.com")
        browser.find("#supplementalEmails_1").fill().`with`("aaa")

        browser.submit("#registerSuperUser")

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.find("#supplementalEmails_1_field dd.error").text === Messages("error.email")

        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")
        browser.find("#supplementalEmails_0").fill().`with`("null@ruimo.com")
        browser.find("#supplementalEmails_1").fill().`with`("foo@ruimo.com")

        browser.submit("#registerSuperUser")
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.waitUntil(
          failFalse(browser.find(".message").text == Messages("userIsCreated"))
        )

        val recs = SQL(
          """
          select * from supplemental_user_email s
          inner join store_user u on s.store_user_id = u.store_user_id
          where u.user_name = {userName}
          order by email
          """
        ).on(
          'userName -> "123456"
        ).as(
          SqlParser.str("supplemental_user_email.email") *
        )
        recs.size === 2
        recs(0) === "foo@ruimo.com"
        recs(1) === "null@ruimo.com"
      }
    }
  }
}
