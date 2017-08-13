package models

import org.specs2.mutable._

import java.time.Instant
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import play.api.i18n.Lang
import helpers.InjectorSupport
import play.api.Application
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder

class TaxSpec extends Specification with InjectorSupport {
  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)

  "Tax" should {
    "Create new tax." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew

        val list = inject[TaxRepo].list
        list.size === 2
        list(0) === tax1
        list(1) === tax2
      }
    }

    "Create name." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew

        val taxName1 = inject[TaxNameRepo].createNew(tax1, localeInfo.Ja, "外税")
        val taxName2 = inject[TaxNameRepo].createNew(tax2, localeInfo.Ja, "内税")

        val list = inject[TaxRepo].tableForDropDown(lang = Lang("ja"), conn = implicitly)
        list.size === 2
        list(0)._1 === tax2.id.get.toString
        list(0)._2 === taxName2.taxName
        list(1)._1 === tax1.id.get.toString
        list(1)._2 === taxName1.taxName
      }
    }

    "apply." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val tax2 = inject[TaxRepo].createNew

        tax1 === inject[TaxRepo].apply(tax1.id.get)
        tax2 === inject[TaxRepo].apply(tax2.id.get)
      }
    }

    "at." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val tax1 = inject[TaxRepo].createNew
        val his1 = inject[TaxHistoryRepo].createNew(tax1, TaxType.INNER_TAX, BigDecimal("5"), date("2013-01-02"))
        val his2 = inject[TaxHistoryRepo].createNew(tax1, TaxType.INNER_TAX, BigDecimal("10"), date("9999-12-31"))

        inject[TaxHistoryRepo].at(tax1.id.get, date("2013-01-01")) === his1
        inject[TaxHistoryRepo].at(tax1.id.get, date("2013-01-02")) === his2
      }
    }

    "Outer tax amount is calculated." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      val his = TaxHistory(None, 0, TaxType.OUTER_TAX, BigDecimal(5), Instant.ofEpochMilli(0))
      implicit val taxRepo = inject[TaxRepo]
      his.taxAmount(BigDecimal(100)) === BigDecimal(5)
      his.taxAmount(BigDecimal(99)) === BigDecimal(4)
      his.taxAmount(BigDecimal(80)) === BigDecimal(4)
      his.taxAmount(BigDecimal(79)) === BigDecimal(3)
    }

    "Innter tax amount is calculated." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      val his = TaxHistory(None, 0, TaxType.INNER_TAX, BigDecimal(5), Instant.ofEpochMilli(0))
      implicit val taxRepo = inject[TaxRepo]
      his.taxAmount(BigDecimal(100)) === BigDecimal(4)
      his.taxAmount(BigDecimal(84)) === BigDecimal(4)
      his.taxAmount(BigDecimal(83)) === BigDecimal(3)
    }

    "Non tax amount is calculated." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      val his = TaxHistory(None, 0, TaxType.NON_TAX, BigDecimal(5), Instant.ofEpochMilli(0))
      implicit val taxRepo = inject[TaxRepo]
      his.taxAmount(BigDecimal(100)) === BigDecimal(0)
      his.taxAmount(BigDecimal(84)) === BigDecimal(0)
      his.taxAmount(BigDecimal(83)) === BigDecimal(0)
    }
  }
}

