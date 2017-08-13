package models

import org.specs2.mutable._
import anorm._
import anorm.SqlParser
import play.api.Application
import play.api.test._
import play.api.test.Helpers._
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder

class CouponSpec extends Specification {
  "Coupon" should {
    "Can create new coupon." in {
      val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      app.injector.instanceOf[Database].withConnection { implicit conn =>
        val coupon1 = Coupon.createNew()
        val coupon2 = Coupon.createNew()

        Coupon(coupon1.id.get) === coupon1
        Coupon(coupon2.id.get) === coupon2
      }
    }

    "Can let item a coupon." in {
      val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      app.injector.instanceOf[Database].withConnection { implicit conn =>
        val localeInfoRepo = app.injector.instanceOf[LocaleInfoRepo]
        val cat1 = app.injector.instanceOf[CategoryRepo].createNew(
          Map(localeInfoRepo.Ja -> "植木", localeInfoRepo.En -> "Plant")
        )
        val item = app.injector.instanceOf[ItemRepo].createNew(cat1)
        Coupon.isCoupon(item.id.get) === false

        Coupon.update(item.id.get, false)
        Coupon.isCoupon(item.id.get) === false

        Coupon.update(item.id.get, true)
        Coupon.isCoupon(item.id.get) === true

        Coupon.update(item.id.get, false)
        Coupon.isCoupon(item.id.get) === false

        Coupon.update(item.id.get, true)
        Coupon.isCoupon(item.id.get) === true
      }
    }
  }
}
