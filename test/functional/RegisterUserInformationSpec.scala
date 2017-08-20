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
import helpers.PasswordHash
import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import anorm._
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import scala.collection.JavaConversions._
import com.ruimo.scoins.Scoping._
import constraints.FormConstraints

class RegisterUserInformationSpec extends Specification with InjectorSupport {
  "User information registration" should {
    "Show error message for blank input" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        // Create tentative user(first name is blank).
        SQL("""
          insert into store_user (
            store_user_id, user_name, first_name, middle_name, last_name,
            email, password_hash, salt, deleted, user_role, company_name
          ) values (
            1, '002-Uno', '', null, '',
            '', 6442108903620542185, -3926372532362629068,
            FALSE, """ + UserRole.NORMAL.ordinal + """, null
          )""").executeUpdate()

        // Though we are not loged-in, the screen of register user information will be shown.
        browser.goTo(
          controllers.routes.UserEntry.registerUserInformation(1L) + "&lang=" + lang.code
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("registerUserInformation"))
        
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#password_main_field .help-inline").text ===
        Messages("error.minLength", inject[FormConstraints].passwordMinLength())
        browser.find("#firstName_field .help-inline").text === Messages("error.required")
        browser.find("#lastName_field .help-inline").text === Messages("error.required")
        browser.find("#firstNameKana_field .help-inline").text === Messages("error.required")
        browser.find("#lastNameKana_field .help-inline").text === Messages("error.required")
        browser.find("#email_field .help-inline").text === Messages("error.email") + ", " + Messages("error.required")
        browser.find("#zip_field .help-inline").text === Messages("zipError")
        browser.find("#address1_field .help-inline").text === Messages("error.required")
        browser.find("#address2_field .help-inline").text === Messages("error.required")
        browser.find("#address3_field .help-inline").text === ""
        browser.find("#tel1_field .help-inline").text === Messages("error.required") + ", " + Messages("error.number")

        val password = "1" * (inject[FormConstraints].passwordMinLength() - 1)
        browser.find("#password_main").fill().`with`(password)
        browser.find("#password_confirm").fill().`with`(password)
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_main_field .help-inline").text ===
        Messages("error.minLength", inject[FormConstraints].passwordMinLength())

        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345679")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_confirm_field .help-inline").text === Messages("confirmPasswordDoesNotMatch")

        // current password does not match
        browser.find("#currentPassword").fill().`with`("foobar")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345678")
        browser.find("#firstName").fill().`with`("first name")
        browser.find("#lastName").fill().`with`("last name")
        browser.find("#firstNameKana").fill().`with`("first name kana")
        browser.find("#lastNameKana").fill().`with`("last name kana")
        browser.find("#email").fill().`with`("null@ruimo.com")
        browser.find("#zip_field input[name='zip1']").fill().`with`("146")
        browser.find("#zip_field input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address 1")
        browser.find("#address2").fill().`with`("address 2")
        browser.find("#tel1").fill().`with`("11111111")

        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#currentPassword_field .help-inline").text === Messages("currentPasswordNotMatch")

        // new password(confirm) does not match
        browser.find("#currentPassword").fill().`with`("Uno")
        browser.find("#password_main").fill().`with`("12345678")
        browser.find("#password_confirm").fill().`with`("12345679")

        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_confirm_field .help-inline").text === Messages("confirmPasswordDoesNotMatch")

        // new password is too easy.
        // Add naive password to dictionary.
        SQL("""
          insert into password_dict (password) values ('password')
        """).executeUpdate()
        browser.find("#currentPassword").fill().`with`("Uno")
        browser.find("#password_main").fill().`with`("password")
        browser.find("#password_confirm").fill().`with`("password")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#password_main_field .help-inline").text === Messages("naivePassword")
        
        // User information should be registered.

