package models

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import helpers.Helper._
import com.ruimo.scoins.Scoping._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class RecommendByAdminSpec extends Specification with InjectorSupport {
  "RecommendByAdmin" should {
    "Can create record and query single record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        inject[RecommendByAdminRepo].count === 0

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val rec1 = inject[RecommendByAdminRepo].createNew(
          site1.id.get, item1.id.get.id, 123, true
        )
        val read1 = inject[RecommendByAdminRepo].apply(rec1.id.get)
        read1 === rec1

        inject[RecommendByAdminRepo].count === 1

        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木2", localeInfo.En -> "Plant2")
        )
        val item2 = inject[ItemRepo].createNew(cat2)
        val rec2 = RecommendByAdmin(
          rec1.id,
          site2.id.get,
          item2.id.get.id,
          234,
          false
        )
        inject[RecommendByAdminRepo].update(rec2)
        val read2 = inject[RecommendByAdminRepo].apply(rec1.id.get)
        read2 === rec2

        inject[RecommendByAdminRepo].remove(rec2.id.get)
        inject[RecommendByAdminRepo].count === 0
      }
    }

    "Can list records" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val itemName1 = inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "Item1"))
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木2", localeInfo.En -> "Plant2")
        )
        val item2 = inject[ItemRepo].createNew(cat2)
        val item3 = inject[ItemRepo].createNew(cat1)

        val rec1 = inject[RecommendByAdminRepo].createNew(site1.id.get, item1.id.get.id, 123, true)
        val rec2 = inject[RecommendByAdminRepo].createNew(site2.id.get, item2.id.get.id, 100, true)
        val rec3 = inject[RecommendByAdminRepo].createNew(site2.id.get, item3.id.get.id, 105, false)
        doWith(inject[RecommendByAdminRepo].listByScore(showDisabled = false, locale = localeInfo.Ja)) { list =>
          list.records.size === 2
          list.records(0)._1 === rec1
          list.records(0)._2 === Some(itemName1(localeInfo.Ja))
          list.records(0)._3 === Some(site1)
          list.records(1)._1 === rec2
          list.records(1)._2 === None
          list.records(1)._3 === Some(site2)
        }

        doWith(inject[RecommendByAdminRepo].listByScore(showDisabled = true, locale = localeInfo.Ja)) { list =>
          list.records.size === 3
          list.records(0)._1 === rec1
          list.records(0)._2 === Some(itemName1(localeInfo.Ja))
          list.records(0)._3 === Some(site1)
          list.records(1)._1 === rec3
          list.records(1)._2 === None
          list.records(1)._3 === Some(site2)
          list.records(2)._1 === rec2
          list.records(2)._2 === None
          list.records(2)._3 === Some(site2)
        }
      }
    }

    "Hidden items should not be shown" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]
      
      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        val itemName1 = inject[ItemNameRepo].createNew(item1, Map(localeInfo.Ja -> "Item1"))
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木2", localeInfo.En -> "Plant2")
        )
        val item2 = inject[ItemRepo].createNew(cat2)
        inject[SiteItemNumericMetadataRepo].createNew(
          site1.id.get, item1.id.get,
          SiteItemNumericMetadataType.HIDE,
          0
        )
        inject[SiteItemNumericMetadataRepo].createNew(
          site2.id.get, item2.id.get,
          SiteItemNumericMetadataType.HIDE,
          1
        )

        val rec1 = inject[RecommendByAdminRepo].createNew(site1.id.get, item1.id.get.id, 123, true)
        val rec2 = inject[RecommendByAdminRepo].createNew(site2.id.get, item2.id.get.id, 100, true)
        doWith(inject[RecommendByAdminRepo].listByScore(showDisabled = false, locale = localeInfo.Ja)) { list =>
          list.records.size === 1
          list.records(0)._1 === rec1
          list.records(0)._2 === Some(itemName1(localeInfo.Ja))
          list.records(0)._3 === Some(site1)
        }
      }
    }
  }
}
