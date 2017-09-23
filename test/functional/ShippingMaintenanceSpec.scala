package functional

import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.test.TestServer
import play.api.i18n.{Messages, Lang}
import models._
import org.openqa.selenium.By
import java.util.concurrent.TimeUnit
import play.api.test.TestServer
import com.ruimo.scoins.Scoping._

class ShippingMaintenanceSpec extends Specification with InjectorSupport {
  "Shipping fee maintenance" should {
    "Should occur validation error in creating shipping box" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ){
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        browser.goTo(
          controllers.routes.ShippingBoxMaintenance.startCreateShippingBox().url + "?lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewShippingBoxTitle"))
        browser.find("#createNewShippingBoxForm").find("input[type='submit']").click
        
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".globalErrorMessage")).displayed()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#itemClass_field").find("dd.error").text === Messages("error.number")
        browser.find("#boxSize_field").find("dd.error").text === Messages("error.number")
        browser.find("#boxName_field").find("dd.error").text === Messages("error.required")

        browser.find("#itemClass").fill().`with`("a")
        browser.find("#boxSize").fill().`with`("a")
        browser.find("#itemClass_field").find("dd.error").text === Messages("error.number")
        browser.find("#boxSize_field").find("dd.error").text === Messages("error.number")
      }
    }

    "Can create shipping box" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        browser.goTo(
          controllers.routes.ShippingBoxMaintenance.startCreateShippingBox().url + "?lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewShippingBoxTitle"))
        browser.webDriver
          .findElement(By.id("siteId"))
          .findElement(By.cssSelector("option[value=\"" + site1.id.get + "\"]")).getText() === "商店1"
        browser.webDriver
          .findElement(By.id("siteId"))
          .findElement(By.cssSelector("option[value=\"" + site2.id.get + "\"]")).getText() === "商店2"
        browser.find("#siteId").find("option[value=\"" + site2.id.get + "\"]").click()

        browser.find("#itemClass").fill().`with`("1")
        browser.find("#boxSize").fill().`with`("2")
        browser.find("#boxName").fill().`with`("BoxName")
        browser.find("#createNewShippingBoxForm").find("input[type='submit']").click

        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".message")).displayed()
        browser.find(".message").text === Messages("shippingBoxIsCreated")
        
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewShippingBoxTitle"))
        val list = inject[ShippingBoxRepo].list(site2.id.get)
        list.size === 1
        doWith(list(0)) { rec =>
          rec.siteId === site2.id.get
          rec.itemClass === 1
          rec.boxSize === 2
          rec.boxName === "BoxName"
        }

        // Creating with the same site and item class will cause duplicated error.
        browser.goTo(
          controllers.routes.ShippingBoxMaintenance.startCreateShippingBox().url + "?lang=" + lang.code
        )
        
        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewShippingBoxTitle"))
        browser.find("#siteId").find("option[value=\"" + site2.id.get + "\"]").click()

        browser.find("#itemClass").fill().`with`("1")
        browser.find("#boxSize").fill().`with`("3")
        browser.find("#boxName").fill().`with`("BoxName2")
        browser.find("#createNewShippingBoxForm").find("input[type='submit']").click

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#itemClass_field").find("dd.error").text === Messages("duplicatedItemClass")
      }
    }

    "Can edit without records" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        
        browser.goTo(
          controllers.routes.ShippingBoxMaintenance.editShippingBox().url + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("editShippingBoxTitle"))
        browser.find(".norecord").text === Messages("no.records.found")
      }
    }

    "Can edit with some records" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        val box2 = inject[ShippingBoxRepo].createNew(site1.id.get, 3, 4, "box2")
        val box3 = inject[ShippingBoxRepo].createNew(site2.id.get, 5, 6, "box3")
        
        browser.goTo(
          controllers.routes.ShippingBoxMaintenance.editShippingBox().url + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("editShippingBoxTitle"))
        browser.find(".shippingBoxTableBodyId").index(0).text === box1.id.get.toString
        browser.find(".shippingBoxTableBodySite").index(0).text === "商店1"
        browser.find(".shippingBoxTableBodyItemClass").index(0).text === "1"
        browser.find(".shippingBoxTableBodyBoxSize").index(0).text === "2"
        browser.find(".shippingBoxTableBodyBoxName").index(0).text === "box1"

        browser.find(".shippingBoxTableBodyId").index(1).text === box2.id.get.toString
        browser.find(".shippingBoxTableBodySite").index(1).text === "商店1"
        browser.find(".shippingBoxTableBodyItemClass").index(1).text === "3"
        browser.find(".shippingBoxTableBodyBoxSize").index(1).text === "4"
        browser.find(".shippingBoxTableBodyBoxName").index(1).text === "box2"

        browser.find(".shippingBoxTableBodyId").index(2).text === box3.id.get.toString
        browser.find(".shippingBoxTableBodySite").index(2).text === "商店2"
        browser.find(".shippingBoxTableBodyItemClass").index(2).text === "5"
        browser.find(".shippingBoxTableBodyBoxSize").index(2).text === "6"
        browser.find(".shippingBoxTableBodyBoxName").index(2).text === "box3"
      }
    }

    "Can edit one box record with validation error" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        
        browser.goTo(
          controllers.routes.ShippingBoxMaintenance.startChangeShippingBox(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("changeShippingBoxTitle"))
        browser.webDriver
          .findElement(By.id("siteId"))
          .findElement(By.cssSelector("option[value=\"" + site1.id.get + "\"]")).getText() === "商店1"

        browser.webDriver
          .findElement(By.id("siteId"))
          .findElement(By.cssSelector("option[value=\"" + site2.id.get + "\"]")).getText() === "商店2"

        browser.find("#itemClass").fill().`with`("")
        browser.find("#boxSize").fill().`with`("")
        browser.find("#boxName").fill().`with`("")

        browser.find("#changeShippingBoxForm").find("input[type='submit']").click

        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el("dd.error")).displayed()
        browser.find("#itemClass_field").find("dd.error").text === Messages("error.number")
        browser.find("#boxSize_field").find("dd.error").text === Messages("error.number")
        browser.find("#boxName_field").find("dd.error").text === Messages("error.required")

        browser.find("#itemClass").fill().`with`("100")
        browser.find("#boxSize").fill().`with`("200")
        browser.find("#boxName").fill().`with`("boxName2")

        browser.find("#changeShippingBoxForm").find("input[type='submit']").click
        browser.await().atMost(10, TimeUnit.SECONDS).until(browser.el(".title")).displayed()

        val box = inject[ShippingBoxRepo].apply(box1.id.get)
        box.itemClass === 100
        box.boxSize === 200
        box.boxName === "boxName2"
      }
    }

    "Can maintenance fee without records" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        browser.find("table.shippingFeeHeader").find(".body").find(".site").text === "商店1"
        browser.find("table.shippingFeeHeader").find(".body").find(".boxName").text === "box1"

        browser.find(".shippingFeeList").find(".body").size === 0
      }
    }

    "Can maintenance fee with records" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        val fee1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.北海道.code)
        val fee2 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        doWith(browser.find("table.shippingFeeHeader").find(".body")) { e =>
          e.find(".site").text === "商店1"
          e.find(".boxName").text === "box1"
        }

        doWith(browser.find(".shippingFeeList").find(".body").index(0)) { e =>
          e.find(".country").text === Messages("country.JPN")
          e.find(".prefecture").text === JapanPrefecture.北海道.toString
          e.find(".shippingFee").text === "-"
        }

        doWith(browser.find(".shippingFeeList").find(".body").index(1)) { e =>
          e.find(".country").text === Messages("country.JPN")
          e.find(".prefecture").text === JapanPrefecture.東京都.toString
          e.find(".shippingFee").text === "-"
        }
      }
    }

    "Can remove fee record" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        val fee1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.北海道.code)
        val fee2 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        browser.find(".shippingFeeList").find(".body").index(0).find(".delete").find("button").click

        // Dialog should be shown.
        browser.await().atMost(10, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset")).displayed()
        // Cancel
        browser.find(".ui-dialog-buttonset").find("button").index(1).click()
        browser.find(".shippingFeeList").find(".body").size === 2

        browser.find(".shippingFeeList").find(".body").index(0).find(".delete").find("button").click
        browser.await().atMost(10, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset")).displayed()
        // do removal
        browser.find(".ui-dialog-buttonset").find("button").index(0).click()
        browser.find(".shippingFeeList").find(".body").size === 1
      }
    }

    "Can create fee record" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el("#createShippingFeeEntryButton")).displayed()
        browser.find("#createShippingFeeEntryButton").click()
        
        // No prefectures are checked.
        browser.find("input:not(:checked)[type='checkbox']").size === JapanPrefecture.all.length

        // Check Tokyo and Kanagawa.
        browser.find("input[type='checkbox'][value='" + JapanPrefecture.東京都.code + "']").click()
        browser.find("input[type='checkbox'][value='" + JapanPrefecture.神奈川県.code + "']").click()
        browser.find("#createShippingFeeForm").find("input[type='submit']").click
        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))

        doWith(browser.find(".shippingFeeList").find(".body").index(0)) { e =>
          e.find(".country").text === Messages("country.JPN")
          e.find(".prefecture").text === JapanPrefecture.東京都.toString
          e.find(".shippingFee").text === "-"
        }

        doWith(browser.find(".shippingFeeList").find(".body").index(1)) { e =>
          e.find(".country").text === Messages("country.JPN")
          e.find(".prefecture").text === JapanPrefecture.神奈川県.toString
          e.find(".shippingFee").text === "-"
        }
      }
    }

    "Show validation error when adding fee" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        val fee1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.北海道.code)
        val fee2 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val tax1 = inject[TaxRepo].createNew
        val taxName1 = inject[TaxNameRepo].createNew(tax1, Ja, "tax01")
        val tax2 = inject[TaxRepo].createNew
        val taxName2 = inject[TaxNameRepo].createNew(tax2, Ja, "tax02")
        
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        // Edit fee for tokyo.
        browser.find(".shippingFeeList").find(".body").index(0).find(".edit").find("a").click

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        doWith(browser.find(".shippingFeeHistory").find(".body")) { rec =>
          rec.find(".boxName").text === "box1"
          rec.find(".country").text === "日本"
          rec.find(".prefecture").text === "北海道"
        }
        
        browser.find("#taxId").find("option").index(0).text === "tax01"
        browser.find("#taxId").find("option").index(1).text === "tax02"

        browser.find("#addShippingFeeHistoryButton").click()

        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el("#fee_field")).displayed()
        browser.find("#fee_field").find(".error").text === Messages("error.number")
        browser.find("#validUntil_field").find(".error").text === Messages("error.localDateTime")
        browser.find("#costFee_field").find(".error").size === 0

        browser.find("#costFee").fill().`with`("-1")
        browser.find("#addShippingFeeHistoryButton").click()

        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el("#fee_field")).displayed()
        browser.find("#costFee_field").find(".error").text === Messages("error.min", 0)
      }
    }

    "Can add, edit, delete fee" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")
        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1, 2, "box1")
        val fee1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.北海道.code)
        val fee2 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val tax1 = inject[TaxRepo].createNew
        val taxName1 = inject[TaxNameRepo].createNew(tax1, Ja, "tax01")
        val tax2 = inject[TaxRepo].createNew
        val taxName2 = inject[TaxNameRepo].createNew(tax2, Ja, "tax02")
        
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        // Edit fee for hokkaido.
        browser.find(".shippingFeeList").find(".body").index(0).find(".edit").find("a").click

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        doWith(browser.find(".shippingFeeHistory").find(".body")) { rec =>
          rec.find(".boxName").text === "box1"
          rec.find(".country").text === "日本"
          rec.find(".prefecture").text === "北海道"
        }
        
        val validDate = LocalDateTime.now().plus(10, ChronoUnit.DAYS)
        val formattedValidDate =
          DateTimeFormatter.ofPattern(Messages("yyyy-MM-dd hh:mm:ss")).format(validDate)

        // without cost fee.
        browser.find("#taxId").find("option").index(1).click()
        browser.find("#fee").fill().`with`("123")
        browser.find("#validUntil").fill().`with`(formattedValidDate)

        browser.find("#addShippingFeeHistoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        browser.webDriver.findElement(By.id("histories_0_taxId")).findElement(
          By.cssSelector("option[value='" + tax2.id.get + "']")
        ).isSelected === true
        browser.find("#histories_0_fee").attribute("value") === "123.00"
        browser.find("#histories_0_costFee").attribute("value") === ""
        browser.find("#histories_0_validUntil").attribute("value") === formattedValidDate

        // Remove history.
        browser.find(".removeHistoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()
        browser.find("#histories_0_fee").size === 0

        // with cost fee.
        browser.find("#taxId").find("option").index(1).click()
        browser.find("#fee").fill().`with`("123")
        browser.find("#costFee").fill().`with`("100")
        browser.find("#validUntil").fill().`with`(formattedValidDate)

        browser.find("#addShippingFeeHistoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        browser.webDriver.findElement(By.id("histories_0_taxId")).findElement(
          By.cssSelector("option[value='" + tax2.id.get + "']")
        ).isSelected === true
        browser.find("#histories_0_fee").attribute("value") === "123.00"
        browser.find("#histories_0_costFee").attribute("value") === "100.00"
        browser.find("#histories_0_validUntil").attribute("value") === formattedValidDate

        // Can change history.
        browser.find("#histories_0_taxId").find("option[value='" + tax1.id.get + "']").click()
        browser.find("#histories_0_fee").fill().`with`("234")
        browser.find("#histories_0_costFee").fill().`with`("")
        browser.find("#histories_0_validUntil").fill().`with`(formattedValidDate)
        browser.find("#updateShippingFeeHistoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        
        browser.webDriver.findElement(By.id("histories_0_taxId")).findElement(
          By.cssSelector("option[value='" + tax1.id.get + "']")
        ).isSelected === true
        browser.find("#histories_0_fee").attribute("value") === "234.00"
        browser.find("#histories_0_costFee").attribute("value") === ""
        browser.find("#histories_0_validUntil").attribute("value") === formattedValidDate

        browser.find("#histories_0_costFee").fill().`with`("100")
        browser.find("#updateShippingFeeHistoryButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        browser.find("#histories_0_costFee").attribute("value") === "100.00"

        // Check fee history.
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.startFeeMaintenanceNow(box1.id.get).url + "&lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeMaintenanceTitle"))
        doWith(browser.find(".shippingFeeList").find(".body").index(0)) { e =>
          e.find(".country").text === Messages("country.JPN")
          e.find(".prefecture").text === JapanPrefecture.北海道.toString
          e.find(".shippingFee").text === "234円"
        }

        // Delete fee history.
        browser.goTo(
          controllers.routes.ShippingFeeMaintenance.editHistory(fee1.id.get).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        browser.find("button.removeHistoryButton").size === 1
        browser.find("button.removeHistoryButton").click()

        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".title")).displayed()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("shippingFeeHistoryMaintenanceTitle"))
        browser.find("button.removeHistoryButton").size === 0
      }
    }
  }
}
