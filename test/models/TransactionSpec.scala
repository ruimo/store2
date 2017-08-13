package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import collection.immutable
import com.ruimo.scoins.Scoping._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import java.time.Instant

class TransactionSpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "TransactionLog" should {
    "Can persist header." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val currency1 = currencyInfo.Jpy
        val now = 1234L

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val list = TransactionLogHeader.list()
        list.size === 1
        header === list(0)
      }
    }

    "Can persist shipping." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val currency1 = currencyInfo.Jpy
        val now = 1234L

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val addr1 = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "FirstName",
          lastName = "LastName",
          zip1 = "123",
          prefecture = JapanPrefecture.東京都,
          address1 = "Address1",
          address2 = "Address2",
          tel1 = "12345678"
        )

        val tranSite = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        val shipping = inject[TransactionLogShippingRepo].createNew(
          tranSite.id.get, BigDecimal(9876), Some(234L), addr1.id.get, 1L, 1, 1L, 1, "boxName", date("2013-05-05")
        )

        val list = inject[TransactionLogShippingRepo].list()
        list.size === 1
        list(0) === shipping
      }
    }

    "Can persist tax." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val currency1 = currencyInfo.Jpy
        val now = 1234L

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )
          
        val tranSite = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        val tax = inject[TransactionLogTaxRepo].createNew(
          tranSite.id.get,
          1234L, 2345L, TaxType.INNER_TAX,
          BigDecimal(5), BigDecimal(333), BigDecimal(222)
        )

        val list = inject[TransactionLogTaxRepo].list()
        list.size === 1
        list(0) === tax
      }
    }

    "Can persist item." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val currency1 = currencyInfo.Jpy
        val now = 1234L

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val tranSite = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        val cat = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "Category"))
        val item = inject[ItemRepo].createNew(cat)

        val itemLog = inject[TransactionLogItemRepo].createNew(
          tranSite.id.get, item.id.get.id, 1234L, 234L, BigDecimal(456), BigDecimal(400), 123L
        )

        val list = inject[TransactionLogItemRepo].list()
        list.size === 1
        list(0) === itemLog
        list(0).itemId === item.id.get.id
        list(0).taxId === 123L
      }
    }

    "Can persist whole transaction." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew
        val taxHistory1 = inject[TaxHistoryRepo].createNew(tax1, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        val taxHistory2 = inject[TaxHistoryRepo].createNew(tax2, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val user1 = inject[StoreUserRepo].create(
          "name1", "first1", None, "last1", "email1", 123L, 234L, UserRole.NORMAL, Some("companyName")
        )
        import localeInfo.{Ja}
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")

        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        val name1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)

        val desc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        val desc2 = inject[ItemDescriptionRepo].createNew(item2, site1, "梅説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)

        val ph1 = inject[ItemPriceHistoryRepo].createNew(
          price1, tax1, currencyInfo.Jpy, BigDecimal(119), None, BigDecimal(100), date("9999-12-31")
        )
        val ph2 = inject[ItemPriceHistoryRepo].createNew(
          price2, tax1, currencyInfo.Jpy, BigDecimal(59), None, BigDecimal(50), date("9999-12-31")
        )

        val cart1 = inject[ShoppingCartItemRepo].addItem(user1.id.get, site1.id.get, item1.id.get.id, 1)
        val cart2 = inject[ShoppingCartItemRepo].addItem(user1.id.get, site2.id.get, item2.id.get.id, 1)

        val itemClass1 = 1L

        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, itemClass1, 10, "小箱")
        val box2 = inject[ShippingBoxRepo].createNew(site2.id.get, itemClass1, 3, "小箱")
        val shipping1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping2 = inject[ShippingFeeRepo].createNew(box2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipHis1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1.id.get, tax2.id.get, BigDecimal(1234), Some(BigDecimal(1000)), date("9999-12-31")
        )
        val shipHis2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2.id.get, tax2.id.get, BigDecimal(2345), None, date("9999-12-31")
        )

        val shippingTotal = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 3)
            .add(site2, itemClass1, 5)
        )

        implicit val storeUserRepo = inject[StoreUserRepo]
        val cart = inject[ShoppingCartItemRepo].listItemsForUser(
          Ja,
          LoginSession(user1, None, 0L)
        )._1
        val addr = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "FirstName",
          lastName = "LastName",
          zip1 = "123",
          prefecture = JapanPrefecture.東京都,
          address1 = "Address1",
          address2 = "Address2",
          tel1 = "12345678"
        )

        val shippingDate = ShippingDate(
          Map(
            site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-03")),
            site2.id.get -> ShippingDateEntry(site2.id.get, date("2013-05-03"))
          )
        )
        val persister = inject[TransactionPersister]
        implicit val taxRepo = inject[TaxRepo]
        val t = persister.persist(
          Transaction(user1.id.get, currencyInfo.Jpy, cart, Some(addr),
            inject[controllers.Shipping].shippingFee(addr, cart), shippingDate)
        )
        val tranNo: Long = t._1
        val taxesBySite: immutable.Map[Site, immutable.Seq[TransactionLogTax]] = t._2

        val ptran = persister.load(tranNo, Ja)
        ptran.header.id.get === tranNo
        ptran.header.userId === user1.id.get
        ptran.header.currencyId === currencyInfo.Jpy.id
        ptran.header.totalAmount === BigDecimal(119 + 59 + 1234 + 2345)
        ptran.header.taxAmount === BigDecimal(
          (119 + 59) * 5 / 100 +
            (1234 + 2345) * 5 / 105
        )
        ptran.header.transactionType === TransactionTypeCode.ACCOUNTING_BILL
        ptran.siteTable.size == 2
        ptran.siteTable.contains(site1) === true
        ptran.siteTable.contains(site2) === true
        ptran.shippingTable.size === 2
        ptran.shippingTable(site1.id.get).size === 1
        val tranShipping1 = ptran.shippingTable(site1.id.get).head
        tranShipping1.amount === 1234
        tranShipping1.addressId === addr.id.get
        tranShipping1.itemClass === itemClass1
        tranShipping1.boxSize === 10
        tranShipping1.taxId === tax2.id.get
        tranShipping1.shippingDate === date("2013-02-03")

        ptran.shippingTable(site2.id.get).size === 1
        val tranShipping2 = ptran.shippingTable(site2.id.get).head
        tranShipping2.amount === 2345
        tranShipping2.addressId === addr.id.get
        tranShipping2.itemClass === itemClass1
        tranShipping2.boxSize === 3
        tranShipping2.taxId === tax2.id.get
        tranShipping2.shippingDate === date("2013-05-03")

        ptran.taxTable.size === 2
        val taxTable1 = ptran.taxTable(site1.id.get).foldLeft(immutable.LongMap[TransactionLogTax]()) {
          (map, e) => map.updated(e.taxId, e)
        }
        taxTable1.size === 2
        taxTable1(tax1.id.get).taxType === TaxType.OUTER_TAX
        taxTable1(tax1.id.get).rate === BigDecimal(5)
        taxTable1(tax1.id.get).targetAmount === BigDecimal(119)
        taxTable1(tax1.id.get).amount === BigDecimal(119 * 5 / 100)

        taxTable1(tax2.id.get).taxType === TaxType.INNER_TAX
        taxTable1(tax2.id.get).rate === BigDecimal(5)
        taxTable1(tax2.id.get).targetAmount === BigDecimal(1234)
        taxTable1(tax2.id.get).amount === BigDecimal(1234 * 5 / 105)

        val taxTable2 = ptran.taxTable(site2.id.get).foldLeft(immutable.LongMap[TransactionLogTax]()) {
          (map, e) => map.updated(e.taxId, e)
        }
        taxTable2.size === 2
        taxTable2(tax1.id.get).taxType === TaxType.OUTER_TAX
        taxTable2(tax1.id.get).rate === BigDecimal(5)
        taxTable2(tax1.id.get).targetAmount === BigDecimal(59)
        taxTable2(tax1.id.get).amount === BigDecimal(59 * 5 / 100)

        taxTable2(tax2.id.get).taxType === TaxType.INNER_TAX
        taxTable2(tax2.id.get).rate === BigDecimal(5)
        taxTable2(tax2.id.get).targetAmount === BigDecimal(2345)
        taxTable2(tax2.id.get).amount === BigDecimal(2345 * 5 / 105)

        ptran.itemTable.size === 2
        val itemTable1 = ptran.itemTable(site1.id.get)
          .foldLeft(
          immutable.HashMap[ItemId, (ItemName, TransactionLogItem, Option[TransactionLogCoupon])]()
        ) {
          (map, e) => map.updated(e._1.itemId, e)
        }
        itemTable1(item1.id.get)._1.name === "杉"
        itemTable1(item1.id.get)._2.itemId === item1.id.get.id
        itemTable1(item1.id.get)._2.itemPriceHistoryId === ph1.id.get
        itemTable1(item1.id.get)._2.quantity === 1
        itemTable1(item1.id.get)._2.amount === BigDecimal(119)

        val itemTable2 = ptran.itemTable(site2.id.get)
          .foldLeft(
          immutable.HashMap[ItemId, (ItemName, TransactionLogItem, Option[TransactionLogCoupon])]()
        ) {
          (map, e) => map.updated(e._1.itemId, e)
        }
        itemTable2(item2.id.get)._1.name === "梅"
        itemTable2(item2.id.get)._2.itemId === item2.id.get.id
        itemTable2(item2.id.get)._2.itemPriceHistoryId === ph2.id.get
        itemTable2(item2.id.get)._2.quantity === 1
        itemTable2(item2.id.get)._2.amount === BigDecimal(59)
      }
    }

    "Can persist paypal transaction." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew
        val taxHistory1 = inject[TaxHistoryRepo].createNew(tax1, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        val taxHistory2 = inject[TaxHistoryRepo].createNew(tax2, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val user1 = inject[StoreUserRepo].create(
          "name1", "first1", None, "last1", "email1", 123L, 234L, UserRole.NORMAL, Some("companyName")
        )
        import localeInfo.{Ja}
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")

        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        val name1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)

        val desc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        val desc2 = inject[ItemDescriptionRepo].createNew(item2, site1, "梅説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)

        val ph1 = inject[ItemPriceHistoryRepo].createNew(
          price1, tax1, currencyInfo.Jpy, BigDecimal(119), None, BigDecimal(100), date("9999-12-31")
        )
        val ph2 = inject[ItemPriceHistoryRepo].createNew(
          price2, tax1, currencyInfo.Jpy, BigDecimal(59), None, BigDecimal(50), date("9999-12-31")
        )

        val cart1 = inject[ShoppingCartItemRepo].addItem(user1.id.get, site1.id.get, item1.id.get.id, 1)
        val cart2 = inject[ShoppingCartItemRepo].addItem(user1.id.get, site2.id.get, item2.id.get.id, 1)

        val itemClass1 = 1L

        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, itemClass1, 10, "小箱")
        val box2 = inject[ShippingBoxRepo].createNew(site2.id.get, itemClass1, 3, "小箱")
        val shipping1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping2 = inject[ShippingFeeRepo].createNew(box2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipHis1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1.id.get, tax2.id.get, BigDecimal(1234), Some(BigDecimal(1000)), date("9999-12-31")
        )
        val shipHis2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2.id.get, tax2.id.get, BigDecimal(2345), None, date("9999-12-31")
        )

        val shippingTotal = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 3)
            .add(site2, itemClass1, 5)
        )

        implicit val storeUserRepo = inject[StoreUserRepo]
        val cart = inject[ShoppingCartItemRepo].listItemsForUser(
          Ja,
          LoginSession(user1, None, 0L)
        )._1
        val addr = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "FirstName",
          lastName = "LastName",
          zip1 = "123",
          prefecture = JapanPrefecture.東京都,
          address1 = "Address1",
          address2 = "Address2",
          tel1 = "12345678"
        )

        val shippingDate = ShippingDate(
          Map(
            site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-03")),
            site2.id.get -> ShippingDateEntry(site2.id.get, date("2013-05-03"))
          )
        )
        val persister = inject[TransactionPersister]
        implicit val taxRepo = inject[TaxRepo]
        val t = persister.persistPaypal(
          Transaction(user1.id.get, currencyInfo.Jpy, cart, Some(addr),
            inject[controllers.Shipping].shippingFee(addr, cart), shippingDate)
        )
        val tranNo: Long = t._1
        val taxexBySite: immutable.Map[Site, immutable.Seq[TransactionLogTax]] = t._2
        val token: Long = t._3

        val ptran: PersistedTransaction = persister.load(tranNo, Ja)
        ptran.header.id.get === tranNo
        ptran.header.userId === user1.id.get
        ptran.header.currencyId === currencyInfo.Jpy.id
        ptran.header.totalAmount === BigDecimal(119 + 59 + 1234 + 2345)
        ptran.header.taxAmount === BigDecimal(
          (119 + 59) * 5 / 100 +
            (1234 + 2345) * 5 / 105
        )
        ptran.header.transactionType === TransactionTypeCode.PAYPAL_EXPRESS_CHECKOUT
        ptran.siteTable.size == 2
        ptran.siteTable.contains(site1) === true
        ptran.siteTable.contains(site2) === true
        ptran.shippingTable.size === 2
        ptran.shippingTable(site1.id.get).size === 1
        val tranShipping1 = ptran.shippingTable(site1.id.get).head
        tranShipping1.amount === 1234
        tranShipping1.addressId === addr.id.get
        tranShipping1.itemClass === itemClass1
        tranShipping1.boxSize === 10
        tranShipping1.taxId === tax2.id.get
        tranShipping1.shippingDate === date("2013-02-03")

        ptran.shippingTable(site2.id.get).size === 1
        val tranShipping2 = ptran.shippingTable(site2.id.get).head
        tranShipping2.amount === 2345
        tranShipping2.addressId === addr.id.get
        tranShipping2.itemClass === itemClass1
        tranShipping2.boxSize === 3
        tranShipping2.taxId === tax2.id.get
        tranShipping2.shippingDate === date("2013-05-03")

        ptran.taxTable.size === 2
        val taxTable1 = ptran.taxTable(site1.id.get).foldLeft(immutable.LongMap[TransactionLogTax]()) {
          (map, e) => map.updated(e.taxId, e)
        }
        taxTable1.size === 2
        taxTable1(tax1.id.get).taxType === TaxType.OUTER_TAX
        taxTable1(tax1.id.get).rate === BigDecimal(5)
        taxTable1(tax1.id.get).targetAmount === BigDecimal(119)
        taxTable1(tax1.id.get).amount === BigDecimal(119 * 5 / 100)

        taxTable1(tax2.id.get).taxType === TaxType.INNER_TAX
        taxTable1(tax2.id.get).rate === BigDecimal(5)
        taxTable1(tax2.id.get).targetAmount === BigDecimal(1234)
        taxTable1(tax2.id.get).amount === BigDecimal(1234 * 5 / 105)

        val taxTable2 = ptran.taxTable(site2.id.get).foldLeft(immutable.LongMap[TransactionLogTax]()) {
          (map, e) => map.updated(e.taxId, e)
        }
        taxTable2.size === 2
        taxTable2(tax1.id.get).taxType === TaxType.OUTER_TAX
        taxTable2(tax1.id.get).rate === BigDecimal(5)
        taxTable2(tax1.id.get).targetAmount === BigDecimal(59)
        taxTable2(tax1.id.get).amount === BigDecimal(59 * 5 / 100)

        taxTable2(tax2.id.get).taxType === TaxType.INNER_TAX
        taxTable2(tax2.id.get).rate === BigDecimal(5)
        taxTable2(tax2.id.get).targetAmount === BigDecimal(2345)
        taxTable2(tax2.id.get).amount === BigDecimal(2345 * 5 / 105)

        ptran.itemTable.size === 2
        val itemTable1 = ptran.itemTable(site1.id.get)
          .foldLeft(
          immutable.HashMap[ItemId, (ItemName, TransactionLogItem, Option[TransactionLogCoupon])]()
        ) {
          (map, e) => map.updated(e._1.itemId, e)
        }
        itemTable1(item1.id.get)._1.name === "杉"
        itemTable1(item1.id.get)._2.itemId === item1.id.get.id
        itemTable1(item1.id.get)._2.itemPriceHistoryId === ph1.id.get
        itemTable1(item1.id.get)._2.quantity === 1
        itemTable1(item1.id.get)._2.amount === BigDecimal(119)

        val itemTable2 = ptran.itemTable(site2.id.get)
          .foldLeft(
          immutable.HashMap[ItemId, (ItemName, TransactionLogItem, Option[TransactionLogCoupon])]()
        ) {
          (map, e) => map.updated(e._1.itemId, e)
        }
        itemTable2(item2.id.get)._1.name === "梅"
        itemTable2(item2.id.get)._2.itemId === item2.id.get.id
        itemTable2(item2.id.get)._2.itemPriceHistoryId === ph2.id.get
        itemTable2(item2.id.get)._2.quantity === 1
        itemTable2(item2.id.get)._2.amount === BigDecimal(59)

        val credit: TransactionLogCreditTender = ptran.creditTable.get
        credit.transactionId === tranNo
        credit.amount === ptran.header.totalAmount + ptran.outerTaxGrandTotal
      }
    }

    "Site owners cannot modify transaction status of other site owneres." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val currency1 = currencyInfo.Jpy
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          1L, 2L, UserRole.ADMIN, Some("companyName2")
        )
        val siteUser2 = inject[SiteUserRepo].createNew(user2.id.get, site2.id.get)

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val tranSite1 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        val tranSite2 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site2.id.get, BigDecimal(345), BigDecimal(456)
        )

        val ship1 = TransactionShipStatus.createNew(
          tranSite1.id.get, TransactionStatus.ORDERED, Instant.now(), None
        )
        val ship2 = TransactionShipStatus.createNew(
          tranSite2.id.get, TransactionStatus.ORDERED, Instant.now(), None
        )

        TransactionShipStatus.byId(ship1.id.get).status === TransactionStatus.ORDERED
        TransactionShipStatus.byId(ship2.id.get).status === TransactionStatus.ORDERED

        TransactionShipStatus.update(
            Some(siteUser1), tranSite2.id.get, TransactionStatus.CANCELED
          )

        TransactionShipStatus.byId(ship1.id.get).status === TransactionStatus.ORDERED
        TransactionShipStatus.byId(ship2.id.get).status === TransactionStatus.ORDERED

        TransactionShipStatus.update(
          Some(siteUser1), tranSite1.id.get, TransactionStatus.CANCELED
        )

        TransactionShipStatus.byId(ship1.id.get).status === TransactionStatus.CANCELED
        TransactionShipStatus.byId(ship2.id.get).status === TransactionStatus.ORDERED
      }
    }

    "Can retrieve transaction item." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val currency1 = currencyInfo.Jpy
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val tranSite1 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        import localeInfo.{Ja}
        val tax1 = inject[TaxRepo].createNew
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 10L)
        ItemNumericMetadata.createNew(item2, ItemNumericMetadataType.HEIGHT, 20L)

        inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.STOCK, 123L
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item2.id.get, SiteItemNumericMetadataType.STOCK, 234L
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 0L
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item2.id.get, SiteItemNumericMetadataType.PROMOTION, 1L
        )

        val name1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))
          
        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site1)

        val ph1 = inject[ItemPriceHistoryRepo].createNew(
          price1, tax1, currencyInfo.Jpy, BigDecimal(119), None, BigDecimal(100), date("9999-12-31")
        )
        val ph2 = inject[ItemPriceHistoryRepo].createNew(
          price2, tax1, currencyInfo.Jpy, BigDecimal(59), None, BigDecimal(50), date("9999-12-31")
        )

        val tranItem1 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item1.id.get.id, price1.id.get, 3, BigDecimal(400 * 3), BigDecimal(300), 123L
        )
        val tranItem2 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item2.id.get.id, price2.id.get, 5, BigDecimal(700 * 5), BigDecimal(400), 234L
        )

        val detail = inject[TransactionDetailRepo].show(tranSite1.id.get, Ja, Some(siteUser1))
        detail.size === 2
        detail(0).itemName === "杉"
        detail(0).unitPrice === BigDecimal(400)
        detail(0).costUnitPrice === BigDecimal(300)
        detail(0).quantity === 3
        detail(0).itemNumericMetadata.size === 1
        detail(0).itemNumericMetadata(ItemNumericMetadataType.HEIGHT).metadata === 10L
        detail(0).siteItemNumericMetadata.size == 2
        detail(0).siteItemNumericMetadata(SiteItemNumericMetadataType.STOCK).metadata === 123L
        detail(0).siteItemNumericMetadata(SiteItemNumericMetadataType.SHIPPING_SIZE).metadata === 0L

        detail(1).itemName === "梅"
        detail(1).unitPrice === BigDecimal(700)
        detail(1).costUnitPrice === BigDecimal(400)
        detail(1).quantity === 5
        detail(1).itemNumericMetadata.size === 1
        detail(1).itemNumericMetadata(ItemNumericMetadataType.HEIGHT).metadata === 20L
        detail(1).siteItemNumericMetadata.size == 2
        detail(1).siteItemNumericMetadata(SiteItemNumericMetadataType.STOCK).metadata === 234L
        detail(1).siteItemNumericMetadata(SiteItemNumericMetadataType.PROMOTION).metadata === 1L

        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser2 = inject[SiteUserRepo].createNew(user2.id.get, site2.id.get)

        // Other site owner cannot see other owners' transaction.
        inject[TransactionDetailRepo].show(tranSite1.id.get, Ja, Some(siteUser2)).size === 0

        // Super user can see all transaction.
        val detail2 = inject[TransactionDetailRepo].show(tranSite1.id.get, Ja, None)
        detail2.size === 2
        detail2(0).itemName === "杉"
        detail2(0).unitPrice === BigDecimal(400)
        detail2(0).quantity === 3

        detail2(1).itemName === "梅"
        detail2(1).unitPrice === BigDecimal(700)
        detail2(1).quantity === 5
      }
    }

    "Can list shipping log by site." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val currency1 = currencyInfo.Jpy
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")

        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val tranSite1 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

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

        val tax = inject[TaxRepo].createNew

        val shipping1 = inject[TransactionLogShippingRepo].createNew(
          transactionSiteId = tranSite1.id.get,
          amount = BigDecimal(1000),
          costAmount = None,
          addressId = addr1.id.get,
          itemClass = 1L,
          boxSize = 3,
          taxId = tax.id.get,
          boxCount = 1,
          boxName = "boxName",
          shippingDate = date("2012-12-31")
        )

        val shipping2 = inject[TransactionLogShippingRepo].createNew(
          transactionSiteId = tranSite1.id.get,
          amount = BigDecimal(2000),
          costAmount = Some(1234),
          addressId = addr1.id.get,
          itemClass = 2L,
          boxSize = 5,
          taxId = tax.id.get,
          boxCount = 2,
          boxName = "boxName2",
          shippingDate = date("2011-12-31")
        )

        val list = inject[TransactionLogShippingRepo].listBySite(tranSite1.id.get)
        list.size === 2
        list(0) === shipping1
        list(1) === shipping2
      }
    }

    "Can retrieve transaction coupon item." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val currency1 = currencyInfo.Jpy
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val tranSite1 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        import localeInfo.{Ja}
        val tax1 = inject[TaxRepo].createNew
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)
        val item3 = inject[ItemRepo].createNew(cat1)

        val itemNumericMd1 = ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 10L)
        val itemNumericMd2 = ItemNumericMetadata.createNew(item2, ItemNumericMetadataType.HEIGHT, 20L)

        val siteItemNumricStock1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.STOCK, 123L
        )
        val siteItemNumericStock2 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item2.id.get, SiteItemNumericMetadataType.STOCK, 234L
        )
        val siteItemNumericShippingSize1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 0L
        )
        val siteItemNumericPromo1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item2.id.get, SiteItemNumericMetadataType.PROMOTION, 1L
        )

        val name1 = inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))
        val name3 = inject[ItemNameRepo].createNew(item3, Map(Ja -> "竹"))
          
        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site1)
        val price3 = inject[ItemPriceRepo].createNew(item3, site1)

        val ph1 = inject[ItemPriceHistoryRepo].createNew(
          price1, tax1, currencyInfo.Jpy, BigDecimal(119), None, BigDecimal(100), date("9999-12-31")
        )
        val ph2 = inject[ItemPriceHistoryRepo].createNew(
          price2, tax1, currencyInfo.Jpy, BigDecimal(59), None, BigDecimal(50), date("9999-12-31")
        )
        val ph3 = inject[ItemPriceHistoryRepo].createNew(
          price3, tax1, currencyInfo.Jpy, BigDecimal(39), None, BigDecimal(30), date("9999-12-31")
          )

        val tranItem1 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item1.id.get.id, price1.id.get, 3, BigDecimal(400 * 3), BigDecimal(300), 123L
        )
        val tranItem2 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item2.id.get.id, price2.id.get, 5, BigDecimal(700 * 5), BigDecimal(400), 234L
        )
        val tranItem3 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item3.id.get.id, price3.id.get, 5, BigDecimal(700 * 5), BigDecimal(400), 234L
        )

        val coupon1 = Coupon.createNew()
        val couponItem1 = CouponItem.create(item1.id.get, coupon1.id.get)
        val tranCoupon1 = inject[TransactionLogCouponRepo].createNew(tranItem1.id.get, coupon1.id.get)

        val coupon2 = Coupon.createNew()
        val couponItem2 = CouponItem.create(item2.id.get, coupon2.id.get)
        val tranCoupon2 = inject[TransactionLogCouponRepo].createNew(tranItem2.id.get, coupon2.id.get)

        val list = inject[TransactionLogCouponRepo].list(localeInfo.Ja, user1.id.get, 0)
        list.records.size === 2
        list.records(0) === CouponDetail(
          tranHeaderId = header.id.get,
          tranCouponId = tranCoupon2.id.get,
          site = site1,
          time = header.transactionTime,
          itemId = item2.id.get,
          itemName = "梅",
          couponId = coupon2.id.get
        )
        list.records(1) === CouponDetail(
          tranHeaderId = header.id.get,
          tranCouponId = tranCoupon1.id.get,
          site = site1,
          time = header.transactionTime,
          itemId = item1.id.get,
          itemName = "杉",
          couponId = coupon1.id.get
        )
      }
    }

    "Can persist metadata." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val currency1 = currencyInfo.Jpy
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )
        val tranSite1 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        import localeInfo.{Ja}
        val tax1 = inject[TaxRepo].createNew
        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)
        val itemNumericMd1 = ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 10L)
        val itemNumericMd2 = ItemNumericMetadata.createNew(item2, ItemNumericMetadataType.HEIGHT, 20L)
        val itemTextMd1 = ItemTextMetadata.createNew(item1, ItemTextMetadataType.ABOUT_HEIGHT, "ABC")
        val siteItemNumericMd1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.STOCK, 111
        )
        val siteItemNumericMd2 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.PROMOTION, 222
        )
        val siteItemTextMd1 = SiteItemTextMetadata.createNew(
          site1.id.get, item1.id.get, SiteItemTextMetadataType.PRICE_MEMO, "AAA"
        )
        val siteItemTextMd2 = SiteItemTextMetadata.createNew(
          site1.id.get, item2.id.get, SiteItemTextMetadataType.LIST_PRICE_MEMO, "BBB"
        )
        val siteItemTextMd3 = SiteItemTextMetadata.createNew(
          site1.id.get, item2.id.get, SiteItemTextMetadataType.PRICE_MEMO, "CCC"
        )
        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site1)
        val tranItem1 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item1.id.get.id, price1.id.get, 3, BigDecimal(400 * 3), BigDecimal(300), 123L
        )
        val tranItem2 = inject[TransactionLogItemRepo].createNew(
          tranSite1.id.get, item2.id.get.id, price1.id.get, 5, BigDecimal(500 * 3), BigDecimal(400), 234L
        )

        TransactionLogItemNumericMetadata.createNew(
          tranItem1.id.get, Seq(itemNumericMd1)
        )
        TransactionLogItemNumericMetadata.createNew(
          tranItem2.id.get, Seq(itemNumericMd2)
        )

        TransactionLogItemTextMetadata.createNew(
          tranItem1.id.get, Seq(itemTextMd1)
        )

        TransactionLogSiteItemNumericMetadata.createNew(
          tranItem1.id.get, Seq(siteItemNumericMd1, siteItemNumericMd2)
        )

        TransactionLogSiteItemTextMetadata.createNew(
          tranItem1.id.get, Seq(siteItemTextMd1)
        )
        TransactionLogSiteItemTextMetadata.createNew(
          tranItem2.id.get, Seq(siteItemTextMd2, siteItemTextMd3)
        )

        doWith(TransactionLogItemNumericMetadata.list(tranItem1.id.get)) { mds =>
          mds.size === 1
          mds.head.transactionItemId === tranItem1.id.get
          mds.head.metadataType === ItemNumericMetadataType.HEIGHT
          mds.head.metadata === 10
        }
        doWith(TransactionLogItemNumericMetadata.list(tranItem2.id.get)) { mds =>
          mds.size === 1
          mds.head.transactionItemId === tranItem2.id.get
          mds.head.metadataType === ItemNumericMetadataType.HEIGHT
          mds.head.metadata === 20
        }
        doWith(TransactionLogItemTextMetadata.list(tranItem1.id.get)) { mds =>
          mds.size === 1
          mds.head.transactionItemId === tranItem1.id.get
          mds.head.metadataType === ItemTextMetadataType.ABOUT_HEIGHT
          mds.head.metadata === "ABC"
        }

        doWith(TransactionLogSiteItemNumericMetadata.list(tranItem1.id.get)) { mds =>
          mds.size === 2
          mds(0).transactionItemId === tranItem1.id.get
          mds(0).metadataType === SiteItemNumericMetadataType.STOCK
          mds(0).metadata === 111
          mds(1).transactionItemId === tranItem1.id.get
          mds(1).metadataType === SiteItemNumericMetadataType.PROMOTION
          mds(1).metadata === 222
        }

        doWith(TransactionLogSiteItemTextMetadata.list(tranItem1.id.get)) { mds =>
          mds.size === 1
          mds(0).transactionItemId === tranItem1.id.get
          mds(0).metadataType === SiteItemTextMetadataType.PRICE_MEMO
          mds(0).metadata === "AAA"
        }
        doWith(TransactionLogSiteItemTextMetadata.list(tranItem2.id.get)) { mds =>
          mds.size === 2
          mds(0).transactionItemId === tranItem2.id.get
          mds(0).metadataType === SiteItemTextMetadataType.PRICE_MEMO
          mds(0).metadata === "CCC"
          mds(1).transactionItemId === tranItem2.id.get
          mds(1).metadataType === SiteItemTextMetadataType.LIST_PRICE_MEMO
          mds(1).metadata === "BBB"
        }
      }
    }

    "Can persist shipping/delivery date." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val currency1 = currencyInfo.Jpy
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )
        val tranSite1 = inject[TransactionLogSiteRepo].createNew(
          header.id.get, site1.id.get, BigDecimal(234), BigDecimal(345)
        )

        val status: TransactionShipStatus = TransactionShipStatus.createNew(
          tranSite1.id.get, TransactionStatus.ORDERED, Instant.now(), None
        )

        TransactionShipStatus.updateShippingDeliveryDate(
          None, tranSite1.id.get, Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
        ) === 1

        doWith(TransactionShipStatus.byId(status.id.get)) { updated =>
          updated.plannedShippingDate === Some(Instant.ofEpochMilli(123L))
          updated.plannedDeliveryDate === Some(Instant.ofEpochMilli(234L))
        }
      }
    }

    "Can update transaction log paypal status." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val currency1 = currencyInfo.Jpy
        val now = 1234L

        val header = TransactionLogHeader.createNew(
          user1.id.get, currency1.id,
          BigDecimal(234), BigDecimal(345),
          TransactionTypeCode.ACCOUNTING_BILL
        )

        val ps: TransactionLogPaypalStatus = TransactionLogPaypalStatus.createNew(
          header.id.get, PaypalStatus.START, PaypalPaymentType.EXPRESS_CHECKOUT, 123L
        )

        ps === TransactionLogPaypalStatus.byId(ps.id.get)
        TransactionLogPaypalStatus.update(header.id.get, PaypalStatus.COMPLETED)
        ps.copy(status = PaypalStatus.COMPLETED) === TransactionLogPaypalStatus.byId(ps.id.get)
      }
    }
  }
}

