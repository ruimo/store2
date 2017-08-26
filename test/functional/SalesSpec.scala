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
import helpers.Helper.disableMailer
import helpers.UrlHelper
import helpers.UrlHelper._
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.By
import org.openqa.selenium.By._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Date.{valueOf => date}
import java.util.concurrent.TimeUnit
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class SalesSpec extends Specification with SalesSpecBase with InjectorSupport {
  "Sales" should {
    "Can sell item." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(inMemoryDatabase(options = Map("MVCC" -> "true")) ++ disableMailer)
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
        
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal("5"), Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()
        browser.find(".addToCartButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset button")).displayed()
        // Goto cart button
        browser.find(".ui-dialog-buttonset button").get(1).click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".toEnterShippingAddressInner a").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address01")
        browser.find("#address2").fill().`with`("address02")
        browser.find("#tel1").fill().`with`("11111111")
        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("cannot.ship.title"))
        browser.find(".backToShippingLink").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        val shippingDate = LocalDateTime.now().plus(10, ChronoUnit.DAYS)
        val formattedShippingDate =
          DateTimeFormatter.ofPattern(Messages("shipping.date.format")).format(shippingDate)

        if (browser.find("#shippingDateTextBox").index(0).displayed) {
          browser.find("#shippingDateTextBox").fill().`with`(formattedShippingDate)
        }

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("confirm.shipping.address"))

        browser.find(".backToShippingLink").click()
        if (browser.find("#shippingDateTextBox").index(0).displayed) {
          browser.find("#shippingDateTextBox").attribute("value") === (formattedShippingDate)
        }

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("confirm.shipping.address"))

        browser.find(".itemTableBody .itemName").text() === "かえで"
        browser.find(".itemTableBody .siteName").text() === "Store01"
        if (itemSizeExists)
          browser.find(".itemTableBody .itemSize").text() === Messages("item.size." + itemClass)
        browser.find(".itemTableBody .itemQuantity").text() === "1"
        browser.find(".itemTableBody .itemPrice").text() === "999円"
        browser.find(".itemTableBody .outerTaxAmount").text() === "0円"
        browser.find(".itemTableBody .grandTotal").text() === "999円"
        browser.find(".shippingTableBody .boxName").text() === "box01"
        browser.find(".shippingTableBody .boxUnitPrice").text() === "123円"
        browser.find(".shippingTableBody .boxQuantity").text() === "1 箱"
        browser.find(".shippingTableBody .boxPrice").text() === "123円"
        browser.find(".salesTotalBody").index(0).find(".itemQuantity").text() === "1"
        browser.find(".salesTotalBody").index(0).find(".itemPrice").text() === "999円"
        browser.find(".salesTotalBody").index(1).find(".itemQuantity").text() === "1 箱"
        browser.find(".salesTotalBody").index(1).find(".itemPrice").text() === "123円"
        browser.find(".salesTotalBody").index(2).find(".itemPrice").text() === "1,122円"
        doWith(browser.find(".shippingAddress")) { e =>
          e.find(".shippingTableBody td.name").text() === "firstName01 lastName01"
          e.find(".shippingTableBody td.nameKana").text() === "firstNameKana01 lastNameKana01"
          e.find(".shippingTableBody td.zip").text() === "146 - 0082"
          e.find(".address .prefecture").text() === JapanPrefecture.東京都.toString
          e.find(".address .address1").text() === "address01"
          e.find(".address .address2").text() === "address02"
          e.find(".shippingTableBody .tel1").text() === "11111111"
        }
        browser.find("#paypalimg").size === 0
        browser.find(".payByAccountingBill").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("end.transaction"))

        browser.find(".itemTableBody .itemNameBody").text() === "かえで"
        browser.find(".itemTableBody .siteName").text() === "Store01"
        if (itemSizeExists)
          browser.find(".itemTableBody .size").text() === Messages("item.size." + itemClass)
        browser.find(".itemTableBody .quantity").text() === "1"
        browser.find(".itemTableBody .itemPrice").text() === "999円"
        browser.find(".itemTableBody .subtotal").text() === "999円"
        browser.find(".itemTableBody .outerTaxAmount").text() === "0円"
        browser.find(".itemTableBody .grandTotal").text() === "999円"
        browser.find(".itemTableBody .siteName").text() === "Store01"
        browser.find(".shippingTableBody .boxName").text() === "box01"
        browser.find(".shippingTableBody .boxUnitPrice").text() === "123円"
        browser.find(".shippingTableBody .boxQuantity").text() === "1 箱"
        browser.find(".shippingTableBody .boxPrice").text() === "123円"
        browser.find(".salesTotal .salesTotalBody").index(0).find(".itemQuantity").text() === "1"
        browser.find(".salesTotal .salesTotalBody").index(0).find(".itemPrice").text() === "999円"
        browser.find(".salesTotal .salesTotalBody").index(1).find(".itemQuantity").text() === "1 箱"
        browser.find(".salesTotal .salesTotalBody").index(1).find(".itemPrice").text() === "123円"
        browser.find(".salesTotal .salesTotalBody").index(2).find(".itemPrice").text() === "1,122円"
        doWith(browser.find(".shippingAddress")) { e =>
          e.find(".shippingTableBody .name").text() === "firstName01 lastName01"
          e.find(".shippingTableBody .nameKana").text() === "firstNameKana01 lastNameKana01"
          e.find(".shippingTableBody .zip").text() === "146 - 0082"

          e.find(".prefecture").text() === JapanPrefecture.東京都.toString
          e.find(".address1").text() === "address01"
          e.find(".address2").text() === "address02"
        }

        browser.find(".shippingTableBody .tel1").text() === "11111111"

        val headers = TransactionLogHeader.list()
        headers.size === 1
        val tran: PersistedTransaction = inject[TransactionPersister].load(headers(0).id.get, Ja)
        doWith(tran.tranSiteLog) { siteLog =>
          siteLog.size === 1
          siteLog(site.id.get).siteId === site.id.get
        }
        doWith(tran.siteTable) { siteTable =>
          siteTable.size === 1
          siteTable.head === site
        }
        val addressId = doWith(tran.shippingTable) { shippingTable =>
          shippingTable.size === 1
          val shippings = shippingTable(site.id.get)
          shippings.size === 1
          doWith(shippings.head) { shipping =>
            shipping.amount === BigDecimal(123)
            shipping.costAmount === Some(BigDecimal(100))
            shipping.itemClass === itemClass
            shipping.boxSize === 3
            shipping.taxId === tax.id.get
            shipping.boxCount === 1
            shipping.boxName === "box01"
            shipping.addressId
          }
        }

        doWith(Address.byId(addressId)) { addr =>
          addr.countryCode === CountryCode.JPN
          addr.firstName === "firstName01"
          addr.middleName === ""
          addr.lastName === "lastName01"
          addr.firstNameKana === "firstNameKana01"
          addr.lastNameKana === "lastNameKana01"
          addr.zip1 === "146"
          addr.zip2 === "0082"
          addr.zip3 === ""
          addr.prefecture === JapanPrefecture.東京都
          addr.address1 === "address01"
          addr.address2 === "address02"
          addr.tel1 === "11111111"
        }

        doWith(tran.taxTable) { taxTable =>
          taxTable.size === 1
          doWith(taxTable(site.id.get)) { taxes =>
            taxes.size === 1
            doWith(taxes.head) { tax =>
              tax.taxId === tax.id.get
              tax.taxType === TaxType.INNER_TAX
              tax.rate === BigDecimal(5)
              tax.targetAmount === BigDecimal(1122)
              tax.amount === BigDecimal(1122 * 5 / 105)
            }
          }
        }

        doWith(tran.itemTable) { itemTable =>
          doWith(itemTable(site.id.get)) { items =>
            items.size === 1
            doWith(items.head) { it =>
              doWith(it._1) { itemName =>
                itemName.localeId === Ja.id
                itemName.itemId === item.id.get
                itemName.name === "かえで"
              }

              doWith(it._2) { tranItem =>
                tranItem.quantity === 1
                tranItem.amount === BigDecimal(999)
                tranItem.costPrice === BigDecimal(888)
                tranItem.taxId === tax.id.get

                doWith(TransactionLogItemNumericMetadata.list(tranItem.id.get)) { mdTable =>
                  mdTable.size === 1
                  doWith(mdTable.head) { md =>
                    md.metadataType === ItemNumericMetadataType.HEIGHT
                    md.metadata === 1
                  }
                }

                doWith(TransactionLogItemTextMetadata.list(tranItem.id.get)) { mdTable =>
                  mdTable.size === 1
                  doWith(mdTable.head) { md =>
                    md.metadataType === ItemTextMetadataType.ABOUT_HEIGHT
                    md.metadata === "Hello"
                  }
                }

                doWith(TransactionLogSiteItemNumericMetadata.list(tranItem.id.get).toSeq) { mdTable =>
                  mdTable.size === 2
                  doWith(mdTable(0)) { md =>
                    md.metadataType === SiteItemNumericMetadataType.STOCK
                    md.metadata === 20
                  }
                  doWith(mdTable(1)) { md =>
                    md.metadataType === SiteItemNumericMetadataType.SHIPPING_SIZE
                    md.metadata === itemClass
                  }
                }

                doWith(TransactionLogSiteItemTextMetadata.list(tranItem.id.get)) { mdTable =>
                  mdTable.size === 1
                  doWith(mdTable.head) { md =>
                    md.metadataType === SiteItemTextMetadataType.PRICE_MEMO
                    md.metadata === "World"
                  }
                }
              }

              it._3 === None
            }
          }
        }
      }
    }

    "If price is expired, error should be shown." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(inMemoryDatabase() ++ disableMailer)
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
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val item = inject[ItemRepo].createNew(cat)
        val item2 = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        inject[SiteItemRepo].createNew(site, item2)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemName2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "まつ"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemDesc2 = inject[ItemDescriptionRepo].createNew(item2, site, "まつ説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPrice2 = inject[ItemPriceRepo].createNew(item2, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val iph2 = inject[ItemPriceHistoryRepo].createNew(
          itemPrice2, tax, currencyInfo.Jpy, BigDecimal(1999), None, BigDecimal("1888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()

        (new Actions(browser.webDriver)).moveToElement(
          browser.webDriver.findElement(By.cssSelector(".addToCartButton"))
        ).click().perform()
        browser.await().atMost(30, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset button")).displayed()
        browser.find(".ui-dialog-buttonset button").index(0).click()

        (new Actions(browser.webDriver)).moveToElement(
          browser.webDriver.findElements(By.cssSelector(".addToCartButton")).get(1)
        ).click().perform()
        browser.await().atMost(30, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset button")).displayed()

        // Expire price history.
        inject[ItemPriceHistoryRepo].update(
          iph.id.get, iph.taxId, iph.currency.id, iph.unitPrice, iph.listPrice, iph.costPrice,
          Instant.ofEpochMilli(System.currentTimeMillis - 10000)
        )

        // Goto cart button
        browser.find(".ui-dialog-buttonset button").get(1).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemExpiredTitle"))
        browser.find(".expiredItemRow").size === 1
        browser.find(".expiredItemRow .siteName").text() === site.name
        browser.find(".expiredItemRow .itemName").text() === itemName(Ja).name
        browser.find("#removeExpiredItemsButton").click()
        browser.find(".shoppingCartTable tr").size === 2
        browser.find(".shoppingCartTable tr").index(1).find("td").text() === itemName2(Ja).name
      }
    }

    "Item expires at shipping confirmation." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(inMemoryDatabase() ++ disableMailer)
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
        
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()
        browser.find(".addToCartButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset button")).displayed()
        // Goto cart button
        browser.find(".ui-dialog-buttonset button").get(1).click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".toEnterShippingAddressInner a").click()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address01")
        browser.find("#address2").fill().`with`("address02")
        browser.find("#tel1").fill().`with`("11111111")

        // Expire price history.
        inject[ItemPriceHistoryRepo].update(
          iph.id.get, iph.taxId, iph.currency.id, iph.unitPrice, iph.listPrice, iph.costPrice,
          Instant.ofEpochMilli(System.currentTimeMillis - 10000)
        )

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemExpiredTitle"))
        browser.find(".expiredItemRow").size === 1
        browser.find(".expiredItemRow .siteName").text() === site.name
        browser.find(".expiredItemRow .itemName").text() === itemName(Ja).name
        browser.find("#removeExpiredItemsButton").click()
        browser.find(".shoppingCartEmpty").text() === Messages("shopping.cart.empty")
      }
    }

    "Item expires on finalizing transaction." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(inMemoryDatabase() ++ disableMailer)
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
        
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 20
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()
        browser.find(".addToCartButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset button")).displayed()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        // Goto cart button
        browser.find(".ui-dialog-buttonset button").get(1).click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".toEnterShippingAddressInner a").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address01")
        browser.find("#address2").fill().`with`("address02")
        browser.find("#tel1").fill().`with`("11111111")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("confirm.shipping.address"))

        // Expire price history.
        inject[ItemPriceHistoryRepo].update(
          iph.id.get, iph.taxId, iph.currency.id, iph.unitPrice, iph.listPrice, iph.costPrice,
          Instant.ofEpochMilli(System.currentTimeMillis - 10000)
        )
        
        browser.find("#paypalimg").size === 0
        browser.find(".payByAccountingBill").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemExpiredTitle"))
        browser.find(".expiredItemRow").size === 1
        browser.find(".expiredItemRow .siteName").text() === site.name
        browser.find(".expiredItemRow .itemName").text() === itemName(Ja).name
        browser.find("#removeExpiredItemsButton").click()
        browser.find(".shoppingCartEmpty").text() === Messages("shopping.cart.empty")
      }
    }

    "Stock exceeds." in new WithBrowser(
      WebDriverFactory(FIREFOX), appl(inMemoryDatabase() ++ disableMailer)
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
        
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "内税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )
        val item = inject[ItemRepo].createNew(cat)
        ItemNumericMetadata.createNew(
          item, ItemNumericMetadataType.HEIGHT, 1
        )
        ItemTextMetadata.createNew(
          item, ItemTextMetadataType.ABOUT_HEIGHT, "Hello"
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.STOCK, 2
        )
        SiteItemTextMetadata.createNew(
          site.id.get, item.id.get, SiteItemTextMetadataType.PRICE_MEMO, "World"
        )
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemClass = 1L
        inject[SiteItemNumericMetadataRepo].createNew(
          site.id.get, item.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, itemClass
        )
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val iph = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        browser.goTo(itemQueryUrl())
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".addToCartButton")).displayed()
        browser.find(".addToCartButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).until(browser.el(".ui-dialog-buttonset button")).displayed()

        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box01")
        val fee = inject[ShippingFeeRepo].createNew(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(fee.id.get, tax.id.get, BigDecimal(123), Some(100),
          Instant.ofEpochMilli(date("9999-12-31").getTime)
        )

        // Goto cart button
        browser.find(".ui-dialog-buttonset button").get(1).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#changeItemQuantityInShoppingCart input[name='quantity']").fill().`with`("3")
        browser.find("#changeItemQuantityInShoppingCart input[type='submit']").click()

        browser.find(".toEnterShippingAddressInner a").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#firstNameKana").fill().`with`("firstNameKana01")
        browser.find("#lastNameKana").fill().`with`("lastNameKana01")
        browser.find("input[name='zip1']").fill().`with`("146")
        browser.find("input[name='zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("address01")
        browser.find("#address2").fill().`with`("address02")
        browser.find("#tel1").fill().`with`("11111111")

        if (browser.find("#agreeCheck").size != 0) {
          browser.find("#agreeCheck").click()
        }
        browser.find("#enterShippingAddressForm input[type='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("confirm.shipping.address"))

        browser.find("#paypalimg").size === 0
        browser.find(".payByAccountingBill").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemStockExhaustedTitle"))
        browser.find(".stockExhaustedItem .stockExhaustedItemRow.body .siteName").text() === "Store01"
        browser.find(".stockExhaustedItem .stockExhaustedItemRow.body .itemName").text() === "かえで"
        browser.find(".stockExhaustedItem .stockExhaustedItemRow.body .remainingCount").text() === "2"

        browser.find("a.toSHoppingCart").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("shopping.cart"))
      }
    }
  }
}
