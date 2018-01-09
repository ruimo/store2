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
import helpers.UrlHelper
import helpers.UrlHelper._

class ModifyUserSpec extends Specification with InjectorSupport {
  "Modify user" should {
    "Can change password with password stretch count begin other than 1." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(
        inMemoryDatabase() + ("passwordHashStretchCount" -> 1000)
      )
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

        // change stretch count to 1000
        SQL(
          "update store_user set stretch_count = 1000 where store_user_id = {id}"
        ).on(
          'id -> user.id.get
        ).executeUpdate()

        browser.goTo(controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString)

        browser.waitUntil(
          browser.find("#password_main")
        ).fill().`with`("1qaz2wsx")
        browser.find("#password_confirm").fill().`with`("1qaz2wsx")
        browser.find("#modifyUser").click()

        logoff(browser)
        login(browser, user.userName, "1qaz2wsx")

        browser.waitUntil(
          webDriver.getTitle === Messages("commonTitle", Messages("adminTitle"))
        )
      }
    }
  }
}
