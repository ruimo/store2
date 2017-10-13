package functional

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

class FileServerSpec extends Specification with InjectorSupport {
  "File Server" should {
    "Works if no files are found." in new WithBrowser(
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

        browser.goTo(
          controllers.routes.FileServer.index().url.addParm("lang", lang.code).toString
        )
        val storedFilesIframe = browser.waitUntil(browser.find("#storedFiles"))
        browser.switchTo(storedFilesIframe)
        browser.find(".fileList .noRecords").text === Messages("recordEmpty")

        PathUtil.withTempFile(None, None) { tmp =>
          Files.write(tmp, Array[Byte](1, 2, 3))
          val ufid = uploadedFileRepo.create(
            adminUser.id.get, "MyFile00", None, Instant.ofEpochMilli(444L), "category00", None
          )
          ctrl.storeAttachment(tmp, ufid)

          browser.goTo(
            controllers.routes.FileServer.index(categoryName = "category00").url.addParm("lang", lang.code).toString
          )
          val storedFilesIframe = browser.waitUntil(browser.find("#storedFiles"))
          browser.switchTo(storedFilesIframe)
          browser.find(".fileList .row").size === 1
          browser.find(".fileList .row .fileName").text === "MyFile00"
          adminUser.fullName === browser.find(".fileList .row .createdUser").text
          browser.find(".fileList .row .link #url" + ufid.value).text === controllers.routes.FileServer.getFile(ufid.value).url

          browser.find(".fileList .row .fileName a").attribute("href").endsWith(
            controllers.routes.FileServer.getFile(ufid.value).url
          ) === true

          // create directory
          browser.find("#path").fill().`with`("abc") 
          browser.find("#createDirBtn").click()

          browser.waitUntil {
            failFalse(browser.find(".fileList .row").size == 2)
          }
          browser.find(".fileList .row .fileName").index(0).text === "/abc"
          browser.find(".fileList .row .fileName").index(1).text === "MyFile00"

          // dig into directory
          browser.find(".fileList .row .fileName a").index(0).click()

          browser.switchTo(browser.waitUntil(browser.find("#storedFiles")))
          browser.find(".fileListHeader .currentDirectory").index(0).text === "/abc"
          browser.find(".fileList .noRecords").text === Messages("recordEmpty")
          browser.find("#gotoParentDirBtn").click()

          browser.switchTo(browser.waitUntil(browser.find("#storedFiles")))
          browser.find(".fileListHeader .currentDirectory").index(0).text === "/"

          // With "category01", there should no file or directory exist.
          browser.goTo(
            controllers.routes.FileServer.index(categoryName = "category01").url.addParm("lang", lang.code).toString
          )
          browser.switchTo(browser.waitUntil(browser.find("#storedFiles")))
          browser.find(".fileList .noRecords").text === Messages("recordEmpty")
        }.get
      }
    }
  }
}
