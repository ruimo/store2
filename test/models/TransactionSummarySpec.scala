package models

import scala.collection.immutable
import org.specs2.mutable.Specification
import play.api.test._
import play.api.test.Helpers._
import com.ruimo.scoins.Scoping._
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.Database
import java.time.Instant

class TransactionSummarySpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "TransactionSummary" should {
    "Can list summary" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew
        inject[TaxHistoryRepo].createNew(tax1, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        inject[TaxHistoryRepo].createNew(tax2, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val user1 = inject[StoreUserRepo].create(
          "name1", "first1", None, "last1", "email1", 123L, 234L, UserRole.NORMAL, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "name2", "first2", None, "last2", "email2", 123L, 234L, UserRole.NORMAL, Some("companyName2")
        )
        import localeInfo.Ja
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")

        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉"))
        inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)

        inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        inject[ItemDescriptionRepo].createNew(item2, site1, "梅説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)

        inject[ItemPriceHistoryRepo].createNew(
          price1, tax1, currencyInfo.Jpy, BigDecimal(119), None, BigDecimal(100), date("9999-12-31")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price2, tax1, currencyInfo.Jpy, BigDecimal(59), None, BigDecimal(50), date("9999-12-31")
        )

        inject[ShoppingCartItemRepo].addItem(user1.id.get, site1.id.get, item1.id.get.id, 1)
        inject[ShoppingCartItemRepo].addItem(user1.id.get, site2.id.get, item2.id.get.id, 1)

        inject[ShoppingCartItemRepo].addItem(user2.id.get, site1.id.get, item1.id.get.id, 2)

        val itemClass1 = 1L

        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, itemClass1, 10, "小箱")
        val box2 = inject[ShippingBoxRepo].createNew(site2.id.get, itemClass1, 3, "小箱")
        val shipping1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping2 = inject[ShippingFeeRepo].createNew(box2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        inject[ShippingFeeHistoryRepo].createNew(
          shipping1.id.get, tax2.id.get, BigDecimal(1234), Some(BigDecimal(1000)), date("9999-12-31")
        )
        inject[ShippingFeeHistoryRepo].createNew(
          shipping2.id.get, tax2.id.get, BigDecimal(2345), None, date("9999-12-31")
        )

        inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 3)
            .add(site2, itemClass1, 5)
        )

        implicit val storeUserRepo = inject[StoreUserRepo]
        val cart1 = inject[ShoppingCartItemRepo].listItemsForUser(
          Ja,
          LoginSession(user1, None, 0)
        )._1
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

