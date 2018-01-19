package models

import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import helpers.{InjectorSupport, TokenGenerator}
import play.api.Application
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder

class FirstSetupSpec extends Specification with InjectorSupport {
  "FirstSetup" should {
    "Salt and hash is created by create() method." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        implicit val storeUserRepo = inject[StoreUserRepo]
        val user = FirstSetup(
          "userName", "firstName", Some("middleName"), "lastName", "email", Seq[String](), "password", "companyName",
          Some("kanaFirstName"), None, Some("kanaLastName")
        ).save(
          conn
        )
        storeUserRepo(user.id.get) === user
      }
    }
  }
}
