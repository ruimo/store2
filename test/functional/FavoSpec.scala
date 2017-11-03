package functional

import com.ruimo.scoins.Scoping._
import java.nio.file.Files
import com.ruimo.scoins.PathUtil
import controllers.FileServer
import helpers.UrlHelper
import helpers.UrlHelper._
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import java.util.concurrent.TimeUnit
import helpers.Helper.disableMailer
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class FavoSpec extends Specification with InjectorSupport {
  "Favo" should {
    "Can count by user." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
        val adminUser = loginWithTestUser(browser)
        val ctrl = inject[FileServer]
        val uploadedFileRepo = inject[UploadedFileRepo]

        val user1 = createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        val user2 = createNormalUser(
          browser, "22222222", "password02", "user02@mail.xxx", "firstName02", "lastName02", "company02"
        )

        logoff(browser)
        login(browser, "11111111", "password01")

        browser.goTo(
          controllers.routes.Favo.show(FavoKind.NEWS.ordinal, 0L).url.addParm("lang", lang.code).toString
        )

        doWith(browser.waitUntil(browser.find(".fav.nolike"))) { fav =>
          fav.find(".favCount").text === "0"
          fav.click()
        }
        doWith(browser.waitUntil(browser.find(".fav.like"))) { fav =>
          fav.find(".favCount").text === "1"
          fav.click()
        }
        doWith(browser.waitUntil(browser.find(".fav.nolike"))) { fav =>
          fav.find(".favCount").text === "0"
          fav.click()
        }

        logoff(browser)
        login(browser, "22222222", "password02")

        browser.goTo(
          controllers.routes.Favo.show(FavoKind.NEWS.ordinal, 0L).url.addParm("lang", lang.code).toString
        )
        doWith(browser.waitUntil(browser.find(".fav.nolike"))) { fav =>
          fav.find(".favCount").text === "1"
          fav.click()
        }
        doWith(browser.waitUntil(browser.find(".fav.like"))) { fav =>
          fav.find(".favCount").text === "2"
          fav.click()
        }
        doWith(browser.waitUntil(browser.find(".fav.nolike"))) { fav =>
          fav.find(".favCount").text === "1"
          fav.click()
        }

        browser.goTo(
          controllers.routes.Favo.show(FavoKind.NEWS.ordinal, 1L).url.addParm("lang", lang.code).toString
        )
        doWith(browser.waitUntil(browser.find(".fav.nolike"))) { fav =>
          fav.find(".favCount").text === "0"
          fav.click()
        }
        doWith(browser.waitUntil(browser.find(".fav.like"))) { fav =>
          fav.find(".favCount").text === "1"
        }

        browser.goTo(
          controllers.routes.Favo.show(FavoKind.NEWS.ordinal, 0L).url.addParm("lang", lang.code).toString
        )
        doWith(browser.waitUntil(browser.find(".fav.like"))) { fav =>
          fav.find(".favCount").text === "2"
        }
      }
    }
  }
}
