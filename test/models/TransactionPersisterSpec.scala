package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import java.time.Instant

class TransactionPersisterSpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "TransactionPersister" should {
    "Can persist one item transaction" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(localeInfo.Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal(900), date("9999-12-31")
        )
        val shoppingCartItem = inject[ShoppingCartItemRepo].addItem(
          user.id.get, site.id.get, item.id.get.id, 2
        )
        implicit val storeUserRepo = inject[StoreUserRepo]
        val cartTotal = inject[ShoppingCartItemRepo].listItemsForUser(
          localeInfo.Ja,
          LoginSession(user, None, 0)
        )._1
        val address = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "First Name",
          lastName = "Last Name"
        )
        val itemClass = 1L
        val now = Instant.now()
        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box1")
        val fee = inject[ShippingFeeRepo].createNew(
          box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code
        )
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(
          fee.id.get, tax.id.get, BigDecimal(123), Some(BigDecimal(100)), date("9999-12-31")
        )
        val shippingTotal = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries().add(site, itemClass, 2),
          now
        )

        val shippingDate = ShippingDate(Map(site.id.get -> ShippingDateEntry(site.id.get, date("2013-02-03"))))

        implicit val taxRepo = inject[TaxRepo]
        inject[TransactionPersister].persist(
          Transaction(user.id.get, currencyInfo.Jpy, cartTotal, Some(address), shippingTotal, shippingDate, now)
        )

        val header = TransactionLogHeader.list()
        header.size === 1
        header(0).userId === user.id.get
        header(0).transactionTime === now
        header(0).currencyId === currencyInfo.Jpy.id
        header(0).totalAmount === BigDecimal(999 * 2 + 123)
        header(0).taxAmount === BigDecimal((999 * 2 + 123) * 5 / 105)
        header(0).transactionType === TransactionTypeCode.ACCOUNTING_BILL

        val siteLog = inject[TransactionLogSiteRepo].list()
        siteLog.size === 1
        siteLog(0).transactionId === header(0).id.get
        siteLog(0).siteId === site.id.get
        siteLog(0).totalAmount === BigDecimal(999 * 2 + 123)
        siteLog(0).taxAmount === BigDecimal((999 * 2 + 123) * 5 / 105)

        val shippingLog = inject[TransactionLogShippingRepo].list()
        shippingLog.size === 1
        shippingLog(0).transactionSiteId === siteLog(0).id.get
        shippingLog(0).amount === BigDecimal(123)
        shippingLog(0).addressId === address.id.get
        shippingLog(0).itemClass === itemClass
        shippingLog(0).boxSize === 3
        shippingLog(0).taxId === tax.id.get
        shippingLog(0).shippingDate === date("2013-02-03")

        val taxLog = inject[TransactionLogTaxRepo].list()
        taxLog.size === 1
        taxLog(0).transactionSiteId === siteLog(0).id.get
        taxLog(0).taxId === tax.id.get
        taxLog(0).taxType === TaxType.INNER_TAX
        taxLog(0).rate === BigDecimal(5)
        taxLog(0).targetAmount === BigDecimal(999 * 2 + 123)
        taxLog(0).amount === BigDecimal((999 * 2 + 123) * 5 / 105)
      }      
    }

    "Can persist coupon item transaction" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "植木"))
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(
          tax, TaxType.INNER_TAX, BigDecimal(5), date("9999-12-31")
        )
        val item = inject[ItemRepo].createNew(cat)
        val coupon = Coupon.createNew()
        CouponItem.create(item.id.get, coupon.id.get)

        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(localeInfo.Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal(900), date("9999-12-31")
        )
        val shoppingCartItem = inject[ShoppingCartItemRepo].addItem(
          user.id.get, site.id.get, item.id.get.id, 2
        )
        implicit val storeUserRepo = inject[StoreUserRepo]
        val cartTotal = inject[ShoppingCartItemRepo].listItemsForUser(
          localeInfo.Ja,
          LoginSession(user, None, 0)
        )._1
        val address = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "First Name",
          lastName = "Last Name"
        )
        val itemClass = 1L
        val now = Instant.now()
        val box = inject[ShippingBoxRepo].createNew(site.id.get, itemClass, 3, "box1")
        val fee = inject[ShippingFeeRepo].createNew(
          box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code
        )
        val feeHistory = inject[ShippingFeeHistoryRepo].createNew(
          fee.id.get, tax.id.get, BigDecimal(123), None, date("9999-12-31")
        )
        val shippingTotal = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries().add(site, itemClass, 2),
          now
        )

        val shippingDate = ShippingDate(Map(site.id.get -> ShippingDateEntry(site.id.get, date("2013-02-03"))))
        implicit val taxRepo = inject[TaxRepo]

        inject[TransactionPersister].persist(
          Transaction(user.id.get, currencyInfo.Jpy, cartTotal, Some(address), shippingTotal, shippingDate, now)
        )

        val header = TransactionLogHeader.list()
        header.size === 1
        header(0).userId === user.id.get
        header(0).transactionTime === now
        header(0).currencyId === currencyInfo.Jpy.id
        header(0).totalAmount === BigDecimal(999 * 2 + 123)
        header(0).taxAmount === BigDecimal((999 * 2 + 123) * 5 / 105)
        header(0).transactionType === TransactionTypeCode.ACCOUNTING_BILL

        val siteLog = inject[TransactionLogSiteRepo].list()
        siteLog.size === 1
        siteLog(0).transactionId === header(0).id.get
        siteLog(0).siteId === site.id.get
        siteLog(0).totalAmount === BigDecimal(999 * 2 + 123)
        siteLog(0).taxAmount === BigDecimal((999 * 2 + 123) * 5 / 105)

        val itemLog = inject[TransactionLogItemRepo].list()
        itemLog.size === 1
        itemLog(0).transactionSiteId === siteLog(0).id.get
        itemLog(0).itemId === item.id.get.id
        itemLog(0).itemPriceHistoryId === itemPriceHistory.id.get
        itemLog(0).quantity === 2
        itemLog(0).amount === BigDecimal(999) * 2
        itemLog(0).costPrice === BigDecimal(900)
        itemLog(0).taxId === tax.id.get

        val couponLogList = inject[TransactionLogCouponRepo].list(localeInfo.Ja, user.id.get)
        couponLogList.records.size === 1
        couponLogList.records(0).tranHeaderId === header(0).id.get
        couponLogList.records(0).site === site
        couponLogList.records(0).time === now
        couponLogList.records(0).itemId === item.id.get
        couponLogList.records(0).itemName === itemName(localeInfo.Ja).name
        couponLogList.records(0).couponId === coupon.id.get

        val couponLog = inject[TransactionLogCouponRepo].at(
          localeInfo.Ja, user.id.get, couponLogList.records(0).tranCouponId
        )
        couponLog.couponDetail.tranHeaderId === header(0).id.get
        couponLog.couponDetail.site === site
        couponLog.couponDetail.time === now
        couponLog.couponDetail.itemId === item.id.get
        couponLog.couponDetail.itemName === itemName(localeInfo.Ja).name
        couponLog.couponDetail.couponId === coupon.id.get

        val shippingLog = inject[TransactionLogShippingRepo].list()
        shippingLog.size === 1
        shippingLog(0).transactionSiteId === siteLog(0).id.get
        shippingLog(0).amount === BigDecimal(123)
        shippingLog(0).addressId === address.id.get
        shippingLog(0).itemClass === itemClass
        shippingLog(0).boxSize === 3
        shippingLog(0).taxId === tax.id.get
        shippingLog(0).shippingDate === date("2013-02-03")

        val taxLog = inject[TransactionLogTaxRepo].list()
        taxLog.size === 1
        taxLog(0).transactionSiteId === siteLog(0).id.get
        taxLog(0).taxId === tax.id.get
        taxLog(0).taxType === TaxType.INNER_TAX
        taxLog(0).rate === BigDecimal(5)
        taxLog(0).targetAmount === BigDecimal(999 * 2 + 123)
        taxLog(0).amount === BigDecimal((999 * 2 + 123) * 5 / 105)
      }      
    }
  }
}
