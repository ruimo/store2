package functional

import java.time.ZoneId
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
import java.util.concurrent.TimeUnit
import org.specs2.mutable.Specification
import models._
import java.sql.Connection
import play.api.i18n.{Lang, Messages}
import helpers.Helper._
import play.api.test._
import play.api.test.Helpers._
import com.ruimo.scoins.Scoping._

class PurchasedCouponSpec extends Specification with InjectorSupport {
  case class Tran(
    now: Instant,
    tranHeader: TransactionLogHeader,
    tranSiteHeader: Seq[TransactionLogSite],
    transporter1: Transporter,
    transporter2: Transporter,
    transporterName1: TransporterName,
    transporterName2: TransporterName,
    address: Address,
    coupon: Coupon
  )

  "Purchased coupon" should {
    "Show purchased coupon list" in new WithBrowser(
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

        // Need site for other customizations where company is selected from sites available in the application.
        val site1 = inject[SiteRepo].createNew(Ja, "商店111")
        createNormalUser(
          browser, "01234567", "password01", "user01@mail.xxx", "firstName01", "lastName01", "商店111"
        )
        createNormalUser(
          browser, "98765432", "password02", "user02@mail.xxx", "firstName02", "lastName02", "商店111"
        )

        val user1 = inject[StoreUserRepo].findByUserName("01234567").get
        val user2 = inject[StoreUserRepo].findByUserName("98765432").get

        val tran01 = createTransaction(lang, user1, 1)
        val tran02 = createTransaction(lang, user2, 10)

        logoff(browser)
        login(browser, "01234567", "password01")
        browser.goTo(
          controllers.routes.CouponHistory.showPurchasedCouponList() + "?lang=" + lang.code
        )

        doWith(browser.find("#purchasedCouponHistory .body")) { body =>
          body.size === 1
          body.find(".tranId").text === tran01.tranHeader.id.get.toString
          body.find(".siteName").text === "商店1"
          body.find(".tranDate").text === DateTimeFormatter.ofPattern(
            Messages("published.date.format")
          ).format(tran01.tranHeader.transactionTime.atZone(ZoneId.systemDefault()))
          body.find(".itemName").text === "植木1"
        }

        browser.find(".itemName input").click()
        
        val currentWindow = browser.webDriver.getWindowHandle
        val allWindows = browser.webDriver.getWindowHandles
        allWindows.remove(currentWindow)
        browser.webDriver.switchTo().window(allWindows.iterator.next)

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".date").find("span").index(1).text() === 
        DateTimeFormatter.ofPattern(Messages("published.date.format")).format(
          tran01.tranHeader.transactionTime.atZone(ZoneId.systemDefault())
        )
        browser.find(".siteName").text() === Messages("coupon.user.company.name", "商店111")
        browser.find(".name").text() === justOneSpace(
          Messages("coupon.user.name", "firstName01", "", "lastName01")
        )
      }
    }
  }

  def createTransaction(
    lang: Lang, user: StoreUser, startIdx: Int
  )(
    implicit conn: Connection, appl: PlayApp, localeInfo: LocaleInfoRepo, currencyInfo: CurrencyRegistry,
    storeUserRepo: StoreUserRepo
  ): Tran = {
    import localeInfo.{En, Ja}
    implicit val taxRepo = inject[TaxRepo]

    val tax = inject[TaxRepo].createNew
    val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

    val site1 = inject[SiteRepo].createNew(Ja, "商店" + startIdx)
    val site2 = inject[SiteRepo].createNew(Ja, "商店" + (startIdx + 1))
    
    val cat1 = inject[CategoryRepo].createNew(
      Map(Ja -> "植木", En -> "Plant")
    )
    
    val item1 = inject[ItemRepo].createNew(cat1)
    val item2 = inject[ItemRepo].createNew(cat1)
    val item3 = inject[ItemRepo].createNew(cat1)
    
    val coupon = Coupon.createNew()
    Coupon.updateAsCoupon(item1.id.get)

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
      trans1.id.get, Ja, "トマト運輸" + startIdx
    )
    val transName2 = inject[TransporterNameRepo].createNew(
      trans2.id.get, Ja, "ヤダワ急便" + startIdx
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
    val now = Instant.now()

    val shippingTotal1 = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
      CountryCode.JPN, JapanPrefecture.東京都.code,
      ShippingFeeEntries().add(site1, 1L, 3).add(site2, 1L, 4),
      now
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
      Transaction(user.id.get, currencyInfo.Jpy, cartTotal, Some(addr1), shippingTotal1, shippingDate1, now)
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
      coupon
    )
  }
}
