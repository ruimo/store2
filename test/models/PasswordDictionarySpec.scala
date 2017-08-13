package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class PasswordDictionarySpec extends Specification with InjectorSupport {
  "Password dictionary spec" should {
    "Invalid password is detected" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        PasswordDictionary.isNaivePassword("password") === false

        SQL(
          " insert into password_dict (password) values ('password')"
        ).executeUpdate()
        PasswordDictionary.isNaivePassword("password") === true
      }
    }
  }
}

