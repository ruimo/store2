package models

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import play.api.i18n.Lang
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport

class LocaleInfoSpec extends Specification with InjectorSupport {
  "LocaleInfo" should {
    "Japaness locale should exists." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      localeInfo(1L).toLocale === new Locale("ja")
      localeInfo(2L).toLocale === new Locale("en")
    }

    "registry has all locale." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      val r = localeInfo.registry
      r(1L) === localeInfo.Ja
      r(2L) === localeInfo.En
    }

    "byLang has all locale." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      val byLang = localeInfo.byLangTable
      byLang.size === 2
      byLang(Lang("ja")) === localeInfo.Ja
      byLang(Lang("en")) === localeInfo.En
    }

    "Lang is correctly selected." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      localeInfo.getDefault(List(Lang("ja"))) === localeInfo.Ja
      localeInfo.getDefault(List(Lang("ja", "JP"))) === localeInfo.Ja
      localeInfo.getDefault(List(Lang("fr"))) === localeInfo.En
    }
  }
}
