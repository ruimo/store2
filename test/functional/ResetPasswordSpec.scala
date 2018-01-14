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
import java.sql.Connection
import java.util.concurrent.TimeUnit

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import play.api.test.TestServer
import helpers.Helper.disableMailer
import helpers.{PasswordHash, TokenGenerator, RandomTokenGenerator}
import models._
import controllers.NeedLogin

class ResetPasswordSpec extends Specification with InjectorSupport {
  val conf = inMemoryDatabase() ++ disableMailer

  "Reset password" should {
    "Can reset password" in new WithBrowser(
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

        val salt = RandomTokenGenerator().next
        val hash = PasswordHash.generate("password", salt)

        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          hash, salt, UserRole.NORMAL, Some("companyName")
        )

        browser.goTo(
          controllers.routes.UserEntry.resetPasswordStart() + "?lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("resetPassword"))

        // Empty input
        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#userName_field dd.error").text === Messages("error.required")

        // Wrong user name
        browser.find("#userName").fill().`with`("userName2")
        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#userName_field dd.error").text === Messages("error.value")

        val now = System.currentTimeMillis
        browser.find("#userName").fill().`with`("userName")
        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val now2 = System.currentTimeMillis
        browser.webDriver.getTitle === Messages("commonTitle", Messages("resetPasswordMailSent"))
        val rec = inject[ResetPasswordRepo].getByStoreUserId(user.id.get).get
        (now <= rec.resetTime.toEpochMilli() && rec.resetTime.toEpochMilli() <= now2) === true
        
        browser.goTo(
          controllers.routes.UserEntry.resetPasswordConfirm(user.id.get, rec.token) + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("resetPassword"))
        browser.find("input[name='userId']").attribute("value") === user.id.get.toString
        browser.find("input[name='token']").attribute("value") === rec.token.toString

        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#password_main_field .error").text === 
          Messages("error.minLength", inject[constraints.FormConstraints].passwordMinLength().toString)
        browser.find("#password_confirm_field .error").text === 
          Messages("error.minLength", inject[constraints.FormConstraints].passwordMinLength().toString)

        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_confirm_field .error").text === 
          Messages("error.minLength", inject[constraints.FormConstraints].passwordMinLength().toString)

        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345679")
        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#password_confirm_field .error").text === Messages("confirmPasswordDoesNotMatch")

        browser.find("#password_main").fill().`with`("1q2w3e4r")
        browser.find("#password_confirm").fill().`with`("1q2w3e4r")
        browser.find("#doResetPasswordButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("passwordIsUpdated"))
        val newUser = inject[StoreUserRepo].apply(user.id.get)
        newUser.passwordHash === PasswordHash.generate("1q2w3e4r", newUser.salt, inject[StoreUserRepo].PasswordHashStretchCount())
      }
    }
  }
}
