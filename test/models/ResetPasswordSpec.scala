package models

import org.specs2.mutable._

import java.time.Instant
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ResetPasswordSpec extends Specification with InjectorSupport {
  "ResetPassword" should {
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

        val rec = inject[ResetPasswordRepo].createNew(
          storeUserId = user.id.get,
          now = Instant.ofEpochMilli(12345L),
          token = 45678L
        )
        val readRec = inject[ResetPasswordRepo].apply(rec.id.get)

        readRec.storeUserId === user.id.get
        readRec.resetTime === Instant.ofEpochMilli(12345L)
        readRec.token === 45678L
      }      
    }

    "Can remove records" in {
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

        val rec = inject[ResetPasswordRepo].createNew(
          storeUserId = user.id.get,
          now = Instant.ofEpochMilli(12345L),
          token = 45678L
        )

        rec === inject[ResetPasswordRepo].get(rec.id.get).get

        inject[ResetPasswordRepo].removeByStoreUserId(user.id.get) === 1L
        inject[ResetPasswordRepo].get(rec.id.get) === None
      }
    }

    "Can determine valid record" in {
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

        val rec = inject[ResetPasswordRepo].createNew(
          storeUserId = user.id.get,
          now = Instant.ofEpochMilli(12345L),
          token = 45678L
        )

        // token does not match
        inject[ResetPasswordRepo].isValid(user.id.get, 45679L, 12344L) === false

        // userid does not match
        inject[ResetPasswordRepo].isValid(user.id.get + 1, 45678L, 12344L) === false

        // record is too old
        inject[ResetPasswordRepo].isValid(user.id.get, 45678L, 12345L) === false

        // valid record
        inject[ResetPasswordRepo].isValid(user.id.get, 45678L, 12344L) === true
      }
    }
  }
}
