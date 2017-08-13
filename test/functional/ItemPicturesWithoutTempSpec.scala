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
import org.specs2.mutable.Specification

import helpers.Helper.downloadBytes
import play.api.http.Status
import play.api.test.Helpers
import play.api.test.Helpers.inMemoryDatabase
import play.api.test.Helpers.running
import play.api.test.TestServer
import java.nio.file.Files
import models._

class ItemPicturesWithoutTempSpec extends Specification with InjectorSupport {
  val dir = Files.createTempDirectory(null)
  lazy val withTempDir = Map(
    "item.picture.path" -> dir.toFile.getAbsolutePath,
    "item.picture.fortest" -> true
  )

  "ItemPicture" should {
    "If specified picture is not found, 'notfound.jpg' will be returned." in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ withTempDir)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        downloadBytes(
          Some(-1),
          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(1, 0).url
        )._1 === Status.OK
      }
    }
  }
}

