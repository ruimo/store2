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
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection

import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.test.TestServer

class ConfirmShippingSpec extends Specification with InjectorSupport {
  "ConfirmShipping" should {
    "More than one site and more than item classes." in new WithBrowser(
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
        val address = createAddress
        val addressHistory = ShippingAddressHistory.createNew(
          user.id.get, address
        )
        val tax = inject[TaxRepo].createNew
        val his = inject[TaxHistoryRepo].createNew(tax, TaxType.OUTER_TAX, BigDecimal("8"), date("9999-12-31"))

        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")

        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木", En -> "Plant"))

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)
        val item3 = inject[ItemRepo].createNew(cat1)

        val name1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉", En -> "Cedar"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅", En -> "Ume"))
        val name3 = inject[ItemNameRepo].createNew(item3, Map(Ja -> "竹", En -> "Bamboo"))
        
        val desc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        val desc2 = inject[ItemDescriptionRepo].createNew(item2, site2, "梅説明")
        val desc3 = inject[ItemDescriptionRepo].createNew(item3, site2, "竹説明")

        val metadata1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1
        )
        val metadata2 = inject[SiteItemNumericMetadataRepo].createNew(
          site2.id.get, item2.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1
        )
        val metadata3 = inject[SiteItemNumericMetadataRepo].createNew(
          site2.id.get, item3.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 2
        )

        val box1_1 = inject[ShippingBoxRepo].createNew(
          site1.id.get, 1, 7, "商店1の箱1"
        )
        val box2_1 = inject[ShippingBoxRepo].createNew(
          site2.id.get, 1, 3, "商店2の箱1"
        )
        val box2_2 = inject[ShippingBoxRepo].createNew(
          site2.id.get, 2, 5, "商店2の箱2"
        )

        val fee1 = inject[ShippingFeeRepo].createNew(box1_1.id.get, CountryCode.JPN, JapanPrefecture.三重県.code)
        val fee2 = inject[ShippingFeeRepo].createNew(box2_1.id.get, CountryCode.JPN, JapanPrefecture.三重県.code)
        val fee3 = inject[ShippingFeeRepo].createNew(box2_2.id.get, CountryCode.JPN, JapanPrefecture.三重県.code)

        val feeHis1 = inject[ShippingFeeHistoryRepo].createNew(fee1.id.get, tax.id.get, BigDecimal(2345), None, date("9999-12-31"))
        val feeHis2 = inject[ShippingFeeHistoryRepo].createNew(fee2.id.get, tax.id.get, BigDecimal(3333), None, date("9999-12-31"))
        val feeHis3 = inject[ShippingFeeHistoryRepo].createNew(fee3.id.get, tax.id.get, BigDecimal(4444), None, date("9999-12-31"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)
        inject[SiteItemRepo].createNew(site2, item3)

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)
        val price3 = inject[ItemPriceRepo].createNew(item3, site2)

        val ph1 = inject[ItemPriceHistoryRepo].createNew(price1, tax, currencyInfo.Jpy, BigDecimal(101), None, BigDecimal(90), date("9999-12-31"))
        val ph2 = inject[ItemPriceHistoryRepo].createNew(price2, tax, currencyInfo.Jpy, BigDecimal(301), None, BigDecimal(200), date("9999-12-31"))
        val ph3 = inject[ItemPriceHistoryRepo].createNew(price3, tax, currencyInfo.Jpy, BigDecimal(401), None, BigDecimal(390), date("9999-12-31"))

        val cart1 = inject[ShoppingCartItemRepo].addItem(user.id.get, site1.id.get, item1.id.get.id, 15)
        val cart2 = inject[ShoppingCartItemRepo].addItem(user.id.get, site2.id.get, item2.id.get.id, 28)
        val cart3 = inject[ShoppingCartItemRepo].addItem(user.id.get, site2.id.get, item3.id.get.id, 40)

        val cartShipping1 = ShoppingCartShipping.updateOrInsert(user.id.get, site1.id.get, date("2013-12-01"))
        val cartShipping2 = ShoppingCartShipping.updateOrInsert(user.id.get, site2.id.get, date("2013-12-02"))

        browser.goTo(
          controllers.routes.Shipping.confirmShippingAddressJa().url + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("confirm.shipping.address"))

        browser.find("table.itemTable").find("tr.itemTableBody").size === 6
        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(0)
          .find("td.itemName")
          .text === "杉"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(0)
          .find("td.itemQuantity")
          .text === "15"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(0)
          .find("td.itemPrice")
          .text === String.format("%1$,d円", Integer.valueOf(15 * 101))

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(0)
          .find("td.siteName")
          .text === "商店1"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(0)
          .find("td.itemSize")
          .text === Messages("item.size.1")

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(1)
          .find("td.itemName")
          .text === "梅"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(1)
          .find("td.itemQuantity")
          .text === "28"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(1)
          .find("td.itemPrice")
          .text === String.format("%1$,d円", Integer.valueOf(28 * 301))

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(1)
          .find("td.siteName")
          .text === "商店2"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(1)
          .find("td.itemSize")
          .text === Messages("item.size.1")

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(2)
          .find("td.itemName")
          .text === "竹"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(2)
          .find("td.itemQuantity")
          .text === "40"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(2)
          .find("td.itemPrice")
          .text === String.format("%1$,d円", Integer.valueOf(40 * 401))

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(2)
          .find("td.siteName")
          .text === "商店2"

        browser.find("table.itemTable")
          .find("tr.itemTableBody").index(2)
          .find("td.itemSize")
          .text === Messages("item.size.2")

        browser.find("table.itemTable")
          .find("tr.subtotalWithoutTax")
          .find("td.subtotal")
          .text === String.format(
            "%1$,d円",
            Integer.valueOf(15 * 101 + 28 * 301 + 40 * 401)
          )

        // 送料
        browser.find("h2.shippingSiteName").size === 2
        browser.find("h2.shippingSiteName").index(0)
          .text === "商店1"
        browser.find("h2.shippingSiteName").index(1)
          .text === "商店2"
        browser.find("h3.shippingDate").index(0)
          .text === "配送希望日: 2013年12月01日"
        browser.find("h3.shippingDate").index(1)
          .text === "配送希望日: 2013年12月02日"

        browser.find("table.shipping")
          .find("tr.shippingTableBody").index(0)
          .find("td.boxName")
          .text === "商店1の箱1"

        browser.find("table.shipping")
          .find("tr.shippingTableBody").index(0)
          .find("td.boxUnitPrice")
          .text === String.format("%1$,d円", Integer.valueOf(2345))

        browser.find("table.shipping")
          .find("tr.shippingTableBody").index(0)
          .find("td.boxQuantity")
          .text === "3 箱"

        browser.find("table.shipping")
          .find("tr.shippingTableBody").index(0)
          .find("td.boxPrice")
          .text === String.format("%1$,d円", Integer.valueOf(3 * 2345))

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(0)
          .find("td.boxName")
          .text === "商店2の箱1"

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(0)
          .find("td.boxUnitPrice")
          .text === String.format("%1$,d円", Integer.valueOf(3333))

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(0)
          .find("td.boxQuantity")
          .text === "10 箱"

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(0)
          .find("td.boxPrice")
          .text === String.format("%1$,d円", Integer.valueOf(10 * 3333))

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(1)
          .find("td.boxName")
          .text === "商店2の箱2"

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(1)
          .find("td.boxUnitPrice")
          .text === String.format("%1$,d円", Integer.valueOf(4444))

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(1)
          .find("td.boxQuantity")
          .text === "8 箱"

        browser.find("table.shipping").index(1)
          .find("tr.shippingTableBody").index(1)
          .find("td.boxPrice")
          .text === String.format("%1$,d円", Integer.valueOf(8 * 4444))

        // Total quantity
        browser.find("table.salesTotal")
          .find("tr.salesTotalBody").index(0)
          .find("td.itemQuantity")
          .text === "" + (15 + 28 + 40)

        // Total amount(including outer tax)
        browser.find("table.salesTotal")
          .find("tr.salesTotalBody").index(0)
          .find("td.itemPrice")
          .text === String.format(
            "%1$,d円",
            Integer.valueOf(
              15 * 101 + 28 * 301 + 40 * 401
              + (15 * 101 + 28 * 301 + 40 * 401) * 8 / 100
            )
          )

        // Shipping box quantity
        browser.find("table.salesTotal")
          .find("tr.salesTotalBody").index(1)
          .find("td.itemQuantity")
          .text === (3 + 10 + 8) + " 箱"

        // Shipping fee
        browser.find("table.salesTotal")
          .find("tr.salesTotalBody").index(1)
          .find("td.itemPrice")
          .text === String.format("%1$,d円", Integer.valueOf(3 * 2345 + 10 * 3333 + 8 * 4444))

        browser.find("table.salesTotal")
          .find("tr.salesTotalBody").index(2)
          .find("td.itemPrice")
          .text === String.format(
            "%1$,d円", Integer.valueOf(
              15 * 101 + 28 * 301 + 40 * 401
              + (15 * 101 + 28 * 301 + 40 * 401) * 8 / 100
              + (3 * 2345 + 10 * 3333 + 8 * 4444)
            )
          )

        // 送付先
        browser
          .find("table.shippingAddress")
          .find("tr.shippingTableBody.courtesyName")
          .find("td").index(1)
          .text === "firstName lastName"

        browser
          .find("table.shippingAddress")
          .find("tr.shippingTableBody.furiganaKana")
          .find("td").index(1)
          .text === "firstNameKana lastNameKana"

        browser
          .find("table.shippingAddress")
          .find("tr.shippingTableBody.email")
          .find("td").index(1)
          .text === "email1"

        browser
          .find("table.shippingAddress")
          .find("tr.shippingTableBody.zipLine")
          .find("td").index(1)
          .text === "zip1 - zip2"

        val addressLine = browser
          .find("table.shippingAddress")
          .find("tr.shippingTableBody.address")
          .find("td").index(1)
          .text

          addressLine.contains(JapanPrefecture.三重県.toString) === true
          addressLine.contains("address1")
          addressLine.contains("address2")

        browser
          .find("table.shippingAddress")
          .find("tr.shippingTableBody.tel1Line")
          .find("td").index(1)
          .text === "123-2345"
      }
    }
  }

  def createAddress(implicit conn: Connection) = Address.createNew(
    countryCode = CountryCode.JPN,
    firstName = "firstName",
    lastName = "lastName",
    firstNameKana = "firstNameKana",
    lastNameKana = "lastNameKana",
    zip1 = "zip1",
    zip2 = "zip2",
    prefecture = JapanPrefecture.三重県,
    address1 = "address1",
    address2 = "address2",
    tel1 = "123-2345",
    email = "email1"
  )
}