        val cart2 = inject[ShoppingCartItemRepo].listItemsForUser(
          Ja,
          LoginSession(user2, None, 0)
        )._1
        val addr2 = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "FirstName2",
          lastName = "LastName2",
          zip1 = "123",
          prefecture = JapanPrefecture.東京都,
          address1 = "Address21",
          address2 = "Address22",
          tel1 = "1234567890"
        )

        val shippingDate1 = ShippingDate(
          Map(
            site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-03")),
            site2.id.get -> ShippingDateEntry(site2.id.get, date("2013-05-03"))
          )
        )
        val shippingDate2 = ShippingDate(
          Map(
            site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-04"))
          )
        )
        val persister = inject[TransactionPersister]
        implicit val taxRepo = inject[TaxRepo]
        val t = persister.persist(
          Transaction(user1.id.get, currencyInfo.Jpy, cart1, Some(addr1),
            inject[controllers.Shipping].shippingFee(addr1, cart1), shippingDate1)
        )
        val tranNo1: Long = t._1
        val taxesBySite1: immutable.Map[Site, immutable.Seq[TransactionLogTax]] = t._2

        val ptran1 = persister.load(tranNo1, Ja)
        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)
        val summary1 = inject[TransactionSummary].list(Some(siteUser1.siteId)).records
        summary1.size === 1
        val entry1 = summary1.head
        entry1.transactionId === tranNo1
        entry1.transactionTime === ptran1.header.transactionTime
        entry1.totalAmount === BigDecimal(119 + 1234)
        entry1.address === Some(addr1.copy(email = user1.email))
        entry1.siteName === "商店1"
        entry1.shippingFee === BigDecimal(1234)
        entry1.status === TransactionStatus.ORDERED

        val sum1 = inject[TransactionSummary].get(Some(siteUser1.siteId), entry1.transactionSiteId)
        sum1.isDefined === true

        val t2 = persister.persist(
          Transaction(user2.id.get, currencyInfo.Jpy, cart2, Some(addr2),
            inject[controllers.Shipping].shippingFee(addr2, cart2), shippingDate2)
        )
        val tranNo2: Long = t2._1
        val taxesBySite2: immutable.Map[Site, immutable.Seq[TransactionLogTax]] = t2._2

        val ptran2 = persister.load(tranNo2, Ja)
        val siteUser2 = inject[SiteUserRepo].createNew(user1.id.get, site2.id.get)
        doWith(inject[TransactionSummary].list(Some(siteUser1.siteId)).records) { s =>
          s.size === 2
          doWith(s(0)) { e =>
            e.transactionId === tranNo2
            e.transactionTime === ptran2.header.transactionTime
            e.totalAmount === BigDecimal(119 * 2 + 1234)
            e.address === Some(addr2.copy(email = user2.email))
            e.siteName === "商店1"
            e.shippingFee === BigDecimal(1234)
            e.status === TransactionStatus.ORDERED
          }

          doWith(s(1)) { e =>
            e.transactionId === tranNo1
            e.transactionTime === ptran1.header.transactionTime
            e.totalAmount === BigDecimal(119 + 1234)
            e.address === Some(addr1.copy(email = user1.email))
            e.siteName === "商店1"
            e.shippingFee === BigDecimal(1234)
            e.status === TransactionStatus.ORDERED
          }
        }

        doWith(inject[TransactionSummary].list(Some(siteUser2.siteId)).records) { s =>
          s.size === 1
          doWith(s(0)) { e =>
            e.transactionId === tranNo1
            e.transactionTime === ptran1.header.transactionTime
              e.totalAmount === BigDecimal(59 + 2345)
            e.address === Some(addr1.copy(email = user1.email))
            e.siteName === "商店2"
            e.shippingFee === BigDecimal(2345)
            e.status === TransactionStatus.ORDERED
          }
        }

        doWith(inject[TransactionSummary].list(storeUserId = Some(user1.id.get)).records) { s =>
          s.size === 2
          doWith(s.map { ele => (ele.siteName, ele) }.toMap) { map =>
            doWith(map("商店1")) { e =>
              e.transactionId === tranNo1
              e.transactionTime === ptran1.header.transactionTime
              e.totalAmount === BigDecimal(119 + 1234)
              e.address === Some(addr1.copy(email = user1.email))
              e.siteName === "商店1"
              e.shippingFee === BigDecimal(1234)
              e.status === TransactionStatus.ORDERED
            }

            doWith(map("商店2")) { e =>
              e.transactionId === tranNo1
              e.transactionTime === ptran1.header.transactionTime
              e.totalAmount === BigDecimal(59 + 2345)
              e.address === Some(addr1.copy(email = user1.email))
              e.siteName === "商店2"
              e.shippingFee === BigDecimal(2345)
              e.status === TransactionStatus.ORDERED
            }
          }
        }

        doWith(inject[TransactionSummary].list(storeUserId = Some(user2.id.get)).records) { s =>
          s.size === 1
          doWith(s(0)) { e =>
            e.transactionId === tranNo2
            e.transactionTime === ptran2.header.transactionTime
            e.totalAmount === BigDecimal(119 * 2 + 1234)
            e.address === Some(addr2.copy(email = user2.email))
            e.siteName === "商店1"
            e.shippingFee === BigDecimal(1234)
            e.status === TransactionStatus.ORDERED
          }
        }

        doWith(inject[TransactionSummary].list(tranId = Some(tranNo1)).records) { s =>
          s.size === 2
          doWith(s.map { ele => (ele.siteName, ele) }.toMap) { map =>
            doWith(map("商店1")) { e =>
              e.transactionId === tranNo1
              e.transactionTime === ptran1.header.transactionTime
              e.totalAmount === BigDecimal(119 + 1234)
              e.address === Some(addr1.copy(email = user1.email))
              e.siteName === "商店1"
              e.shippingFee === BigDecimal(1234)
              e.status === TransactionStatus.ORDERED
            }

            doWith(map("商店2")) { e =>
              e.transactionId === tranNo1
              e.transactionTime === ptran1.header.transactionTime
              e.totalAmount === BigDecimal(59 + 2345)
              e.address === Some(addr1.copy(email = user1.email))
              e.siteName === "商店2"
              e.shippingFee === BigDecimal(2345)
              e.status === TransactionStatus.ORDERED
            }
          }
        }

        doWith(inject[TransactionSummary].list(tranId = Some(tranNo2)).records) { s =>
          s.size === 1
          doWith(s(0)) { e =>
            e.transactionId === tranNo2
            e.transactionTime === ptran2.header.transactionTime
            e.totalAmount === BigDecimal(119 * 2 + 1234)
            e.address === Some(addr2.copy(email = user2.email))
            e.siteName === "商店1"
            e.shippingFee === BigDecimal(1234)
            e.status === TransactionStatus.ORDERED
          }
        }
      }
    }

    "Can listByPeriod summary" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew
        inject[TaxHistoryRepo].createNew(tax1, TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31"))
        inject[TaxHistoryRepo].createNew(tax2, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val user1 = inject[StoreUserRepo].create(
          "name1", "first1", None, "last1", "email1", 123L, 234L, UserRole.NORMAL, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "name2", "first2", None, "last2", "email2", 123L, 234L, UserRole.NORMAL, Some("companyName2")
        )
        import localeInfo.Ja
        val site1 = inject[SiteRepo].createNew(Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(Ja, "商店2")

        val cat1 = inject[CategoryRepo].createNew(Map(Ja -> "植木"))

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        inject[ItemNameRepo].createNew(item1, Map(Ja -> "杉"))
        inject[ItemNameRepo].createNew(item2, Map(Ja -> "梅"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item2)

        inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        inject[ItemDescriptionRepo].createNew(item2, site1, "梅説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)

        inject[ItemPriceHistoryRepo].createNew(
          price1, tax1, currencyInfo.Jpy, BigDecimal(119), None, BigDecimal(100), date("9999-12-31")
        )
        inject[ItemPriceHistoryRepo].createNew(
            price2, tax1, currencyInfo.Jpy, BigDecimal(59), None, BigDecimal(50), date("9999-12-31")
        )

        inject[ShoppingCartItemRepo].addItem(user1.id.get, site1.id.get, item1.id.get.id, 1)
        inject[ShoppingCartItemRepo].addItem(user1.id.get, site2.id.get, item2.id.get.id, 1)

        inject[ShoppingCartItemRepo].addItem(user2.id.get, site1.id.get, item1.id.get.id, 2)

        val itemClass1 = 1L

        val box1 = inject[ShippingBoxRepo].createNew(site1.id.get, itemClass1, 10, "小箱")
        val box2 = inject[ShippingBoxRepo].createNew(site2.id.get, itemClass1, 3, "小箱")
        val shipping1 = inject[ShippingFeeRepo].createNew(box1.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        val shipping2 = inject[ShippingFeeRepo].createNew(box2.id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
        inject[ShippingFeeHistoryRepo].createNew(
          shipping1.id.get, tax2.id.get, BigDecimal(1234), None, date("9999-12-31")
        )
        inject[ShippingFeeHistoryRepo].createNew(
          shipping2.id.get, tax2.id.get, BigDecimal(2345), Some(BigDecimal(2000)), date("9999-12-31")
        )

        inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
          CountryCode.JPN, JapanPrefecture.東京都.code,
          ShippingFeeEntries()
            .add(site1, itemClass1, 3)
            .add(site2, itemClass1, 5)
        )

        implicit val storeUserRepo = inject[StoreUserRepo]
        val cart1 = inject[ShoppingCartItemRepo].listItemsForUser(
          Ja,
          LoginSession(user1, None, 0)
        )._1
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

        val cart2 = inject[ShoppingCartItemRepo].listItemsForUser(
          Ja,
          LoginSession(user2, None, 0)
        )._1
        val addr2 = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "FirstName2",
          lastName = "LastName2",
          zip1 = "123",
          prefecture = JapanPrefecture.東京都,
          address1 = "Address21",
          address2 = "Address22",
          tel1 = "1234567890"
        )

        val shippingDate1 = ShippingDate(
          Map(
            site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-03")),
            site2.id.get -> ShippingDateEntry(site2.id.get, date("2013-05-03"))
          )
        )
        val shippingDate2 = ShippingDate(
          Map(
            site1.id.get -> ShippingDateEntry(site1.id.get, date("2013-02-04"))
          )
        )
        val persister = inject[TransactionPersister]
        implicit val taxRepo = inject[TaxRepo]
        val t = persister.persist(
          Transaction(
            user1.id.get, currencyInfo.Jpy, cart1, Some(addr1),
            inject[controllers.Shipping].shippingFee(addr1, cart1), shippingDate1,
            now = date("2013-01-31")
          )
        )
        val tranNo1: Long = t._1
        val taxesBySite1: immutable.Map[Site, immutable.Seq[TransactionLogTax]] = t._2

        val t2 = persister.persist(
          Transaction(
            user2.id.get, currencyInfo.Jpy, cart2, Some(addr2),
            inject[controllers.Shipping].shippingFee(addr2, cart2), shippingDate2,
            now = date("2013-03-01")
          )
        )
        val tranNo2: Long = t2._1
        val taxesBySite2: immutable.Map[Site, immutable.Seq[TransactionLogTax]] = t2._2

        val ptran1 = persister.load(tranNo1, Ja)
        val ptran2 = persister.load(tranNo2, Ja)
        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)
        val siteUser2 = inject[SiteUserRepo].createNew(user1.id.get, site2.id.get)
        doWith(inject[TransactionSummary].listByPeriod(siteId = Some(siteUser1.siteId), yearMonth = YearMonth(2013, 1, "show"))) { s =>
          s.size === 1
          doWith(s(0)) { e =>
            e.transactionId === tranNo1
            e.transactionTime === ptran1.header.transactionTime
            e.totalAmount === BigDecimal(119 + 1234)
            e.address === Some(addr1.copy(email = user1.email))
            e.siteName === "商店1"
            e.shippingFee === BigDecimal(1234)
            e.status === TransactionStatus.ORDERED
          }
        }

        doWith(inject[TransactionSummary].listByPeriod(siteId = Some(siteUser1.siteId), yearMonth = YearMonth(2013, 3, "show"))) { s =>
          s.size === 1
          doWith(s(0)) { e =>
            e.transactionId === tranNo2
            e.transactionTime === ptran2.header.transactionTime
            e.totalAmount === BigDecimal(119 * 2 + 1234)
            e.address === Some(addr2.copy(email = user2.email))
            e.siteName === "商店1"
            e.shippingFee === BigDecimal(1234)
            e.status === TransactionStatus.ORDERED
          }
        }
      }
    }
  }
}
