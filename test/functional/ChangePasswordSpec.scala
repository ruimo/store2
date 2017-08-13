package functional

import play.api.test._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import com.ruimo.scoins.Scoping._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ChangePasswordSpec extends Specification with InjectorSupport {
  "Change password" should {
    "Be able to do validation." in new WithBrowser(
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

        val adminUser = loginWithTestUser(browser)

        browser.goTo(
          controllers.routes.UserEntry.changePasswordStart() + "?lang=" + lang.code
        )
        
        // Empty
        browser.find("#doResetPasswordButton").click()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#currentPassword_field .error").text === Messages("error.required")

        val passwordMinLength: Int = inject[constraints.FormConstraints].passwordMinLength()
        browser.find("#newPassword_main_field .error").text === Messages("error.minLength", passwordMinLength)
        browser.find("#newPassword_confirm_field .error").text === Messages("error.minLength", passwordMinLength)

        // Current password is wrong.
        browser.find("#currentPassword_field input[type='password']").fill().`with`("password0")
        browser.find("#newPassword_main_field input[type='password']").fill().`with`("password2")
        browser.find("#newPassword_confirm_field input[type='password']").fill().`with`("password2")
        browser.find("#doResetPasswordButton").click()
        browser.find("#currentPassword_field .error").text === Messages("currentPasswordNotMatch")

        // Password is not ASCII char.
        browser.find("#currentPassword_field input[type='password']").fill().`with`("password")
        browser.find("#newPassword_main_field input[type='password']").fill().`with`("あいうえおかきくけこ")
        browser.find("#newPassword_confirm_field input[type='password']").fill().`with`("あいうえおかきくけこ")
        browser.find("#doResetPasswordButton").click()
        browser.find("#newPassword_main_field .error").text === Messages("error.pasword.char")

        // Confirmation password does not match.
        browser.find("#currentPassword_field input[type='password']").fill().`with`("password")
        browser.find("#newPassword_main_field input[type='password']").fill().`with`("password2")
        browser.find("#newPassword_confirm_field input[type='password']").fill().`with`("password3")
        browser.find("#doResetPasswordButton").click()
        browser.find("#newPassword_confirm_field .error").text === Messages("confirmPasswordDoesNotMatch")

        val newPassword = "gH'(1hgf6"
        browser.find("#currentPassword_field input[type='password']").fill().`with`("password")
        browser.find("#newPassword_main_field input[type='password']").fill().`with`(newPassword)
        browser.find("#newPassword_confirm_field input[type='password']").fill().`with`(newPassword)
        browser.find("#doResetPasswordButton").click()

        browser.find(".message").text === Messages("passwordIsUpdated")
        
        // Check if new password is saved.
        inject[StoreUserRepo].apply(adminUser.id.get).passwordMatch(newPassword) === true
      }
    }
  }
}
