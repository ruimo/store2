package models

import org.specs2.mutable._
import play.api.Application
import play.api.test._
import play.api.test.Helpers._
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder

class CurrencyInfoSpec extends Specification {
  "CurrencyInfo" should {
    "Japan and English locale" in {
      val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyRegistry = app.injector.instanceOf[CurrencyRegistry]
      currencyRegistry.Jpy === CurrencyInfo(1L, "JPY")
      currencyRegistry.Usd === CurrencyInfo(2L, "USD")
    }

    "Dropdown items" in {
      val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyRegistry = app.injector.instanceOf[CurrencyRegistry]
      app.injector.instanceOf[Database].withConnection { implicit conn =>
        val list = currencyRegistry.tableForDropDown
        list(0)._1 == currencyRegistry.Jpy.id
        list(0)._2 == currencyRegistry.Jpy.currencyCode
        list(1)._1 == currencyRegistry.Usd.id
        list(1)._2 == currencyRegistry.Usd.currencyCode
      }
    }
  }
}
