package functional

import com.ruimo.scoins.PathUtil
import java.io.FileNotFoundException
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

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.test.TestServer
import controllers.ItemPictures
import java.nio.file.{Paths, Files}
import java.util
import java.nio.charset.Charset
import java.net.{HttpURLConnection, URL}
import java.text.SimpleDateFormat
import play.api.http.Status
import org.openqa.selenium.By
import play.api.test.TestServer

class ItemPicturesSpec extends Specification with InjectorSupport {
  val testDir = Files.createTempDirectory(null)
  lazy val withTempDir = Map(
    "item.picture.path" -> testDir.toFile.getAbsolutePath,
    "item.picture.fortest" -> true
  )
  lazy val avoidLogin = Map(
    "need.authentication.entirely" -> false
  )

  "ItemPicture" should {
    "If specified picture is not found, 'notfound.jpg' will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val file = testDir.resolve("notfound.jpg")
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))

        downloadString(
          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(1, 0).url
        )._2 === "Hello"

        Files.deleteIfExists(file)
      }
    }

    "If specified detail picture is not found, 'detailnotfound.jpg' will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val file = testDir.resolve("detailnotfound.jpg")
        Files.deleteIfExists(file)
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))

        downloadString(
          browser.baseUrl.get + controllers.routes.ItemPictures.getDetailPicture(1).url
        )._2 === "Hello"

        Files.deleteIfExists(file)
      }
    }

    "If specified picture is found and modified, it will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val file = testDir.resolve("2_1.jpg")
        Files.deleteIfExists(file)
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))

        downloadString(
          Some(file.toFile.lastModified - 1000),
          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(2, 1).url
        )._2 === "Hello"

        Files.deleteIfExists(file)
      }
    }

    "If specified detail picture is found and modified, it will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val file = testDir.resolve("detail2.jpg")
        Files.deleteIfExists(file)
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))

        downloadString(
          Some(file.toFile.lastModified - 1000),
          browser.baseUrl.get + controllers.routes.ItemPictures.getDetailPicture(2).url
        )._2 === "Hello"

        Files.deleteIfExists(file)
      }
    }

    "If specified picture is found but not modified, 304 will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val file = testDir.resolve("2_1.jpg")
        Files.deleteIfExists(file)
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))

        downloadString(
          Some(file.toFile.lastModified),
          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(2, 1).url
        )._1 === Status.NOT_MODIFIED

        downloadString(
          Some(file.toFile.lastModified + 1000),
          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(2, 1).url
        )._1 === Status.NOT_MODIFIED

        Files.deleteIfExists(file)
      }
    }

    "If specified detail picture is found but not modified, 304 will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val file = testDir.resolve("detail2.jpg")
        Files.deleteIfExists(file)
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))

        downloadString(
          Some(file.toFile.lastModified),
          browser.baseUrl.get + controllers.routes.ItemPictures.getDetailPicture(2).url
        )._1 === Status.NOT_MODIFIED

        downloadString(
          Some(file.toFile.lastModified + 1000),
          browser.baseUrl.get + controllers.routes.ItemPictures.getDetailPicture(2).url
        )._1 === Status.NOT_MODIFIED

        Files.deleteIfExists(file)
      }
    }

