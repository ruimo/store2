package models

import org.specs2.mutable._

import java.sql.Connection
import java.time.Instant
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import com.ruimo.scoins.Scoping._

import helpers.QueryString
import helpers.{CategoryIdSearchCondition, CategoryCodeSearchCondition}
import com.ruimo.scoins.Scoping._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ItemSpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "Item" should {
    "List item when empty." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        inject[ItemRepo].listBySiteId(siteId = 1, locale = localeInfo.Ja, queryString = "foo") === List()
      }
    }

    "Item name." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
        
      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val names = inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))

        names.size === 2
        names(localeInfo.Ja) === ItemName(localeInfo.Ja.id, item1.id.get, "杉")
        names(localeInfo.En) === ItemName(localeInfo.En.id, item1.id.get, "Cedar")

        val map = inject[ItemNameRepo].list(item1)
        map.size === 2
        map(localeInfo.Ja) === ItemName(localeInfo.Ja.id, item1.id.get, "杉")
        map(localeInfo.En) === ItemName(localeInfo.En.id, item1.id.get, "Cedar")
      }
    }

    "item price." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val item1 = inject[ItemRepo].createNew(cat1)
          
        inject[ItemPriceRepo].get(site1, item1) === None

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val saved1 = inject[ItemPriceRepo].get(site1, item1).get
        saved1 === price1
      }
    }

    "Can get item price history." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val item1 = inject[ItemRepo].createNew(cat1)
        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val tax = inject[TaxRepo].createNew
      
        val currencyInfo = inject[CurrencyRegistry]
        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(100), None, BigDecimal(90), date("2013-01-02")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(200), None, BigDecimal(190), date("9999-12-31")
        )

        inject[ItemPriceHistoryRepo].at(price1.id.get, date("2013-01-01")).unitPrice === BigDecimal(100)
        inject[ItemPriceHistoryRepo].at(price1.id.get, date("2013-01-02")).unitPrice === BigDecimal(200)
        inject[ItemPriceHistoryRepo].at(price1.id.get, date("2013-01-03")).unitPrice === BigDecimal(200)
      }
    }

    "Can get metadata" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 100)
        ItemNumericMetadata.createNew(item2, ItemNumericMetadataType.HEIGHT, 1000)
        ItemNumericMetadata(item1, ItemNumericMetadataType.HEIGHT).metadata === 100
        ItemNumericMetadata(item2, ItemNumericMetadataType.HEIGHT).metadata === 1000
      }
    }

    "Can create site item text metadata" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        SiteItemTextMetadata.createNew(site1.id.get, item1.id.get, SiteItemTextMetadataType.PRICE_MEMO, "MEMO01")
        SiteItemTextMetadata.createNew(site2.id.get, item1.id.get, SiteItemTextMetadataType.PRICE_MEMO, "MEMO02")

        SiteItemTextMetadata(site1.id.get, item1.id.get, SiteItemTextMetadataType.PRICE_MEMO).metadata === "MEMO01"
        SiteItemTextMetadata(site2.id.get, item1.id.get, SiteItemTextMetadataType.PRICE_MEMO).metadata === "MEMO02"
      }
    }

    "Can get all metadata at once" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat1)

        ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 100)

        ItemNumericMetadata.createNew(item2, ItemNumericMetadataType.HEIGHT, 1000)

        val map1 = ItemNumericMetadata.all(item1)
        map1.size === 1
        map1(ItemNumericMetadataType.HEIGHT).metadata === 100

        val map2 = ItemNumericMetadata.all(item2)
        map2.size === 1
        map2(ItemNumericMetadataType.HEIGHT).metadata === 1000
      }
    }

    case class CreatedRecords(
      category1: Category, category2: Category
    )

    def storeItems(tax: Tax, site1: Site, site2: Site)(implicit app: Application, conn: Connection): CreatedRecords = {
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "果樹", localeInfo.En -> "Fruit")
        )

        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat2)
        val item3 = inject[ItemRepo].createNew(cat1)
        val item4 = inject[ItemRepo].createNew(cat2)
        val item5 = inject[ItemRepo].createNew(cat1)

        inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))
        inject[ItemNameRepo].createNew(item2, Map(localeInfo.Ja -> "梅", localeInfo.En -> "Ume"))
        inject[ItemNameRepo].createNew(item3, Map(localeInfo.Ja -> "桜", localeInfo.En -> "Cherry"))
        inject[ItemNameRepo].createNew(item4, Map(localeInfo.Ja -> "桃", localeInfo.En -> "Peach"))
        inject[ItemNameRepo].createNew(item5, Map(localeInfo.Ja -> "もみじ", localeInfo.En -> "Maple"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site1, item3)
        inject[SiteItemRepo].createNew(site1, item5)

        inject[SiteItemRepo].createNew(site2, item2)
        inject[SiteItemRepo].createNew(site2, item4)

        inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        inject[ItemDescriptionRepo].createNew(item2, site2, "梅説明")
        inject[ItemDescriptionRepo].createNew(item3, site1, "桜説明")
        inject[ItemDescriptionRepo].createNew(item4, site2, "桃説明")
        inject[ItemDescriptionRepo].createNew(item5, site1, "もみじ説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site2)
        val price3 = inject[ItemPriceRepo].createNew(item3, site1)
        val price4 = inject[ItemPriceRepo].createNew(item4, site2)
        val price5 = inject[ItemPriceRepo].createNew(item5, site1)

        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(100), None, BigDecimal(90), date("2013-01-02")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(101), None, BigDecimal(89), date("9999-12-31")
        )

        inject[ItemPriceHistoryRepo].createNew(
          price2, tax, currencyInfo.Jpy, BigDecimal(300), None, BigDecimal(290), date("2013-01-03")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price2, tax, currencyInfo.Jpy, BigDecimal(301), None, BigDecimal(291), date("9999-12-31")
        )

        inject[ItemPriceHistoryRepo].createNew(
          price3, tax, currencyInfo.Jpy, BigDecimal(500), None, BigDecimal(480), date("2013-01-04")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price3, tax, currencyInfo.Jpy, BigDecimal(501), None, BigDecimal(481), date("9999-12-31")
        )

        inject[ItemPriceHistoryRepo].createNew(
          price4, tax, currencyInfo.Jpy, BigDecimal(1200), None, BigDecimal(1100), date("2013-01-05")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price4, tax, currencyInfo.Jpy, BigDecimal(1201), None, BigDecimal(1101), date("9999-12-31")
        )

        inject[ItemPriceHistoryRepo].createNew(
          price5, tax, currencyInfo.Jpy, BigDecimal(2000), None, BigDecimal(1900), date("2013-01-06")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price5, tax, currencyInfo.Jpy, BigDecimal(2001), None, BigDecimal(1901), date("9999-12-31")
        )

        val height1 = ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 100)
        val height2 = ItemNumericMetadata.createNew(item2, ItemNumericMetadataType.HEIGHT, 200)
        val height3 = ItemNumericMetadata.createNew(item3, ItemNumericMetadataType.HEIGHT, 300)
        val height4 = ItemNumericMetadata.createNew(item4, ItemNumericMetadataType.HEIGHT, 400)
        val height5 = ItemNumericMetadata.createNew(item5, ItemNumericMetadataType.HEIGHT, 500)

        CreatedRecords(cat1, cat2)
      }
    }

    "List item by site." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]
      inject[Database].withConnection { implicit conn =>
        val tax = inject[TaxRepo].createNew

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        
        storeItems(tax, site1, site2)

        val time = date("2013-01-04")
        val list1 = inject[ItemRepo].listBySite(site1, localeInfo.Ja, "", now = time)
        list1.size === 3

        list1(0)._2.name === "もみじ"
        list1(1)._2.name === "杉"
        list1(2)._2.name === "桜"

        list1(0)._3.description === "もみじ説明"
        list1(1)._3.description === "杉説明"
        list1(2)._3.description === "桜説明"

        list1(0)._5.taxId === tax.id.get
        list1(0)._5.currency === currencyInfo.Jpy
        list1(0)._5.unitPrice === BigDecimal(2000)

        list1(1)._5.taxId === tax.id.get
        list1(1)._5.currency === currencyInfo.Jpy
        list1(1)._5.unitPrice === BigDecimal(101)

        list1(2)._5.taxId === tax.id.get
        list1(2)._5.currency === currencyInfo.Jpy
        list1(2)._5.unitPrice === BigDecimal(501)

        list1(0)._6(ItemNumericMetadataType.HEIGHT).metadata === 500
        list1(1)._6(ItemNumericMetadataType.HEIGHT).metadata === 100
        list1(2)._6(ItemNumericMetadataType.HEIGHT).metadata === 300
      }
    }

    "List item by category." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn => {
        val tax = inject[TaxRepo].createNew

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat1: Category = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant"))
        val cat2 = inject[CategoryRepo].createNew(parent = Some(cat1), names = Map(localeInfo.Ja -> "果樹", localeInfo.En -> "Fruit"))
        
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat2)

        inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))
        inject[ItemNameRepo].createNew(item2, Map(localeInfo.Ja -> "梅", localeInfo.En -> "Ume"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site1, item2)

        inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        inject[ItemDescriptionRepo].createNew(item2, site1, "梅説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site1)

        inject[ItemPriceHistoryRepo].createNew(
            price1, tax, currencyInfo.Jpy, BigDecimal(101), None, BigDecimal(89), date("9999-12-31")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price2, tax, currencyInfo.Jpy, BigDecimal(301), None, BigDecimal(291), date("9999-12-31")
        )

        // Since cat2 is a child of cat1, both item1 and item2 will be shown.
        val list1 = inject[ItemRepo].list(
          locale = localeInfo.Ja, queryString = QueryString(), category = CategoryIdSearchCondition(cat1.id.get)
        )
        doWith(list1.records) { recs =>
          recs.size === 2

          recs(0)._2.name === "杉"
          recs(1)._2.name === "梅"
        }
      }
    }

    "List item by category with supplemental category." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax = inject[TaxRepo].createNew

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat0 = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "樹木", localeInfo.En -> "Tree"))
        val cat1 = inject[CategoryRepo].createNew(Some(cat0), Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant"))
        val cat2 = inject[CategoryRepo].createNew(Some(cat0), Map(localeInfo.Ja -> "果樹", localeInfo.En -> "Fruit"))
        val cat3 = inject[CategoryRepo].createNew(Some(cat0), Map(localeInfo.Ja -> "盆栽", localeInfo.En -> "Bonsai"))
        
        // item1: cat1, cat2
        // item2: cat2
        // item3: cat1
        // item4: cat3
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat2)
        val item3 = inject[ItemRepo].createNew(cat1)
        val item4 = inject[ItemRepo].createNew(cat3)
        inject[SupplementalCategoryRepo].createNew(item1.id.get, cat2.id.get)

        inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))
        inject[ItemNameRepo].createNew(item2, Map(localeInfo.Ja -> "梅", localeInfo.En -> "Ume"))
        inject[ItemNameRepo].createNew(item3, Map(localeInfo.Ja -> "松", localeInfo.En -> "Pine"))
        inject[ItemNameRepo].createNew(item4, Map(localeInfo.Ja -> "もみじ", localeInfo.En -> "Maple"))

        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site1, item2)
        inject[SiteItemRepo].createNew(site1, item3)
        inject[SiteItemRepo].createNew(site1, item4)

        inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        inject[ItemDescriptionRepo].createNew(item2, site1, "梅説明")
        inject[ItemDescriptionRepo].createNew(item3, site1, "松説明")
        inject[ItemDescriptionRepo].createNew(item4, site1, "もみじ説明")

        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item2, site1)
        val price3 = inject[ItemPriceRepo].createNew(item3, site1)
        val price4 = inject[ItemPriceRepo].createNew(item4, site1)

        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(101), None, BigDecimal(89), date("9999-12-31")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price2, tax, currencyInfo.Jpy, BigDecimal(301), None, BigDecimal(291), date("9999-12-31")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price3, tax, currencyInfo.Jpy, BigDecimal(401), None, BigDecimal(391), date("9999-12-31")
        )
        inject[ItemPriceHistoryRepo].createNew(
          price4, tax, currencyInfo.Jpy, BigDecimal(501), None, BigDecimal(491), date("9999-12-31")
        )

        doWith(
          inject[ItemRepo].list(
            locale = localeInfo.Ja, queryString = QueryString(), category = CategoryIdSearchCondition(cat2.id.get)
          ).records
        ) { recs =>
          recs.size === 2

          recs(0)._2.name === "杉"
          recs(1)._2.name === "梅"
        }

        doWith(
          inject[ItemRepo].list(
            locale = localeInfo.Ja, queryString = QueryString(), category = CategoryIdSearchCondition(cat1.id.get)
          ).records
        ) { recs =>
          recs.size === 2

          recs(0)._2.name === "杉"
          recs(1)._2.name === "松"
        }

        doWith(
          inject[ItemRepo].list(
            locale = localeInfo.Ja, queryString = QueryString(), category = CategoryIdSearchCondition(cat3.id.get)
          ).records
        ) { recs =>
          recs.size === 1

          recs(0)._2.name === "もみじ"
        }

        // cat1 or cat3
        doWith(
          inject[ItemRepo].list(
            locale = localeInfo.Ja, queryString = QueryString(),
            category = CategoryIdSearchCondition(cat1.id.get + "," + cat3.id.get)
          ).records
        ) { recs =>
          recs.size === 3

          recs(0)._2.name === "もみじ"
          recs(1)._2.name === "杉"
          recs(2)._2.name === "松"
        }

        // cat1 or cat3 & cat2
        doWith(
          inject[ItemRepo].list(
            locale = localeInfo.Ja, queryString = QueryString(),
            category = CategoryIdSearchCondition(cat1.id.get + "," + cat3.id.get + "&" + cat2.id.get)
          ).records
        ) { recs =>
          recs.size === 1

          recs(0)._2.name === "杉"
        }
      }
    }

    "List item." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax = inject[TaxRepo].createNew

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        
        val createdRecords = storeItems(tax, site1, site2)

        val time = date("2013-01-04")

        doWith(inject[ItemRepo].list(None, localeInfo.Ja, QueryString(), now = time)) { pages =>
          pages.pageCount === 1
          pages.currentPage === 0
          pages.pageSize === 10
          val list1 = pages.records
          list1.size === 5

          list1(0)._2.name === "もみじ"
          list1(1)._2.name === "杉"
          list1(2)._2.name === "桃"
          list1(3)._2.name === "桜"
          list1(4)._2.name === "梅"

          list1(0)._3.description === "もみじ説明"
          list1(1)._3.description === "杉説明"
          list1(2)._3.description === "桃説明"
          list1(3)._3.description === "桜説明"
          list1(4)._3.description === "梅説明"

          list1(0)._5.taxId === tax.id.get
          list1(0)._5.currency === currencyInfo.Jpy
          list1(0)._5.unitPrice === BigDecimal(2000)

          list1(1)._5.taxId === tax.id.get
          list1(1)._5.currency === currencyInfo.Jpy
          list1(1)._5.unitPrice === BigDecimal(101)

          list1(2)._5.taxId === tax.id.get
          list1(2)._5.currency === currencyInfo.Jpy
          list1(2)._5.unitPrice === BigDecimal(1200)

          list1(3)._5.taxId === tax.id.get
          list1(3)._5.currency === currencyInfo.Jpy
          list1(3)._5.unitPrice === BigDecimal(501)

          list1(4)._5.taxId === tax.id.get
          list1(4)._5.currency === currencyInfo.Jpy
          list1(4)._5.unitPrice === BigDecimal(301)

          list1(0)._6(ItemNumericMetadataType.HEIGHT).metadata === 500
          list1(1)._6(ItemNumericMetadataType.HEIGHT).metadata === 100
          list1(2)._6(ItemNumericMetadataType.HEIGHT).metadata === 400
          list1(3)._6(ItemNumericMetadataType.HEIGHT).metadata === 300
          list1(4)._6(ItemNumericMetadataType.HEIGHT).metadata === 200
        }

        // Specify category
        doWith(
          inject[ItemRepo].list(None, localeInfo.Ja, QueryString(), CategoryIdSearchCondition(createdRecords.category1.id.get), now = time)
        ) { pages =>
          pages.pageCount === 1
          pages.currentPage === 0
          pages.pageSize === 10
          val list1 = pages.records
          list1.size === 3

          list1(0)._2.name === "もみじ"
          list1(1)._2.name === "杉"
          list1(2)._2.name === "桜"

          list1(0)._3.description === "もみじ説明"
          list1(1)._3.description === "杉説明"
          list1(2)._3.description === "桜説明"

          list1(0)._5.taxId === tax.id.get
          list1(0)._5.currency === currencyInfo.Jpy
          list1(0)._5.unitPrice === BigDecimal(2000)

          list1(1)._5.taxId === tax.id.get
          list1(1)._5.currency === currencyInfo.Jpy
          list1(1)._5.unitPrice === BigDecimal(101)

          list1(2)._5.taxId === tax.id.get
          list1(2)._5.currency === currencyInfo.Jpy
          list1(2)._5.unitPrice === BigDecimal(501)

          list1(0)._6(ItemNumericMetadataType.HEIGHT).metadata === 500
          list1(1)._6(ItemNumericMetadataType.HEIGHT).metadata === 100
          list1(2)._6(ItemNumericMetadataType.HEIGHT).metadata === 300
        }

          // Specify site
        doWith(
          inject[ItemRepo].list(
            None, localeInfo.Ja, QueryString(),
            CategoryIdSearchCondition.Null, CategoryCodeSearchCondition.Null ,Some(site1.id.get), now = time
          )
        ) { pages =>
          pages.pageCount === 1
          pages.currentPage === 0
          pages.pageSize === 10
          val list1 = pages.records
          list1.size === 3

          list1(0)._2.name === "もみじ"
          list1(1)._2.name === "杉"
          list1(2)._2.name === "桜"

          list1(0)._3.description === "もみじ説明"
          list1(1)._3.description === "杉説明"
          list1(2)._3.description === "桜説明"

          list1(0)._5.taxId === tax.id.get
          list1(0)._5.currency === currencyInfo.Jpy
          list1(0)._5.unitPrice === BigDecimal(2000)

          list1(1)._5.taxId === tax.id.get
          list1(1)._5.currency === currencyInfo.Jpy
          list1(1)._5.unitPrice === BigDecimal(101)

          list1(2)._5.taxId === tax.id.get
          list1(2)._5.currency === currencyInfo.Jpy
          list1(2)._5.unitPrice === BigDecimal(501)

          list1(0)._6(ItemNumericMetadataType.HEIGHT).metadata === 500
          list1(1)._6(ItemNumericMetadataType.HEIGHT).metadata === 100
          list1(2)._6(ItemNumericMetadataType.HEIGHT).metadata === 300
        }
      }
    }

    "List item for maintenance." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val tax = inject[TaxRepo].createNew

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        
        storeItems(tax, site1, site2)

        val time = date("2013-01-04")

        val pages = inject[ItemRepo].listForMaintenance(
          siteUser = None,
          locale = localeInfo.Ja,
          queryString = QueryString(),
          now = time
        )
        pages.pageCount === 1
        pages.currentPage === 0
        pages.pageSize === 10
        val list1 = pages.records
        list1.size === 5

        list1(0)._2.get.name === "もみじ"
        list1(1)._2.get.name === "杉"
        list1(2)._2.get.name === "桃"
        list1(3)._2.get.name === "桜"
        list1(4)._2.get.name === "梅"

        list1(0)._3.get.description === "もみじ説明"
        list1(1)._3.get.description === "杉説明"
        list1(2)._3.get.description === "桃説明"
        list1(3)._3.get.description === "桜説明"
        list1(4)._3.get.description === "梅説明"

        doWith(list1(0)._5) { optPriceHistory =>
          optPriceHistory.get.taxId === tax.id.get
          optPriceHistory.get.currency === currencyInfo.Jpy
          optPriceHistory.get.unitPrice === BigDecimal(2000)
          }

        doWith(list1(1)._5) { optPriceHistory =>
          optPriceHistory.get.taxId === tax.id.get
          optPriceHistory.get.currency === currencyInfo.Jpy
          optPriceHistory.get.unitPrice === BigDecimal(101)
        }

        doWith(list1(2)._5) { optPriceHistory =>
          optPriceHistory.get.taxId === tax.id.get
          optPriceHistory.get.currency === currencyInfo.Jpy
          optPriceHistory.get.unitPrice === BigDecimal(1200)
        }

        doWith(list1(3)._5) { optPriceHistory =>
          optPriceHistory.get.taxId === tax.id.get
          optPriceHistory.get.currency === currencyInfo.Jpy
          optPriceHistory.get.unitPrice === BigDecimal(501)
        }

        doWith(list1(4)._5) { optPriceHistory =>
          optPriceHistory.get.taxId === tax.id.get
          optPriceHistory.get.currency === currencyInfo.Jpy
          optPriceHistory.get.unitPrice === BigDecimal(301)
        }

        list1(0)._6(ItemNumericMetadataType.HEIGHT).metadata === 500
        list1(1)._6(ItemNumericMetadataType.HEIGHT).metadata === 100
        list1(2)._6(ItemNumericMetadataType.HEIGHT).metadata === 400
        list1(3)._6(ItemNumericMetadataType.HEIGHT).metadata === 300
        list1(4)._6(ItemNumericMetadataType.HEIGHT).metadata === 200

        doWith(
          inject[ItemRepo].listForMaintenance(
            siteUser = None,
              locale = localeInfo.En,
            queryString = QueryString(),
            now = time
          )
        ) { pages =>
          pages.pageCount === 1
          pages.currentPage === 0
          pages.pageSize === 10
            
          doWith(pages.records) { list =>
            list.size === 5

            list(0)._2.get.name === "Cedar"
            list(1)._2.get.name === "Cherry"
            list(2)._2.get.name === "Maple"
            list(3)._2.get.name === "Peach"
            list(4)._2.get.name === "Ume"

            list(0)._3 === None
            list(1)._3 === None
            list(2)._3 === None
            list(3)._3 === None
            list(4)._3 === None

            doWith(list(0)._5) { optPriceHistory =>
              optPriceHistory.get.taxId === tax.id.get
              optPriceHistory.get.currency === currencyInfo.Jpy
              optPriceHistory.get.unitPrice === BigDecimal(101)
            }

            doWith(list(1)._5) { optPriceHistory =>
              optPriceHistory.get.taxId === tax.id.get
              optPriceHistory.get.currency === currencyInfo.Jpy
              optPriceHistory.get.unitPrice === BigDecimal(501)
            }

            doWith(list(2)._5) { optPriceHistory =>
              optPriceHistory.get.taxId === tax.id.get
              optPriceHistory.get.currency === currencyInfo.Jpy
              optPriceHistory.get.unitPrice === BigDecimal(2000)
            }

            doWith(list(3)._5) { optPriceHistory =>
              optPriceHistory.get.taxId === tax.id.get
              optPriceHistory.get.currency === currencyInfo.Jpy
              optPriceHistory.get.unitPrice === BigDecimal(1200)
            }

            doWith(list(4)._5) { optPriceHistory =>
              optPriceHistory.get.taxId === tax.id.get
              optPriceHistory.get.currency === currencyInfo.Jpy
              optPriceHistory.get.unitPrice === BigDecimal(301)
            }

            list(0)._6(ItemNumericMetadataType.HEIGHT).metadata === 100
            list(1)._6(ItemNumericMetadataType.HEIGHT).metadata === 300
            list(2)._6(ItemNumericMetadataType.HEIGHT).metadata === 500
            list(3)._6(ItemNumericMetadataType.HEIGHT).metadata === 400
            list(4)._6(ItemNumericMetadataType.HEIGHT).metadata === 200
          }
        }
      }
    }

    "Can create sql for item query." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[ItemRepo].createQueryConditionSql(
        QueryString(List("Hello", "World")), CategoryIdSearchCondition.Null, CategoryCodeSearchCondition.Null, None
      ) ===
      "and (item_name.item_name like {query0} or item_description.description like {query0}) " +
      "and (item_name.item_name like {query1} or item_description.description like {query1}) "

      inject[ItemRepo].createQueryConditionSql(
        QueryString(List("Hello", "World")), CategoryIdSearchCondition(123L), CategoryCodeSearchCondition.Null, None
      ) ===
      "and (item_name.item_name like {query0} or item_description.description like {query0}) " +
      "and (item_name.item_name like {query1} or item_description.description like {query1}) " +
      """
          and (
            item.category_id in (
              select descendant from category_path where ancestor in (123)
            )
            or exists (
              select descendant from category_path
              where ancestor in (
                select category_id from supplemental_category where item_id = item.item_id
              )
              and descendant in (123)
            )
          )
        """

      inject[ItemRepo].createQueryConditionSql(
        QueryString(List()), CategoryIdSearchCondition(123L), CategoryCodeSearchCondition.Null, None
      ) ===
      """
          and (
            item.category_id in (
              select descendant from category_path where ancestor in (123)
            )
            or exists (
              select descendant from category_path
              where ancestor in (
                select category_id from supplemental_category where item_id = item.item_id
              )
              and descendant in (123)
            )
          )
        """

      inject[ItemRepo].createQueryConditionSql(
        QueryString(List()), CategoryIdSearchCondition.Null, CategoryCodeSearchCondition.Null, Some(234L)
      ) ===
      "and site.site_id = 234 "
    }

    "Can get ite information from site id and item id." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val startTime = System.currentTimeMillis

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        val cat1 = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant"))
        val cat2 = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "果樹", localeInfo.En -> "Fruit"))
          
        val item1 = inject[ItemRepo].createNew(cat1)
        val item2 = inject[ItemRepo].createNew(cat2)
          
        val name1 = inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))
        val name2 = inject[ItemNameRepo].createNew(item2, Map(localeInfo.Ja -> "桃", localeInfo.En -> "Peach"))

        val siteItem1 = inject[SiteItemRepo].createNew(site1, item1)
        val siteItem2 = inject[SiteItemRepo].createNew(site1, item2)

        inject[SiteItemRepo].createNew(site2, item1)

        doWith(inject[SiteItemRepo].getWithSiteAndItem(site1.id.get, item1.id.get, localeInfo.Ja).get) { rec =>
          rec._1 === site1
          rec._2 === name1(localeInfo.Ja)
        }
        doWith(inject[SiteItemRepo].getWithSiteAndItem(site1.id.get, item1.id.get, localeInfo.En).get) { rec =>
          rec._1 === site1
          rec._2 === name1(localeInfo.En)
        }

        doWith(inject[SiteItemRepo].getWithSiteAndItem(site1.id.get, item2.id.get, localeInfo.Ja).get) { rec =>
          rec._1 === site1
          rec._2 === name2(localeInfo.Ja)
        }

        doWith(inject[SiteItemRepo].getWithSiteAndItem(site2.id.get, item1.id.get, localeInfo.Ja).get) { rec =>
          rec._1 === site2
          rec._2 === name1(localeInfo.Ja)
        }

        inject[SiteItemRepo].getWithSiteAndItem(site2.id.get, item2.id.get, localeInfo.Ja) === None

        val currentTime = System.currentTimeMillis
        doWith(inject[SiteItemRepo].list(item1.id.get)) { tbl =>
          tbl.size === 2
          tbl(0)._2.itemId.id === item1.id.get.id
          tbl(0)._2.created.toEpochMilli must be_>=(startTime)
          tbl(0)._2.created.toEpochMilli must be_<=(currentTime)

          tbl(1)._2.itemId.id === item1.id.get.id
          tbl(1)._2.created.toEpochMilli must be_>=(startTime)
          tbl(1)._2.created.toEpochMilli must be_<=(currentTime)
          }
        }
      }
    }
  }
}
