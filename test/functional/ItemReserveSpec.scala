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
import java.util.concurrent.TimeUnit
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection
import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.test.TestServer
import com.ruimo.scoins.Scoping._
import helpers.Helper.disableMailer
import helpers.UrlHelper
import helpers.UrlHelper.fromString

class ItemReserveSpec extends Specification with InjectorSupport {
  "Item reservation" should {
    "Show reserve button" in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ disableMailer)
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
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item01 = inject[ItemRepo].createNew(cat)
        val siteItem01 = inject[SiteItemRepo].createNew(site, item01)
        val itemName01 = inject[ItemNameRepo].createNew(item01, Map(Ja -> "かえで"))
        val itemDesc01 = inject[ItemDescriptionRepo].createNew(item01, site, "かえで説明")
        val itemPrice01 = inject[ItemPriceRepo].createNew(item01, site)
        val itemPriceHistory01 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice01, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        val item02 = inject[ItemRepo].createNew(cat)
        val siteItem02 = inject[SiteItemRepo].createNew(site, item02)
        val itemName02 = inject[ItemNameRepo].createNew(item02, Map(Ja -> "もみじ"))
        val itemDesc02 = inject[ItemDescriptionRepo].createNew(item02, site, "もみじ説明")
        val itemPrice02 = inject[ItemPriceRepo].createNew(item02, site)
        val itemPriceHistory02 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice02, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item02.id.get, SiteItemNumericMetadataType.RESERVATION_ITEM, 1
        )
        
        browser.goTo(
          controllers.routes.ItemQuery.query(
            q = List(), orderBySpec = "item_name.item_name", templateNo = 0
          ).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.find(".queryItemTable")) { tbl =>
          doWith(tbl.find(".queryItemTableBody").index(0)) { tr =>
            tr.find(".queryItemItemName").text === "かえで"
            tr.find("button.addToCartButton").text === Messages("purchase")
          }

          doWith(tbl.find(".queryItemTableBody").index(1)) { tr =>
            tr.find(".queryItemItemName").text === "もみじ"
            doWith(tr.find("button.reserveButton")) { btn =>
              btn.text === Messages("itemReservation")
              btn.click()
            }
          }
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        doWith(browser.find(".itemReservation")) { tbl =>
          tbl.find(".siteName.body").text === site.name
          tbl.find(".itemName.body").text === "もみじ"
        }

        doWith(browser.find("#itemReservationForm")) { form =>
          form.find("#siteId").attribute("value") === site.id.get.toString
          form.find("#itemId").attribute("value") === item02.id.get.id.toString
          form.find("#name").attribute("value") === user.fullName
          form.find("#email").attribute("value") === user.email
        }

        browser.goTo(
          controllers.routes.ItemQuery.query(
            q = List(), orderBySpec = "item_name.item_name", templateNo = 1
          ).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.list"))
        doWith(browser.find(".queryItemTable")) { tbl =>
          doWith(tbl.find(".queryItemTableBody").index(0)) { tr =>
            tr.find(".queryItemItemName").attribute("textContent").trim === "かえで"
            tr.find("button.addToCartButton").attribute("textContent").trim === Messages("purchase")
          }

          doWith(tbl.find(".queryItemTableBody").index(1)) { tr =>
            tr.find(".queryItemItemName").attribute("textContent").trim === "もみじ"
            tr.find("button.reserveButton").attribute("textContent").trim === Messages("itemReservation")
          }
        }

        browser.goTo(
          controllers.routes.ItemDetail.show(item02.id.get.id, site.id.get).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.detail"))
        doWith(browser.find("button.reserveButton")) { btn =>
          btn.text === Messages("itemReservation")
          btn.click()
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        doWith(browser.find("#itemReservationForm")) { form =>
          form.find("#siteId").attribute("value") === site.id.get.toString
          form.find("#itemId").attribute("value") === item02.id.get.id.toString
          form.find("#name").attribute("value") === user.fullName
          form.find("#email").attribute("value") === user.email
        }

        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item02.id.get, SiteItemNumericMetadataType.ITEM_DETAIL_TEMPLATE, 1
        )
        browser.goTo(
          controllers.routes.ItemDetail.show(item02.id.get.id, site.id.get).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("item.detail"))
        browser.find("button.reserveButton").attribute("textContent").trim === Messages("itemReservation")
      }
    }

    "Can reserve item without comment" in new WithBrowser(
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

        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item01 = inject[ItemRepo].createNew(cat)
        val siteItem01 = inject[SiteItemRepo].createNew(site, item01)
        val itemName01 = inject[ItemNameRepo].createNew(item01, Map(Ja -> "かえで"))
        val itemDesc01 = inject[ItemDescriptionRepo].createNew(item01, site, "かえで説明")
        val itemPrice01 = inject[ItemPriceRepo].createNew(item01, site)
        val itemPriceHistory01 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice01, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item01.id.get, SiteItemNumericMetadataType.RESERVATION_ITEM, 1
        )
        browser.goTo(
          controllers.routes.ItemInquiryReserve.startItemReservation(
            site.id.get, item01.id.get.id
          ).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        doWith(browser.find(".itemReservation.siteItemContainer")) { tbl =>
          tbl.find(".siteName.body").text === site.name
          tbl.find(".itemName.body").text === "かえで"
        }
        doWith(browser.find("#itemReservationForm")) { form =>
          form.find("#siteId").attribute("value") === site.id.get.toString
          form.find("#itemId").attribute("value") === item01.id.get.id.toString
          form.find("#name").attribute("value") === user.fullName
          form.find("#email").attribute("value") === user.email
        }

        browser.find("#name").fill().`with`("")
        browser.find("#email").fill().`with`("")

        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        browser.find("#name_field dd.error").text === Messages("error.required")
        browser.find("#email_field dd.error").text === Messages("error.required")
        browser.find("#comment_field dd.error").size === 0

        // Reserve without comment
        browser.find("#name").fill().`with`("MyName")
        browser.find("#email").fill().`with`("email@xxx.xxx")

        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservationConfirm"))
        val rec: ItemInquiry = SQL("select * from item_inquiry").as(ItemInquiry.simple.single)

        doWith(browser.find("#submitItemReservationForm")) { form =>
          form.find("#id").attribute("value") === rec.id.get.id.toString

          doWith(form.find(".itemInquiry.confirmationTable")) { tbl =>
            tbl.find(".siteName.body").text === site.name
            tbl.find(".itemName.body").text === "かえで"
            tbl.find(".name.body").text === "MyName"
            tbl.find(".email.body").text === "email@xxx.xxx"
            tbl.find(".message.body").text === ""
          }
        }
        // amend entry
        browser.find("#amendItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        browser.find("#siteId").attribute("value") === site.id.get.toString
        browser.find("#itemId").attribute("value") === item01.id.get.id.toString
        browser.find("#name").attribute("value") === "MyName"
        browser.find("#email").attribute("value") === "email@xxx.xxx"
        browser.find("#comment").text === ""
        
        // Confirm error
        browser.find("#name").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#name_field dd.error").text === Messages("error.required")
        browser.find("#email_field dd.error").text === Messages("error.required")
        browser.find("#comment_field dd.error").size === 0

        browser.find("#name").fill().`with`("MyName2")
        browser.find("#email").fill().`with`("email2@xxx.xxx")
        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#id").attribute("value") === rec.id.get.id.toString
        browser.find(".confirmationTable .siteName.body").text === site.name
        browser.find(".confirmationTable .itemName.body").text === "かえで"
        browser.find(".confirmationTable .name.body").text === "MyName2"
        browser.find(".confirmationTable .email.body").text === "email2@xxx.xxx"
        browser.find(".confirmationTable .message.body").text === ""
        
        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("company.name"))

        doWith(SQL("select * from item_inquiry").as(ItemInquiry.simple.single)) { inq =>
          inq.id === rec.id
          inq.siteId === site.id.get
          inq.itemId === item01.id.get
          inq.storeUserId === user.id.get
          inq.inquiryType === ItemInquiryType.RESERVATION
          inq.submitUserName === "MyName2"
          inq.email === "email2@xxx.xxx"
          inq.status === ItemInquiryStatus.SUBMITTED

          doWith(ItemInquiryField(inq.id.get)) { fields =>
            fields.size === 1
            fields('Message) === ""
          }
        }
      }
    }

    "Can reserve item with comment" in new WithBrowser(
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

        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item01 = inject[ItemRepo].createNew(cat)
        val siteItem01 = inject[SiteItemRepo].createNew(site, item01)
        val itemName01 = inject[ItemNameRepo].createNew(item01, Map(Ja -> "かえで"))
        val itemDesc01 = inject[ItemDescriptionRepo].createNew(item01, site, "かえで説明")
        val itemPrice01 = inject[ItemPriceRepo].createNew(item01, site)
        val itemPriceHistory01 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice01, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item01.id.get, SiteItemNumericMetadataType.RESERVATION_ITEM, 1
        )
        browser.goTo(
          controllers.routes.ItemInquiryReserve.startItemReservation(
            site.id.get, item01.id.get.id
          ).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        doWith(browser.find(".itemReservation.siteItemContainer")) { tbl =>
          tbl.find(".siteName.body").text === site.name
          tbl.find(".itemName.body").text === "かえで"
        }
        doWith(browser.find("#itemReservationForm")) { form =>
          form.find("#siteId").attribute("value") === site.id.get.toString
          form.find("#itemId").attribute("value") === item01.id.get.id.toString
          form.find("#name").attribute("value") === user.fullName
          form.find("#email").attribute("value") === user.email
        }

        browser.find("#name").fill().`with`("")
        browser.find("#email").fill().`with`("")

        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        browser.find("#name_field dd.error").text === Messages("error.required")
        browser.find("#email_field dd.error").text === Messages("error.required")
        browser.find("#comment_field dd.error").size === 0

        // Reserve with comment
        browser.find("#name").fill().`with`("MyName")
        browser.find("#email").fill().`with`("email@xxx.xxx")
        browser.find("#comment").fill().`with`("Comment")

        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservationConfirm"))
        val rec: ItemInquiry = SQL("select * from item_inquiry").as(ItemInquiry.simple.single)

        doWith(browser.find("#submitItemReservationForm")) { form =>
          form.find("#id").attribute("value") === rec.id.get.id.toString

          doWith(form.find(".itemInquiry.confirmationTable")) { tbl =>
            tbl.find(".siteName.body").text === site.name
            tbl.find(".itemName.body").text === "かえで"
            tbl.find(".name.body").text === "MyName"
            tbl.find(".email.body").text === "email@xxx.xxx"
            tbl.find(".message.body").text === "Comment"
          }
        }

        // amend entry
        browser.find("#amendItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemReservation"))
        browser.find("#siteId").attribute("value") === site.id.get.toString
        browser.find("#itemId").attribute("value") === item01.id.get.id.toString
        browser.find("#name").attribute("value") === "MyName"
        browser.find("#email").attribute("value") === "email@xxx.xxx"
        browser.find("#comment").text === "Comment"
        
        // Confirm error
        browser.find("#name").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#name_field dd.error").text === Messages("error.required")
        browser.find("#email_field dd.error").text === Messages("error.required")
        browser.find("#comment_field dd.error").size === 0

        browser.find("#name").fill().`with`("MyName2")
        browser.find("#email").fill().`with`("email2@xxx.xxx")
        browser.find("#comment").fill().`with`("Comment2")
        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#id").attribute("value") === rec.id.get.id.toString
        browser.find(".confirmationTable .siteName.body").text === site.name
        browser.find(".confirmationTable .itemName.body").text === "かえで"
        browser.find(".confirmationTable .name.body").text === "MyName2"
        browser.find(".confirmationTable .email.body").text === "email2@xxx.xxx"
        browser.find(".confirmationTable .message.body").text === "Comment2"
        
        browser.find("#submitItemReservation").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("company.name"))

        doWith(SQL("select * from item_inquiry").as(ItemInquiry.simple.single)) { inq =>
          inq.id === rec.id
          inq.siteId === site.id.get
          inq.itemId === item01.id.get
          inq.storeUserId === user.id.get
          inq.inquiryType === ItemInquiryType.RESERVATION
          inq.submitUserName === "MyName2"
          inq.email === "email2@xxx.xxx"
          inq.status === ItemInquiryStatus.SUBMITTED

          doWith(ItemInquiryField(inq.id.get)) { fields =>
            fields.size === 1
            fields('Message) === "Comment2"
          }
        }
      }
    }
  }
}
