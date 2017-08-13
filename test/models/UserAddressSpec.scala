package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.Database
import java.time.Instant

class UserAddressSpec extends Specification with InjectorSupport {
  "User address" should {
    "Can create new record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user = inject[StoreUserRepo].create(
          userName = "uno",
          firstName = "",
          middleName = None,
          lastName = "",
          email = "",
          passwordHash = 0L,
          salt = 0L,
          userRole = UserRole.NORMAL,
          companyName = None
        )
        val address01 = Address.createNew(
          countryCode = CountryCode.JPN
        )
        val address02 = Address.createNew(
          countryCode = CountryCode.JPN
        )

        val ua1 = UserAddress.createNew(user.id.get, address01.id.get)
        val ua2 = UserAddress.createNew(user.id.get, address02.id.get)

        ua1.seq === 1
        ua2.seq === 2
      }
    }

    "Can get by userid" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user = inject[StoreUserRepo].create(
          userName = "uno",
          firstName = "",
          middleName = None,
          lastName = "",
          email = "",
          passwordHash = 0L,
          salt = 0L,
          userRole = UserRole.NORMAL,
          companyName = None
        )
        val address01 = Address.createNew(
          countryCode = CountryCode.JPN
        )
        val address02 = Address.createNew(
          countryCode = CountryCode.JPN
        )

        val ua1 = UserAddress.createNew(user.id.get, address01.id.get)
        val ua2 = UserAddress.createNew(user.id.get, address02.id.get)
        val rec = UserAddress.getByUserId(user.id.get)

        rec.isDefined === true
        rec.get.storeUserId === user.id.get
        rec.get.addressId === address01.id.get
        rec.get.seq === 1
      }
    }
  }
}

