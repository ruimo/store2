package functional

import java.text.SimpleDateFormat
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
import models._
import java.sql.Connection
import LocaleInfo._
import play.api.i18n.{Lang, Messages}
import play.api.db._
import helpers.Helper._
import play.api.test._
import play.api.test.Helpers._
import helpers.ViewHelpers
import java.util.concurrent.TimeUnit
import com.ruimo.scoins.Scoping._
import SeleniumHelpers.FirefoxJa

class OrderHistorySpec extends Specification with InjectorSupport {
  case class Tran(
    now: Long,
    tranHeader: TransactionLogHeader,
    tranSiteHeader: Seq[TransactionLogSite],
    transporter1: Transporter,
    transporter2: Transporter,
    transporterName1: TransporterName,
    transporterName2: TransporterName,
    address: Address,
    itemPriceHistory: Seq[ItemPriceHistory]
  )

  "Order history" should {
    "Show login user's order history" in new WithBrowser(
      WebDriverFactory(FIREFOX), appl()
    ){
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val tran = createTransaction(lang, user)
        browser.goTo(
          controllers.routes.OrderHistory.showOrderHistory() + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("order.history.title"))
        browser.find(".orderHistoryInnerTable1").size === 2
        doWith(browser.find(".orderHistoryInnerTable1")) { b =>
          b.find(".transactionTime td").text ===
            "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(tran.now)
          b.find(".tranNo").text === tran.tranHeader.id.get.toString
          val user = inject[StoreUserRepo].apply(tran.tranHeader.userId)
          b.find(".buyerName").text === user.firstName + " " + user.lastName
          if (b.find(".subtotal").index(0).text == ViewHelpers.toAmount(tran.tranSiteHeader(0).totalAmount)) {
            b.find(".subtotal").index(1).text === ViewHelpers.toAmount(tran.tranSiteHeader(1).totalAmount)
          }
          else {
            b.find(".subtotal").index(0).text === ViewHelpers.toAmount(tran.tranSiteHeader(1).totalAmount)
            b.find(".subtotal").index(1).text === ViewHelpers.toAmount(tran.tranSiteHeader(0).totalAmount)
          }
          b.find(".outerTaxAmount").text === ViewHelpers.toAmount(0)
          if (b.find(".total").index(0).text == ViewHelpers.toAmount(tran.tranSiteHeader(0).totalAmount)) {
            b.find(".total").index(1).text === ViewHelpers.toAmount(tran.tranSiteHeader(1).totalAmount)
          }
          else {
            b.find(".total").index(0).text === ViewHelpers.toAmount(tran.tranSiteHeader(1).totalAmount)
            b.find(".total").index(1).text === ViewHelpers.toAmount(tran.tranSiteHeader(0).totalAmount)
          }
        }
        doWith(browser.find(".shippingAddressTable")) { b =>
          b.find(".name").text === tran.address.firstName + " " + tran.address.lastName
          b.find(".zip").text === tran.address.zip1 + " - " + tran.address.zip2
          b.find(".prefecture").text === tran.address.prefecture.toString
          b.find(".address1").text === tran.address.address1
          b.find(".address2").text === tran.address.address2
          b.find(".tel1").text === tran.address.tel1
          b.find(".comment").text === tran.address.comment
        }
        doWith(browser.find(".orderHistoryInnerTable2")) { b =>
          b.find(".status").text === Messages("transaction.status.ORDERED")
          if (b.find(".shippingDate").index(0).text == 
            new SimpleDateFormat(Messages("shipping.date.format")).format(
              new java.util.Date(date("2013-02-03").toEpochMilli)
            )
          ) {
            b.find(".shippingDate").index(1).text ===
            new SimpleDateFormat(Messages("shipping.date.format")).format(
              new java.util.Date(date("2013-02-04").toEpochMilli)
            )
          }
          else {
            b.find(".shippingDate").index(0).text ===
            new SimpleDateFormat(Messages("shipping.date.format")).format(
              new java.util.Date(date("2013-02-04").toEpochMilli)
            )

            b.find(".shippingDate").index(1).text ===
            new SimpleDateFormat(Messages("shipping.date.format")).format(
              new java.util.Date(date("2013-02-03").toEpochMilli)
            )
          }
        }

        val (tran0, tran1) = if (
          browser.find(".orderHistoryInnerTable3").find("td.itemName").index(0).text == "植木1"
        ) (0, 1) else (1, 0)

        doWith(browser.find(".orderHistoryInnerTable3")) { b =>
          b.size === 2
          b.get(tran0).find("td.unitPrice").index(0).text === "100円"
          b.get(tran0).find("td.quantity").index(0).text === "3"
          b.get(tran0).find("td.price").index(0).find(".body").text === "300円"
          b.get(tran0).find("td.itemName").index(1).text === "植木3"
          b.get(tran0).find("td.unitPrice").index(1).text === "300円"
          b.get(tran0).find("td.quantity").index(1).text === "7"
          b.get(tran0).find("td.price").index(1).find(".body").text === "2,100円"
          b.get(tran0).find("td.subtotalBody").find(".body").text === "2,400円"

          b.get(tran1).find("td.unitPrice").text === "200円"
          b.get(tran1).find("td.quantity").text === "5"
          b.get(tran1).find("td.price").find(".body").text === "1,000円"
          b.get(tran1).find("td.itemName").index(0).text === "植木2"
          b.get(tran1).find("td.subtotalBody").find(".body").text === "1,000円"
        }

        val (box0, box1) = if (
          browser.find(".orderHistoryInnerTable4").index(0).find("td.boxName").text == "site-box1"
        ) (0, 1) else (1, 0)

        doWith(browser.find(".orderHistoryInnerTable4").index(box0)) { b =>
          b.find("td.boxName").text === "site-box1"
          b.find("td.boxPrice").text === "123円"
          b.find("td.subtotalBody").text === "123円"
        }
        doWith(browser.find(".orderHistoryInnerTable4").index(box1)) { b =>
          b.find("td.boxName").text === "site-box2"
          b.find("td.boxPrice").text === "468円"
          b.find("td.subtotalBody").text === "468円"
        }


        doWith(browser.find(".orderHistoryInnerTable1").get(1)) { b =>
          b.find(".transactionTime").text ===
            "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(tran.now)
          b.find(".tranNo").text === tran.tranHeader.id.get.toString
          val user = inject[StoreUserRepo].apply(tran.tranHeader.userId)
          b.find(".buyerName").text === user.firstName + " " + user.lastName
        }

        browser.find(".orderHistoryInnerTable1").index(tran1).find(".subtotal").text === ViewHelpers.toAmount(1468)
        browser.find(".orderHistoryInnerTable1").index(tran0).find(".subtotal").text === ViewHelpers.toAmount(2523)
          
        browser.find(".orderHistoryInnerTable1").find(".outerTaxAmount").text === ViewHelpers.toAmount(0)

        browser.find(".orderHistoryInnerTable1").index(tran1).find(".total").text === ViewHelpers.toAmount(1468)
        browser.find(".orderHistoryInnerTable1").index(tran0).find(".total").text === ViewHelpers.toAmount(2523)

        doWith(browser.find(".shippingAddressTable").get(1)) { b =>
          b.find(".name").text === tran.address.firstName + " " + tran.address.lastName
          b.find(".zip").text === tran.address.zip1 + " - " + tran.address.zip2
          b.find(".prefecture").text === tran.address.prefecture.toString
          b.find(".address1").text === tran.address.address1
          b.find(".address2").text === tran.address.address2
          b.find(".tel1").text === tran.address.tel1
          b.find(".comment").text === tran.address.comment
        }
        doWith(browser.find(".orderHistoryInnerTable2").index(tran1)) { b =>
          b.find(".status").text === Messages("transaction.status.ORDERED")
          b.find(".shippingDate").text === 
          new SimpleDateFormat(Messages("shipping.date.format")).format(
            new java.util.Date(date("2013-02-04").toEpochMilli)
          )
        }
        doWith(browser.find(".orderHistoryInnerTable2").index(tran0)) { b =>
          b.find(".status").text === Messages("transaction.status.ORDERED")
          b.find(".shippingDate").text ===
          new SimpleDateFormat(Messages("shipping.date.format")).format(
            new java.util.Date(date("2013-02-03").toEpochMilli)
          )
        }
        doWith(browser.find(".orderHistoryInnerTable3").index(tran1)) { b =>
          b.find("td.itemName").text === "植木2"
          b.find("td.unitPrice").text === "200円"
          b.find("td.quantity").text === "5"
          b.find("td.price").find(".body").text === "1,000円"

          b.find("td.subtotalBody").find(".body").text === "1,000円"
        }
        doWith(browser.find(".orderHistoryInnerTable3").index(tran0)) { b =>
          b.find("td.itemName").index(0).text === "植木1"
          b.find("td.unitPrice").index(0).text === "100円"
          b.find("td.quantity").index(0).text === "3"
          b.find("td.price").index(0).find(".body").text === "300円"

          b.find("td.subtotalBody").find(".body").text === "2,400円"
        }
        doWith(browser.find(".orderHistoryInnerTable4").index(tran1)) { b =>
          b.find("td.boxName").text === "site-box2"
          b.find("td.boxPrice").text === "468円"
          b.find("td.subtotalBody").text === "468円"
        }
        doWith(browser.find(".orderHistoryInnerTable4").index(tran0)) { b =>
          b.find("td.boxName").text === "site-box1"
          b.find("td.boxPrice").text === "123円"
          b.find("td.subtotalBody").text === "123円"
        }
      }
    }

