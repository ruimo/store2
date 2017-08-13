package models

import java.time.Instant
import org.specs2.mutable._
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.db.Database
import java.util.Locale
import java.sql.Date.{valueOf => date}
import java.time.Instant

import com.ruimo.scoins.Scoping._
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

class ItemDetailSpec extends Specification with InjectorSupport {
  implicit def date2milli(d: java.sql.Date) = d.getTime

  "ItemDetail" should {
    "Get simple case." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val names = inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))
        val desc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明")
        val price1 = inject[ItemPriceRepo].createNew(item1, site1)

        val tax = inject[TaxRepo].createNew
        val currencyInfo = inject[CurrencyRegistry]
        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(100), None, BigDecimal(90), Instant.ofEpochMilli(date("2013-01-02"))
        )
        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(200), None, BigDecimal(190), Instant.ofEpochMilli(date("9999-12-31"))
        )

        val detail = inject[ItemDetailRepo].show(
          site1.id.get, item1.id.get.id, localeInfo.Ja, Instant.ofEpochMilli(date("2013-01-01")),
          UnitPriceStrategy
        ).get
        detail.name === "杉"
        detail.description === "杉説明"
        detail.itemNumericMetadata.isEmpty === true
        detail.siteItemNumericMetadata.isEmpty == true
        detail.price === BigDecimal(100)
        detail.siteName === "商店1"

        val detail2 = inject[ItemDetailRepo].show(
          site1.id.get, item1.id.get.id, localeInfo.Ja, Instant.ofEpochMilli(date("2013-01-02")),
          UnitPriceStrategy
        ).get
        detail2.name === "杉"
        detail2.description === "杉説明"
        detail2.itemNumericMetadata.isEmpty === true
        detail2.siteItemNumericMetadata.isEmpty == true
        detail2.price === BigDecimal(200)
        detail2.siteName === "商店1"

        ItemNumericMetadata.createNew(item1, ItemNumericMetadataType.HEIGHT, 100)
        inject[SiteItemNumericMetadataRepo].createNew(site1.id.get, item1.id.get, SiteItemNumericMetadataType.STOCK, 123L)
        val detail3 = inject[ItemDetailRepo].show(
          site1.id.get, item1.id.get.id, localeInfo.Ja, Instant.ofEpochMilli(date("2013-01-02")),
          UnitPriceStrategy
        ).get
        detail3.name === "杉"
        detail3.description === "杉説明"
        detail3.itemNumericMetadata.size === 1
        detail3.itemNumericMetadata(ItemNumericMetadataType.HEIGHT).metadata === 100L
        detail3.siteItemNumericMetadata.size == 1
        detail3.siteItemNumericMetadata(SiteItemNumericMetadataType.STOCK).metadata === 123L
        detail3.price === BigDecimal(200)
        detail3.siteName === "商店1"
      }
    }

    "More than one store can sell the same item." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val names = inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "杉", localeInfo.En -> "Cedar"))
        val desc1 = inject[ItemDescriptionRepo].createNew(item1, site1, "杉説明1")
        val desc2 = inject[ItemDescriptionRepo].createNew(item1, site2, "杉説明2")
        val price1 = inject[ItemPriceRepo].createNew(item1, site1)
        val price2 = inject[ItemPriceRepo].createNew(item1, site2)

        val currencyInfo = inject[CurrencyRegistry]
        val tax = inject[TaxRepo].createNew
        inject[ItemPriceHistoryRepo].createNew(
          price1, tax, currencyInfo.Jpy, BigDecimal(200), None, BigDecimal(190), Instant.ofEpochMilli(date("9999-12-31"))
        )
        inject[ItemPriceHistoryRepo]createNew(
          price2, tax, currencyInfo.Jpy, BigDecimal(300), None, BigDecimal(290), Instant.ofEpochMilli(date("9999-12-31"))
        )

        doWith(
          inject[ItemDetailRepo].show(
            site1.id.get, item1.id.get.id, localeInfo.Ja, Instant.ofEpochMilli(date("2013-01-01")),
            UnitPriceStrategy
          ).get
        ) { detail =>
          detail.name === "杉"
          detail.description === "杉説明1"
          detail.itemNumericMetadata.isEmpty === true
          detail.siteItemNumericMetadata.isEmpty == true
          detail.price === BigDecimal(200)
          detail.siteName === "商店1"
        }

        doWith(
          inject[ItemDetailRepo].show(
            site2.id.get, item1.id.get.id, localeInfo.Ja, Instant.ofEpochMilli(date("2013-01-01")),
            UnitPriceStrategy
          ).get
        ) { detail =>
          detail.name === "杉"
          detail.description === "杉説明2"
          detail.itemNumericMetadata.isEmpty === true
          detail.siteItemNumericMetadata.isEmpty == true
          detail.price === BigDecimal(300)
          detail.siteName === "商店2"
        }
      }
    }
  }
}
