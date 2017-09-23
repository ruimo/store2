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
import anorm._
import helpers.PasswordHash
import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import play.api.i18n.{Lang, Messages}
import models._
import play.api.Play
import helpers.Helper._
import java.util.concurrent.TimeUnit

class CreateNewNormalUserSpec extends Specification with InjectorSupport {
  "CreateNewNormalUser" should {
    "Can create record" in new WithBrowser(
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

        browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser.url + "?lang=" + lang.code)
        
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))
        browser.find("#userName").fill().`with`("01234567")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("site01")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#lastName").fill().`with`("lastName")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")

        browser.submit("#registerNormalUser")
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        // Waiting next normal user to create.
        browser.waitUntil(
          failFalse(browser.find(".message").text == Messages("userIsCreated"))
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))
        
        val newUser = inject[StoreUserRepo].findByUserName("01234567").get
        newUser.userName === "01234567"
        newUser.firstName === "firstname"
        newUser.middleName === None
        newUser.lastName === "lastName"
        newUser.email === "ruimo@ruimo.com"
        newUser.passwordHash === PasswordHash.generate("password", newUser.salt)
        newUser.deleted === false
        newUser.userRole === UserRole.NORMAL
        newUser.companyName === Some("site01")
      }
    }

    "Email error" in new WithBrowser(
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
        browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser.url + "?lang=" + lang.code)
        
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))
        browser.find("#userName").fill().`with`("01234567")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("companyname")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#lastName").fill().`with`("lastName")
        browser.find("#email").fill().`with`("ruimo")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")

        browser.submit("#registerNormalUser")
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        // Waiting next normal user to create.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))

        browser.waitUntil(
          browser.$(".globalErrorMessage").text == Messages("inputError") &&
          failFalse(browser.$("#email_field dd.error").text == Messages("error.email"))
        )
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

        val user = loginWithTestUser(browser)
        browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser.url + "?lang=" + lang.code)

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))
        browser.find("#userName").fill().`with`("01234567")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("companyname")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345679")
        browser.submit("#registerNormalUser")

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        // Waiting next normal user to create.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))

        browser.waitUntil(
          browser.$(".globalErrorMessage").text == Messages("inputError") &&
          browser.$("#password_confirm_field dd.error").text == Messages("confirmPasswordDoesNotMatch")
        )
      }
    }

    "If normalUserNamePattern is set, user name should match the specified pattern." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() + ("normalUserNamePattern" -> "[0-9]{6}") + ("maxCountOfSupplementalEmail" -> 0))
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

        browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser.url + "?lang=" + lang.code)
        
        browser.find("#supplementalEmails_0_field").size === 0

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))
        browser.find("#userName").fill().`with`("abcdef")
        browser.find("#firstName").fill().`with`("firstname")
        browser.find("#lastName").fill().`with`("lastname")
        browser.find("#companyName").fill().`with`("site01")
        browser.find("#email").fill().`with`("ruimo@ruimo.com")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#lastName").fill().`with`("lastName")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")

        browser.submit("#registerNormalUser")
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // User name is not in pattern.
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))
        browser.await().atMost(10, TimeUnit.SECONDS).until(browser.el("#userName_field dd.error")).displayed()

        browser.find("#userName_field dd.error").text === Messages("normalUserNamePatternError")

        browser.find("#userName").fill().`with`("12345")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")
        browser.submit("#registerNormalUser")

        browser.waitUntil(
          failFalse(browser.find("#userName_field dd.error").text == Messages("normalUserNamePatternError"))
        )

        browser.find("#userName").fill().`with`("1234567")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")
        browser.submit("#registerNormalUser")

        browser.waitUntil(
          failFalse(browser.find("#userName_field dd.error").text == Messages("normalUserNamePatternError"))
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNormalUserTitle"))

        browser.find("#userName").fill().`with`("123456")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")
        browser.submit("#registerNormalUser")

        browser.waitUntil(
          browser.find(".message").text == Messages("userIsCreated")
        )
        failFalse(browser.webDriver.getTitle == Messages("commonTitle", Messages("createNormalUserTitle")))

        val newUser = inject[StoreUserRepo].findByUserName("123456").get
        newUser.userName === "123456"
        newUser.firstName === "firstname"
        newUser.middleName === None
        newUser.lastName === "lastName"
        newUser.email === "ruimo@ruimo.com"
        newUser.passwordHash === PasswordHash.generate("password", newUser.salt)
        newUser.deleted === false
        newUser.userRole === UserRole.NORMAL
        newUser.companyName === Some("site01")
      }
    }

    "Supplemental email fields should be shown." in new WithBrowser(
      WebDriverFactory(CHROME),
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

        browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser.url + "?lang=" + lang.code)

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

        browser.submit("#registerNormalUser")
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.waitUntil(
          failFalse(browser.$(".globalErrorMessage").text == Messages("inputError"))
        )
        browser.find("#supplementalEmails_1_field dd.error").text === Messages("error.email")

        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")
        browser.find("#supplementalEmails_0").fill().`with`("null@ruimo.com")
        browser.find("#supplementalEmails_1").fill().`with`("foo@ruimo.com")

        browser.submit("#registerNormalUser")
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
