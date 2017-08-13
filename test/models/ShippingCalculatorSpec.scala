package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.sql.Date.{valueOf => date}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ShippingCalculatorSpec extends Specification with InjectorSupport {
  "ShippingCalculator" should {
    "Earn empty map when no items." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      new ShippingFeeEntries().bySiteAndItemClass.size === 0
    }

    "On item earns single entry." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val e = new ShippingFeeEntries().add(site, 2, 5)

        e.bySiteAndItemClass.size === 1
        val byItemClass = e.bySiteAndItemClass(site)
        byItemClass.size === 1
        byItemClass(2) === 5
      }
    }

    "Quantity should added." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")

        val e = new ShippingFeeEntries()
          .add(site, 2, 5)
          .add(site, 2, 10)

        e.bySiteAndItemClass.size === 1
        val byItemClass = e.bySiteAndItemClass(site)
        byItemClass.size === 1
        byItemClass(2) === 15
      }
    }

    "Two item classes earn two entries." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")

        val e = new ShippingFeeEntries()
          .add(site, 2, 5)
          .add(site, 2, 10)
          .add(site, 3, 10)

        e.bySiteAndItemClass.size === 1
        val byItemClass = e.bySiteAndItemClass(site)
        byItemClass.size === 2
        byItemClass(2) === 15
        byItemClass(3) === 10
      }
    }

    "Two sites and two items classes earn four entries." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        val e = ShippingFeeEntries()
          .add(site1, 2, 5)
          .add(site1, 2, 10)
          .add(site1, 3, 10)
          .add(site2, 2, 3)
          .add(site2, 3, 2)

        e.bySiteAndItemClass.size === 2
        val byItemClass1 = e.bySiteAndItemClass(site1)
        byItemClass1.size === 2
        byItemClass1(2) === 15
        byItemClass1(3) === 10

        val byItemClass2 = e.bySiteAndItemClass(site2)
        byItemClass2.size === 2
        byItemClass2(2) === 3
        byItemClass2(3) === 2
      }
    }
  }
}