// Bug https://bugzilla.mozilla.org/show_bug.cgi?id=1361329
//    "Upload item picture." in new WithBrowser(
//      WebDriverFactory(CHROME),
//      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
//    ) {
//      inject[Database].withConnection { implicit conn =>
//        val currencyInfo = inject[CurrencyRegistry]
//        val localeInfo = inject[LocaleInfoRepo]
//        import localeInfo.{En, Ja}
//        implicit val lang = Lang("ja")
//        implicit val storeUserRepo = inject[StoreUserRepo]
//        val Messages = inject[MessagesApi]
//        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
//
//        val user = loginWithTestUser(browser)
//        val site = inject[SiteRepo].createNew(Ja, "Store01")
//
//        val user01 = inject[StoreUserRepo].create(
//          "user01", "Admin", None, "Manager", "admin@abc.com",
//          4151208325021896473L, -1106301469931443100L, UserRole.NORMAL, Some("Company1")
//        )
//        val siteOwner = inject[SiteUserRepo].createNew(user01.id.get, site.id.get)
//        logoff(browser)
//        login(browser, "user01", "password")
//
//        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
//        val tax = inject[TaxRepo].createNew
//        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "tax01")
//        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
//        val item = inject[ItemRepo].createNew(cat)
//
//        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "ItemName01"))
//        val itemDescription = inject[ItemDescriptionRepo].createNew(item, site, "ItemDescription01")
//
//        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
//        val itemPriceHis = inject[ItemPriceHistoryRepo].createNew(
//          itemPrice, tax, currencyInfo.Jpy, BigDecimal("123"), None, BigDecimal("234"), date("9999-12-31")
//        )
//
//        val file = testDir.resolve("notfound.jpg")
//        Files.deleteIfExists(file)
//        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))
//
//        // Since no item pictures found, notfound.jpg will be obtained.
//        downloadString(
//          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(item.id.get.id, 0).url
//        )._2 === "Hello"
//
//        // Set timestamp of 'notfound.jpg' to very old.
//        file.toFile.setLastModified(date("1990-01-01").toEpochMilli)
//
//        // Of course, the file is not modified.
//        downloadString(
//          Some(date("2013-01-01").toEpochMilli),
//          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(item.id.get.id, 0).url
//        )._1 === Status.NOT_MODIFIED
//
//        // Now upload new file.
//        browser.goTo(
//          controllers.routes.ItemMaintenance.startChangeItem(item.id.get.id).url +
//          "&lang=" + lang.code
//        )
//        val p = Paths.get("testdata/kinseimaruIdx.jpg").toAbsolutePath.toString
//        browser.webDriver
//          .findElement(By.id("itemPictureUpload0"))
//          .sendKeys(p)
//        val now = System.currentTimeMillis
//        browser.find("#itemPictureUploadSubmit0").click()
//
//        testDir.resolve(item.id.get.id + "_0.jpg").toFile.exists === true
//
//        // Download file.
//        downloadBytes(
//          Some(now - 1000),
//          controllers.routes.ItemPictures.getPicture(item.id.get.id, 0).url
//        )._1 === Status.OK
//        
//        downloadBytes(
//          Some(now + 5000),
//          controllers.routes.ItemPictures.getPicture(item.id.get.id, 0).url
//        )._1 === Status.NOT_MODIFIED
//
//        // Delete file.
//        browser.find("#itemPictureRemove0").click()
//
//        testDir.resolve(item.id.get.id + "_0.jpg").toFile.exists === false
//        
//        // Download file. 'notfound.jpg' should be obtained.
//        // 200 should be returned. Otherwise, browser cache will not be refreshed!
//        val str = downloadString(
//          Some(now - 1000),
//          browser.baseUrl.get + controllers.routes.ItemPictures.getPicture(item.id.get.id, 0).url
//        )
//        str._1 === Status.OK
//        str._2 === "Hello"
//      }
//    }

    "If specified attachment is not found, 404 will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        downloadString(
          browser.baseUrl.get + controllers.routes.ItemPictures.getAttachment(1, 2, "foo").url
        ) must throwA[FileNotFoundException]
      }
    }

    "If specified attachment is found and modified, it will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        Files.createDirectories(testDir.resolve("attachments"))
        val file = testDir.resolve("attachments").resolve("1_2_file.jpg")
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))
        downloadString(
          Some(file.toFile.lastModified - 1000),
          browser.baseUrl.get + controllers.routes.ItemPictures.getAttachment(1, 2, "file.jpg").url
        )._2 === "Hello"

        Files.deleteIfExists(file)
      }
    }

    "If specified attachment is found but not modified, 304 will be returned." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        Files.createDirectories(testDir.resolve("attachments"))
        val file = testDir.resolve("attachments").resolve("1_2_file.jpg")
        Files.write(file, util.Arrays.asList("Hello"), Charset.forName("US-ASCII"))
        downloadString(
          Some(file.toFile.lastModified),
          browser.baseUrl.get + controllers.routes.ItemPictures.getAttachment(1, 2, "file.jpg").url
        )._1 === Status.NOT_MODIFIED

        downloadString(
          Some(file.toFile.lastModified + 1000),
          browser.baseUrl.get + controllers.routes.ItemPictures.getAttachment(1, 2, "file.jpg").url
        )._1 === Status.NOT_MODIFIED

        Files.deleteIfExists(file)
      }
    }

    "Attachment count reflects item.attached.file.count settings." in new WithBrowser(
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

        inject[ItemPictures].attachmentCount === 5
      }
    }

    "retrieveAttachmentNames returns empty if no files are found." in new WithBrowser(
      WebDriverFactory(CHROME),
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

        if (Files.exists(testDir.resolve("attachments"))) {
          PathUtil.deleteDir(testDir.resolve("attachments"))
        }
        Files.createDirectories(testDir.resolve("attachments"))
        inject[ItemPictures].retrieveAttachmentNames(1).isEmpty === true
      }
    }

    "retrieveAttachmentNames returns file names." in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase() ++ withTempDir)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val attachmentDir = testDir.resolve("attachments")
        Files.createDirectories(attachmentDir)
        Files.write(
          attachmentDir.resolve("1_2_file1.jpg"), util.Arrays.asList("000"), Charset.forName("US-ASCII")
        )
        Files.write(
          attachmentDir.resolve("2_2_file2.mp3"), util.Arrays.asList("111"), Charset.forName("US-ASCII")
        )
        Files.write(
          attachmentDir.resolve("1_3_file3.ogg"), util.Arrays.asList("222"), Charset.forName("US-ASCII")
        )
        
        val map = inject[ItemPictures].retrieveAttachmentNames(1)
        map.size === 2
        map(2) === "file1.jpg"
        map(3) === "file3.ogg"

        Files.deleteIfExists(attachmentDir.resolve("1_2_file1.jpg"))
        Files.deleteIfExists(attachmentDir.resolve("2_2_file2.mp3"))
        Files.deleteIfExists(attachmentDir.resolve("1_3_file3.ogg"))
      }
    }
  }
}