        browser.find("#currentPassword").fill().`with`("Uno")
        browser.find("#password_main").fill().`with`("passwor0")
        browser.find("#password_confirm").fill().`with`("passwor0")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(inject[StoreUserRepo].findByUserName("002-Uno").get) { u =>
          u.userName === "002-Uno"
          u.firstName === "first name"
          u.middleName === None
          u.lastName === "last name"
          u.email === "null@ruimo.com"
          u.passwordHash === PasswordHash.generate("passwor0", u.salt)
          u.salt === -3926372532362629068L
          u.deleted === false
          u.userRole === UserRole.NORMAL
          u.companyName === None
        }
      }
    }

    "Login page should be shown after registration" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(
        inMemoryDatabase() +
          ("need.authentication.entirely" -> "true") +
          ("auto.login.after.registration" -> "false")
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        // Create tentative user(first name is blank).
        SQL("""
          insert into store_user (
            store_user_id, user_name, first_name, middle_name, last_name,
            email, password_hash, salt, deleted, user_role, company_name
          ) values (
            1, '002-Uno', '', null, '',
            '', 6442108903620542185, -3926372532362629068,
            FALSE, """ + UserRole.NORMAL.ordinal + """, null
          )""").executeUpdate()

        // Though we are not loged-in, the screen of register user information will be shown.
        browser.goTo(
          controllers.routes.UserEntry.registerUserInformation(1L) + "&lang=" + lang.code
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("registerUserInformation"))

        // User information should be registered.
        browser.find("#currentPassword").fill().`with`("Uno")
        browser.find("#password_main").fill().`with`("passwor0")
        browser.find("#password_confirm").fill().`with`("passwor0")
        browser.find("#firstName").fill().`with`("first name")
        browser.find("#lastName").fill().`with`("last name")
        browser.find("#firstNameKana").fill().`with`("first name kana")
        browser.find("#lastNameKana").fill().`with`("last name kana")
        browser.find("#email").fill().`with`("null@ruimo.com")
        browser.find("#zip_field input[name='zip1']").fill().`with`("146")
        browser.find("#zip_field input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address 1")
        browser.find("#address2").fill().`with`("address 2")
        browser.find("#tel1").fill().`with`("11111111")

        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("login"))
        doWith(inject[StoreUserRepo].findByUserName("002-Uno").get) { u =>
          u.userName === "002-Uno"
          u.firstName === "first name"
          u.middleName === None
          u.lastName === "last name"
          u.email === "null@ruimo.com"
          u.passwordHash === PasswordHash.generate("passwor0", u.salt)
          u.salt === -3926372532362629068L
          u.deleted === false
          u.userRole === UserRole.NORMAL
          u.companyName === None
        }
      }
    }

    "Should be automatically logged in after registration" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(
        inMemoryDatabase() +
          ("need.authentication.entirely" -> "true") +
          ("auto.login.after.registration" -> "true")
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        // Create tentative user(first name is blank).
        SQL("""
          insert into store_user (
            store_user_id, user_name, first_name, middle_name, last_name,
            email, password_hash, salt, deleted, user_role, company_name
          ) values (
            1, '002-Uno', '', null, '',
            '', 6442108903620542185, -3926372532362629068,
            FALSE, """ + UserRole.NORMAL.ordinal + """, null
          )""").executeUpdate()

        // Though we are not loged-in, the screen of register user information will be shown.
        browser.goTo(
          controllers.routes.UserEntry.registerUserInformation(1L) + "&lang=" + lang.code
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("registerUserInformation"))

        // User information should be registered.
        browser.find("#currentPassword").fill().`with`("Uno")
        browser.find("#password_main").fill().`with`("passwor0")
        browser.find("#password_confirm").fill().`with`("passwor0")
        browser.find("#firstName").fill().`with`("first name")
        browser.find("#lastName").fill().`with`("last name")
        browser.find("#firstNameKana").fill().`with`("first name kana")
        browser.find("#lastNameKana").fill().`with`("last name kana")
        browser.find("#email").fill().`with`("null@ruimo.com")
        browser.find("#zip_field input[name='zip1']").fill().`with`("146")
        browser.find("#zip_field input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address 1")
        browser.find("#address2").fill().`with`("address 2")
        browser.find("#tel1").fill().`with`("11111111")
        browser.find(".submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // Should be automatically logged in.
        browser.webDriver.getTitle !== Messages("commonTitle", Messages("login"))
        doWith(inject[StoreUserRepo].findByUserName("002-Uno").get) { u =>
          u.userName === "002-Uno"
          u.firstName === "first name"
          u.middleName === None
          u.lastName === "last name"
          u.email === "null@ruimo.com"
          u.passwordHash === PasswordHash.generate("passwor0", u.salt)
          u.salt === -3926372532362629068L
          u.deleted === false
          u.userRole === UserRole.NORMAL
          u.companyName === None
        }
      }
    }
  }
}