    "Show login user's order history list" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val tran = createTransaction(lang, user)
        browser.goTo(
          controllers.routes.OrderHistory.showOrderHistoryList() + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("order.history.list.title"))
        doWith(browser.find(".orderHistoryTable")) { b =>
          b.find(".transactionId").index(0).text === tran.tranHeader.id.get.toString
          b.find(".transactionDate").index(0).text === "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(tran.now)
          b.find(".siteName").index(0).text === "商店1"
          b.find(".price").index(0).text === "2,523円"

          b.find(".transactionId").index(1).text === tran.tranHeader.id.get.toString
          b.find(".transactionDate").index(1).text === "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(tran.now)
          b.find(".siteName").index(1).text === "商店2"
          b.find(".price").index(1).text === "1,468円"
        }

        browser.find(".transactionId").index(0).find("a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("order.history.title"))
        browser.find(".subtotal").index(0).text === "2,523円"
        browser.find(".subtotal").index(1).text === "1,468円"
      }
    }

    "Can put an item that is bought before into shopping cart" in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val tran = createTransaction(lang, user)
        browser.goTo(
          controllers.routes.Purchase.clear() + "?lang=" + lang.code
        )
        browser.goTo(
          controllers.routes.OrderHistory.showOrderHistory() + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("order.history.title"))

        browser.find(".orderHistoryInnerTable3").size === 2

        if (browser.find(".orderHistoryInnerTable3").index(0).find("td.itemName").text == "植木1") {
          browser.find(".orderHistoryInnerTable3").index(0).find("button").get(0).click()
        }
        else {
          browser.find(".orderHistoryInnerTable3").index(1).find("button").get(0).click()
        }

        browser.waitUntil(
          failFalse(browser.find(".ui-dialog-buttonset").first().displayed())
        )
        browser.find(".ui-dialog-titlebar").find("span.ui-dialog-title").text === Messages("shopping.cart")
        doWith(browser.find("#cartDialogContent")) { b =>
          b.find("td.itemName").text === "植木1"
          b.find("td.siteName").text === "商店1"
          b.find("td.unitPrice").text === "100円"
          b.find("td.quantity").text === "3"
          b.find("td.price").text === "300円"
        }

        browser.find(".ui-dialog-buttonset").find("button").get(0).click()
        browser.waitUntil(
          failFalse(! browser.find(".ui-dialog-buttonset").first().displayed())
        )

        if (browser.find(".orderHistoryInnerTable3").index(0).find("td.itemName").text == "植木1") {
          browser.find(".orderHistoryInnerTable3").index(0).find("button").get(2).click()
        }
        else {
          browser.find(".orderHistoryInnerTable3").index(1).find("button").get(2).click()
        }

        browser.waitUntil(
          failFalse(browser.find(".ui-dialog-buttonset").first().displayed())
        )

        browser.find(".ui-dialog-titlebar").find("span.ui-dialog-title").text === Messages("shopping.cart")
        doWith(browser.find("#cartDialogCurrentContent")) { b =>
          b.find("td.itemName").text === "植木1"
          b.find("td.siteName").text === "商店1"
          b.find("td.unitPrice").text === "100円"
          b.find("td.quantity").text === "6"
          b.find("td.price").text === "600円"

          b.find("td.itemName").get(1).text === "植木3"
          b.find("td.siteName").get(1).text === "商店1"
          b.find("td.unitPrice").get(1).text === "300円"
          b.find("td.quantity").get(1).text === "7"
          b.find("td.price").get(1).text === "2,100円"
        }

        browser.find(".ui-dialog-buttonset").find("button").get(0).click()
        browser.waitUntil(
          failFalse(! browser.find(".ui-dialog-buttonset").first().displayed())
        )

        if (browser.find(".orderHistoryInnerTable3").index(0).find("td.itemName").text == "植木1") {
          browser.find(".orderHistoryInnerTable3").index(1).find("button").get(0).click()
        }
        else {
          browser.find(".orderHistoryInnerTable3").index(0).find("button").get(0).click()
        }

        browser.waitUntil(
          failFalse(browser.find(".ui-dialog-buttonset").first().displayed())
        )

        browser.find(".ui-dialog-titlebar").find("span.ui-dialog-title").text === Messages("shopping.cart")
        doWith(browser.find("#cartDialogCurrentContent")) { b =>
          b.find("td.itemName").text === "植木1"
          b.find("td.siteName").text === "商店1"
          b.find("td.unitPrice").text === "100円"
          b.find("td.quantity").text === "6"
          b.find("td.price").text === "600円"

          b.find("td.itemName").get(1).text === "植木3"
          b.find("td.siteName").get(1).text === "商店1"
          b.find("td.unitPrice").get(1).text === "300円"
          b.find("td.quantity").get(1).text === "7"
          b.find("td.price").get(1).text === "2,100円"

          b.find("td.itemName").get(2).text === "植木2"
          b.find("td.siteName").get(2).text === "商店2"
          b.find("td.unitPrice").get(2).text === "200円"
          b.find("td.quantity").get(2).text === "5"
          b.find("td.price").get(2).text === "1,000円"
        }
      }
    }

    "Can put an item that is bought before into shopping cart and expired." in new WithBrowser(
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

        val user = loginWithTestUser(browser)
        val tran = createTransaction(lang, user)
        browser.goTo(
          controllers.routes.Purchase.clear() + "?lang=" + lang.code
        )
        browser.goTo(
          controllers.routes.OrderHistory.showOrderHistory() + "?lang=" + lang.code
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("order.history.title"))

        browser.find(".orderHistoryInnerTable3").size === 2

        // Expire 植木1
        doWith(tran.itemPriceHistory.head) { iph =>
          inject[ItemPriceHistoryRepo].update(
            iph.id.get, iph.taxId, iph.currency.id, iph.unitPrice, iph.listPrice, iph.costPrice,
            Instant.ofEpochMilli(System.currentTimeMillis - 10000)
          )
        }

        if (browser.find(".orderHistoryInnerTable3").index(0).find("td.itemName").text == "植木1") {
          browser.find(".orderHistoryInnerTable3").index(0).find("button").get(0).click()
        }
        else {
          browser.find(".orderHistoryInnerTable3").index(1).find("button").get(0).click()
        }

        browser.await().atMost(30, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("itemExpiredTitle"))
        browser.find(".expiredItemRow").size === 1
        browser.find(".expiredItemRow .siteName").text === "商店1"
        browser.find(".expiredItemRow .itemName").text === "植木1"

        browser.find("#removeExpiredItemsButton").click()
        browser.find(".shoppingCartEmpty").text === Messages("shopping.cart.empty")
      }
    }
  }

  def createTransaction(lang: Lang, user: StoreUser)(implicit conn: Connection, app: PlayApp): Tran = {
    val localeInfo = inject[LocaleInfoRepo]
    import localeInfo.{En, Ja}
    val currencyInfo = inject[CurrencyRegistry]
    implicit val storeUserRepo = inject[StoreUserRepo]

    implicit val taxRepo = inject[TaxRepo]
    val tax = taxRepo.createNew
    val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

    val site1 = inject[SiteRepo].createNew(Ja, "商店1")
    val site2 = inject[SiteRepo].createNew(Ja, "商店2")
    
    val cat1 = inject[CategoryRepo].createNew(
      Map(Ja -> "植木", En -> "Plant")
    )
    
    val item1 = inject[ItemRepo].createNew(cat1)
    val item2 = inject[ItemRepo].createNew(cat1)
    val item3 = inject[ItemRepo].createNew(cat1)
    
    inject[SiteItemRepo].createNew(site1, item1)
    inject[SiteItemRepo].createNew(site2, item2)
    inject[SiteItemRepo].createNew(site1, item3)
    
    inject[SiteItemNumericMetadataRepo].createNew(site1.id.get, item1.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1L)
    inject[SiteItemNumericMetadataRepo].createNew(site2.id.get, item2.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1L)
    inject[SiteItemNumericMetadataRepo].createNew(site1.id.get, item3.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1L)
    
    val itemName1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "植木1"))
    val itemName2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "植木2"))
    val itemName3 = inject[ItemNameRepo].createNew(item3, Map(Ja -> "植木3"))
    
    val itemDesc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "desc1")
    val itemDesc2 = inject[ItemDescriptionRepo].createNew(item2, site2, "desc2")
    val itemDesc3 = inject[ItemDescriptionRepo].createNew(item3, site1, "desc3")
    
    val itemPrice1 = inject[ItemPriceRepo].createNew(item1, site1)
    val itemPrice2 = inject[ItemPriceRepo].createNew(item2, site2)
    val itemPrice3 = inject[ItemPriceRepo].createNew(item3, site1)
    
    val itemPriceHis1 = inject[ItemPriceHistoryRepo].createNew(
      itemPrice1, tax, currencyInfo.Jpy, BigDecimal("100"), None, BigDecimal("90"), date("9999-12-31")
    )
    val itemPriceHis2 = inject[ItemPriceHistoryRepo].createNew(
      itemPrice2, tax, currencyInfo.Jpy, BigDecimal("200"), None, BigDecimal("190"), date("9999-12-31")
    )
    val itemPriceHis3 = inject[ItemPriceHistoryRepo].createNew(
      itemPrice3, tax, currencyInfo.Jpy, BigDecimal("300"), None, BigDecimal("290"), date("9999-12-31")
    )
    
    val shoppingCartItem1 = inject[ShoppingCartItemRepo].addItem(user.id.get, site1.id.get, item1.id.get.id, 3)
    val shoppingCartItem2 = inject[ShoppingCartItemRepo].addItem(user.id.get, site2.id.get, item2.id.get.id, 5)
    val shoppingCartItem3 = inject[ShoppingCartItemRepo].addItem(user.id.get, site1.id.get, item3.id.get.id, 7)
    
    val addr1 = Address.createNew(
      countryCode = CountryCode.JPN,
      firstName = "firstName1",
      lastName = "lastName1",
      zip1 = "zip1",
      zip2 = "zip2",
      prefecture = JapanPrefecture.東京都,
      address1 = "address1-1",
      address2 = "address1-2",
      tel1 = "tel1-1",
      comment = "comment1"
    )
    
    val trans1 = inject[TransporterRepo].createNew
    val trans2 = inject[TransporterRepo].createNew
    val transName1 = inject[TransporterNameRepo].createNew(
      trans1.id.get, Ja, "トマト運輸"
    )
    val transName2 = inject[TransporterNameRepo].createNew(
      trans2.id.get, Ja, "ヤダワ急便"
    )
    
    val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, 1L, 3, "site-box1")
    val box2 = inject[ShippingBoxRepo].createNew(site2.id.get, 1L, 2, "site-box2")
    
    val fee1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
    val fee2 = inject[ShippingFeeRepo].createNew(box2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
    
    val feeHis1 = inject[ShippingFeeHistoryRepo].createNew(
      fee1.id.get, tax.id.get, BigDecimal(123), None, date("9999-12-31")
    )
    val feeHis2 = inject[ShippingFeeHistoryRepo].createNew(
      fee2.id.get, tax.id.get, BigDecimal(234), None, date("9999-12-31")
    )
    val now = System.currentTimeMillis

    val shippingTotal1 = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
      CountryCode.JPN, JapanPrefecture.東京都.code,
      ShippingFeeEntries().add(site1, 1L, 3).add(site2, 1L, 4),
      Instant.ofEpochMilli(now)
    )
    val shippingDate1 = ShippingDate(
      Map(
        site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-03")),
        site2.id.get -> ShippingDateEntry(site2.id.get, date("2013-02-04"))
      )
    )

    val (cartTotal: ShoppingCartTotal, errors: Seq[ItemExpiredException]) =
      inject[ShoppingCartItemRepo].listItemsForUser(Ja, LoginSession(user, None, 0))
    val tranId = inject[TransactionPersister].persist(
      Transaction(
        user.id.get, currencyInfo.Jpy, cartTotal, Some(addr1), shippingTotal1, shippingDate1, Instant.ofEpochMilli(now)
      )
    )
    val tranList = TransactionLogHeader.list()
    val tranSiteList = inject[TransactionLogSiteRepo].list()

    Tran(
      now,
      tranList(0),
      tranSiteList,
      transporter1 = trans1,
      transporter2 = trans2,
      transporterName1 = transName1,
      transporterName2 = transName2,
      addr1,
      itemPriceHis1::itemPriceHis2::itemPriceHis3::Nil
    )
  }
}
