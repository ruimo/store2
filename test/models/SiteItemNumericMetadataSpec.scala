package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import com.ruimo.scoins.Scoping._

import java.sql.Date.{valueOf => date}
import helpers.QueryString
import helpers.{CategoryIdSearchCondition, CategoryCodeSearchCondition}
import com.ruimo.scoins.Scoping._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import java.time.Instant

class SiteItemNumericMetadataSpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "SiteItemNumericMetadata" should {
    "Can create new record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val item1 = inject[ItemRepo].createNew(cat1)

        val rec1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.HIDE, 1, Instant.ofEpochMilli(2L)
        )

        inject[SiteItemNumericMetadataRepo].all(
          site1.id.get, item1.id.get
        ).size === 0

        doWith(
          inject[SiteItemNumericMetadataRepo].all(
            site1.id.get, item1.id.get, 1L
          )
        ) { map =>
          map.size === 1
          map(SiteItemNumericMetadataType.HIDE) === rec1
        }

        inject[SiteItemNumericMetadataRepo].all(
          site1.id.get, item1.id.get, 2L
        ).size === 0
      }
    }

    "Can pick valid record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val item1 = inject[ItemRepo].createNew(cat1)

        val rec1 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.HIDE, 1, Instant.ofEpochMilli(2L)
        )
        val rec2 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.HIDE, 2, Instant.ofEpochMilli(4L)
        )
        val rec3 = inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get, SiteItemNumericMetadataType.STOCK, 0, Instant.ofEpochMilli(3L)
        )

        inject[SiteItemNumericMetadataRepo].all(
          site1.id.get, item1.id.get
        ).size === 0

        doWith(
          inject[SiteItemNumericMetadataRepo].all(
            site1.id.get, item1.id.get, 1L
          )
        ) { map =>
          map.size === 2
          map(SiteItemNumericMetadataType.HIDE) === rec1
          map(SiteItemNumericMetadataType.STOCK) === rec3
        }

        doWith(
          inject[SiteItemNumericMetadataRepo].all(
            site1.id.get, item1.id.get, 2L
          )
        ) { map =>
          map.size === 2
          map(SiteItemNumericMetadataType.HIDE) === rec2
          map(SiteItemNumericMetadataType.STOCK) === rec3
        }

        doWith(
          inject[SiteItemNumericMetadataRepo].all(
            site1.id.get, item1.id.get, 3L
          )
        ) { map =>
          map.size === 1
          map(SiteItemNumericMetadataType.HIDE) === rec2
        }

        inject[SiteItemNumericMetadataRepo].all(
          site1.id.get, item1.id.get, 4L
        ).size === 0
      }
    }
  }
}

