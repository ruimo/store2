package models

import org.specs2.mutable._

import java.time.Instant
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ShippingSpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "Shipping" should {
    "Can create shipping fee record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val itemClass1 = 1L

        val box = inject[ShippingBoxRepo].createNew(
          site1.id.get, itemClass1, 10, "小箱"
        )
        val shipping = inject[ShippingFeeRepo].createNew(
          box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code
        )

        inject[ShippingFeeRepo].apply(box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code) === shipping
      }
    }

    "Can create shipping fee history." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val itemClass1 = 1L
        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val box = inject[ShippingBoxRepo].createNew(
          site1.id.get, itemClass1, 10, "小箱"
        )
        val shipping = inject[ShippingFeeRepo].createNew(
          box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code
        )
        val history1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping.id.get, tax.id.get, BigDecimal(1234), Some(BigDecimal(1111)), date("9999-12-31")
        )

        val list = inject[ShippingFeeHistoryRepo].list(shipping.id.get)
        list.size === 1
        list(0) === history1

        inject[ShippingFeeHistoryRepo].at(shipping.id.get).fee === BigDecimal(1234)
        inject[ShippingFeeHistoryRepo].at(shipping.id.get).costFee === Some(BigDecimal(1111))
          
        val history2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping.id.get, tax.id.get, BigDecimal(2345), None, date("2013-12-31")
        )

        val list2 = inject[ShippingFeeHistoryRepo].list(shipping.id.get)
        list2.size === 2
        list2(0) === history2
        list2(1) === history1

        inject[ShippingFeeHistoryRepo].at(shipping.id.get, date("2013-12-30")).fee === BigDecimal(2345)
        inject[ShippingFeeHistoryRepo].at(shipping.id.get, date("2013-12-30")).costFee === None
        inject[ShippingFeeHistoryRepo].at(shipping.id.get, date("2013-12-31")).fee === BigDecimal(1234)
        inject[ShippingFeeHistoryRepo].at(shipping.id.get, date("2013-12-31")).costFee === Some(BigDecimal(1111))
      }
    }

    "Can list by country and location." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val itemClass1 = 1L

        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val box = inject[ShippingBoxRepo].createNew(
          site1.id.get, itemClass1, 10, "小箱"
        )
        val shipping1 = inject[ShippingFeeRepo].createNew(
          box.id.get, CountryCode.JPN, JapanPrefecture.東京都.code
        )
        val history1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1.id.get, tax.id.get, BigDecimal(1234), None, date("9999-12-31")
        )
        val history2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1.id.get, tax.id.get, BigDecimal(2345), Some(BigDecimal(2222)), date("2013-12-31")
        )

        val shipping2 = inject[ShippingFeeRepo].createNew(
          box.id.get, CountryCode.JPN, JapanPrefecture.埼玉県.code
        )
        val history3 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2.id.get, tax.id.get, BigDecimal(9999), None, date("9999-12-31")
        )
        val history4 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2.id.get, tax.id.get, BigDecimal(8888), Some(BigDecimal(8000)), date("2013-12-31")
        )

        val list = inject[ShippingFeeHistoryRepo].listByLocation(CountryCode.JPN, JapanPrefecture.東京都.code)
        list.size === 2
        list(0) === (shipping1, history2)
        list(1) === (shipping1, history1)

        val list2 = inject[ShippingFeeHistoryRepo].listByLocation(CountryCode.JPN, JapanPrefecture.埼玉県.code)
        list2.size === 2
        list2(0) === (shipping2, history4)
        list2(1) === (shipping2, history3)

        inject[ShippingFeeHistoryRepo].listByLocation(CountryCode.JPN, JapanPrefecture.三重県.code).size === 0
      }
    }

    "Can list by country and location." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val itemClass1 = 1L
        val itemClass2 = 2L

        val tax = inject[TaxRepo].createNew
        val taxHistory = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val box1_1 = inject[ShippingBoxRepo].createNew(site1.id.get, itemClass1, 10, "小箱1")
        val box1_2 = inject[ShippingBoxRepo].createNew(site1.id.get, itemClass2, 5, "中箱1")

        val box2_1 = inject[ShippingBoxRepo].createNew(site2.id.get, itemClass1, 7, "小箱2")
        val box2_2 = inject[ShippingBoxRepo].createNew(site2.id.get, itemClass2, 3, "中箱2")

        val shipping1_1_tokyo = inject[ShippingFeeRepo].createNew(box1_1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping1_1_saitama = inject[ShippingFeeRepo].createNew(box1_1.id.get, CountryCode.JPN, JapanPrefecture.埼玉県.code)
        val shipping1_2_tokyo = inject[ShippingFeeRepo].createNew(box1_2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping1_2_saitama = inject[ShippingFeeRepo].createNew(box1_2.id.get, CountryCode.JPN, JapanPrefecture.埼玉県.code)

        val shipping2_1_tokyo = inject[ShippingFeeRepo].createNew(box2_1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping2_1_saitama = inject[ShippingFeeRepo].createNew(box2_1.id.get, CountryCode.JPN, JapanPrefecture.埼玉県.code)
        val shipping2_2_tokyo = inject[ShippingFeeRepo].createNew(box2_2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping2_2_saitama = inject[ShippingFeeRepo].createNew(box2_2.id.get, CountryCode.JPN, JapanPrefecture.埼玉県.code)

        val history1_1_tokyo_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_1_tokyo.id.get, tax.id.get, BigDecimal(1234), None, date("9999-12-31")
        )
        val history1_1_tokyo_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_1_tokyo.id.get, tax.id.get, BigDecimal(1000), Some(BigDecimal(900)), date("2013-12-31")
        )

        val history1_1_saitama_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_1_saitama.id.get, tax.id.get, BigDecimal(12), None, date("9999-12-31")
        )
        val history1_1_saitama_2 = inject[ShippingFeeHistoryRepo].createNew(
            shipping1_1_saitama.id.get, tax.id.get, BigDecimal(34), Some(BigDecimal(20)), date("2013-12-31")
        )

        val history1_2_tokyo_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_2_tokyo.id.get, tax.id.get, BigDecimal(234), None, date("9999-12-31")
        )
        val history1_2_tokyo_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_2_tokyo.id.get, tax.id.get, BigDecimal(345), Some(BigDecimal(300)), date("2013-12-31")
        )
          
        val history1_2_saitama_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_2_saitama.id.get, tax.id.get, BigDecimal(112), Some(BigDecimal(100)), date("9999-12-31")
        )
        val history1_2_saitama_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping1_2_saitama.id.get, tax.id.get, BigDecimal(221), None, date("2013-12-31")
        )

        val history2_1_tokyo_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_1_tokyo.id.get, tax.id.get, BigDecimal(222), Some(BigDecimal(200)), date("9999-12-31")
        )
        val history2_1_tokyo_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_1_tokyo.id.get, tax.id.get, BigDecimal(333), None, date("2013-12-31")
        )

        val history2_1_saitama_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_1_saitama.id.get, tax.id.get, BigDecimal(999), None, date("9999-12-31")
        )
        val history2_1_saitama_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_1_saitama.id.get, tax.id.get, BigDecimal(888), None, date("2013-12-31")
        )

        val history2_2_tokyo_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_2_tokyo.id.get, tax.id.get, BigDecimal(9999), Some(BigDecimal(9000)), date("9999-12-31")
        )
        val history2_2_tokyo_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_2_tokyo.id.get, tax.id.get, BigDecimal(8888), Some(BigDecimal(8000)), date("2013-12-31")
        )

        val history2_2_saitama_1 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_2_saitama.id.get, tax.id.get, BigDecimal(7777), Some(BigDecimal(7000)), date("9999-12-31")
        )
        val history2_2_saitama_2 = inject[ShippingFeeHistoryRepo].createNew(
          shipping2_2_saitama.id.get, tax.id.get, BigDecimal(6666), None, date("2013-12-31")
        )

        val map1 = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 7)
            .add(site1, itemClass1, 8)
            .add(site1, itemClass1, 4),
          date("2013-12-30")
        )

        map1.size === 1
        val bySite1 = map1.bySite(site1)
        bySite1.table.size === 1
        val info1 = bySite1.byItemClass(itemClass1).table(0)
        info1.shippingBox.boxName === "小箱1"
        info1.itemQuantity === 19
        info1.boxQuantity === 2
        info1.boxUnitPrice === history1_1_tokyo_2.fee

        val map2 = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 7)
            .add(site1, itemClass1, 8)
            .add(site1, itemClass1, 4)
            .add(site1, itemClass2, 4),
          date("2013-12-31")
        )

        map2.size === 2
        val bySite2 = map2.bySite(site1)
        bySite2.size === 2
        val info2 = bySite2.byItemClass(itemClass2).table(0)
        info2.shippingBox.boxName === "中箱1"
        info2.itemQuantity === 4
        info2.boxQuantity === 1
        info2.boxUnitPrice === history1_2_tokyo_1.fee

        val map3 = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.埼玉県.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 7)
            .add(site1, itemClass1, 8)
            .add(site1, itemClass1, 4)
            .add(site1, itemClass2, 4)
            .add(site2, itemClass1, 20)
            .add(site2, itemClass1, 1)
            .add(site2, itemClass2, 6),
          date("2013-12-31")
        )

        map3.size === 4
        val bySite3 = map3.bySite(site1)
        bySite3.size === 2
        val info3 = bySite3.byItemClass(itemClass1).table(0)
        info3.shippingBox.boxName === "小箱1"
        info3.itemQuantity === 19
        info3.boxQuantity === 2
        info3.boxUnitPrice === history1_1_saitama_1.fee

        val info4 = bySite3.byItemClass(itemClass2).table(0)
        info4.shippingBox.boxName === "中箱1"
        info4.itemQuantity === 4
        info4.boxQuantity === 1
        info4.boxUnitPrice === history1_2_saitama_1.fee

        map3.bySite(site2).size === 2
        val info5 = map3.bySite(site2).byItemClass(itemClass1).table(0)
        info5.shippingBox.boxName === "小箱2"
        info5.itemQuantity === 21
        info5.boxQuantity === 3
        info5.boxUnitPrice === history2_1_saitama_1.fee

        val info6 = map3.bySite(site2).byItemClass(itemClass2).table(0)
        info6.shippingBox.boxName === "中箱2"
        info6.itemQuantity === 6
        info6.boxQuantity === 2
        info6.boxUnitPrice === history2_2_saitama_1.fee
      }
    }

    "Tax by tax type can be obtained from shipping total." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      val site1 = Site(Some(1L), 1L, "site1")
      val site2 = Site(Some(2L), 1L, "site2")
      val itemClass1 = 1L
      val itemClass2 = 2L
      val box1 = ShippingBox(Some(1L), site1.id.get, itemClass1, 5, "box1")
      val fee1 = ShippingFee(Some(1L), box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
      val fee2 = ShippingFee(Some(2L), box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
      val fee3 = ShippingFee(Some(3L), box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
      val fee4 = ShippingFee(Some(4L), box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
      val taxId1 = 1L
      val taxId2 = 2L
      val taxId3 = 3L
      val taxId4 = 4L
      val taxHis1 = TaxHistory(Some(1L), taxId1, TaxType.OUTER_TAX, BigDecimal(8), date("2013-12-31"))
      val taxHis2 = TaxHistory(Some(2L), taxId2, TaxType.OUTER_TAX, BigDecimal(8), date("2013-12-31"))
      val taxHis3 = TaxHistory(Some(3L), taxId3, TaxType.INNER_TAX, BigDecimal(8), date("2013-12-31"))
      val taxHis4 = TaxHistory(Some(4L), taxId4, TaxType.NON_TAX, BigDecimal(0), date("2013-12-31"))

      implicit val taxRepo = inject[TaxRepo]
      val total = ShippingTotal(
        List(
          ShippingTotalEntry(
            site1, itemClass1, box1, fee1, 3, 1, BigDecimal(12), Some(10), taxHis1
          ),
          ShippingTotalEntry(
            site1, itemClass2, box1, fee2, 5, 2, BigDecimal(23), None, taxHis2
          ),
          ShippingTotalEntry(
            site2, itemClass1, box1, fee3, 2, 3, BigDecimal(34), Some(30), taxHis3
          ),
          ShippingTotalEntry(
            site2, itemClass2, box1, fee4, 4, 4, BigDecimal(45), None, taxHis4
          )
        )
      )

      val byType = total.taxByType
      byType.size === 3
      byType(TaxType.OUTER_TAX) === BigDecimal(8 * 12 / 100 + 8 * 23 * 2 / 100)
      byType(TaxType.INNER_TAX) === BigDecimal(8 * 34 * 3 / 108)
      byType(TaxType.NON_TAX) === BigDecimal(0)
    }

    "Can list shipping box." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val itemClass1 = 1L
        val itemClass2 = 2L
      
        val box1 = inject[ShippingBoxRepo].createNew(
          site1.id.get, itemClass1, 10, "小箱1"
        )
        val box2 = inject[ShippingBoxRepo].createNew(
          site1.id.get, itemClass2, 11, "小箱2"
        )
        val box3 = inject[ShippingBoxRepo].createNew(
          site2.id.get, itemClass1, 12, "小箱3"
        )

        val listBySite1 = inject[ShippingBoxRepo].list(site1.id.get)
        listBySite1.size === 2
        listBySite1(0) === box1
        listBySite1(1) === box2

        val listBySite2 = inject[ShippingBoxRepo].list(site2.id.get)
        listBySite2.size === 1
        listBySite2(0) === box3

        val list = inject[ShippingBoxRepo].list
        list.size === 3
      }
    }

    "Can list shipping fee." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val itemClass1 = 1L
      
        val box1 = inject[ShippingBoxRepo].createNew(
          site1.id.get, itemClass1, 10, "小箱1"
        )

        val fee1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code())
        val fee2 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.千葉県.code())

        val tax1 = inject[TaxRepo].createNew
        val his1 = inject[ShippingFeeHistoryRepo].createNew(
          fee1.id.get, tax1.id.get, BigDecimal("100"), Some(BigDecimal(50)), date("2013-12-31")
        )
        val his2 = inject[ShippingFeeHistoryRepo].createNew(
          fee1.id.get, tax1.id.get, BigDecimal("200"), None, date("9999-12-31")
        )

        val list1 = inject[ShippingFeeRepo].listWithHistory(box1.id.get, date("2013-12-30"))
        list1.size === 2
        list1(0)._1.shippingBoxId === box1.id.get
        list1(0)._1.countryCode === CountryCode.JPN
        list1(0)._1.locationCode === JapanPrefecture.東京都.code()
        list1(0)._2.get.shippingFeeId === fee1.id.get
        list1(0)._2.get.taxId === tax1.id.get
        list1(0)._2.get.fee === BigDecimal("100")
        list1(0)._2.get.validUntil === date("2013-12-31")

        list1(1)._1.shippingBoxId === box1.id.get
        list1(1)._1.countryCode === CountryCode.JPN
        list1(1)._1.locationCode === JapanPrefecture.千葉県.code()
        list1(1)._2 === None

        val list2 = inject[ShippingFeeRepo].listWithHistory(box1.id.get, date("2013-12-31"))
        list2.size === 2
        list2(0)._1.shippingBoxId === box1.id.get
        list2(0)._1.countryCode === CountryCode.JPN
        list2(0)._1.locationCode === JapanPrefecture.東京都.code()
        list2(0)._2.get.shippingFeeId === fee1.id.get
        list2(0)._2.get.taxId === tax1.id.get
        list2(0)._2.get.fee === BigDecimal("200")
        list2(0)._2.get.validUntil === date("9999-12-31")

        list2(1)._1.shippingBoxId === box1.id.get
        list2(1)._1.countryCode === CountryCode.JPN
        list2(1)._1.locationCode === JapanPrefecture.千葉県.code()
        list2(1)._2 === None
      }
    }

    "Can create shopping cart shippping." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )

        ShoppingCartShipping.updateOrInsert(user1.id.get, site1.id.get, date("2015-10-11"))
        ShoppingCartShipping.updateOrInsert(user1.id.get, site2.id.get, date("2015-10-11"))

        ShoppingCartShipping.find(user1.id.get) === Some(date("2015-10-11"))
      }
    }
  }
}
