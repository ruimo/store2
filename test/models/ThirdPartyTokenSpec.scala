package models

import org.specs2.mutable._

import com.ruimo.scoins.Scoping._
import java.time.Instant
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class ThirdPartyTokenSpec extends Specification with InjectorSupport {
  "Third party token" should {
    "Can create token." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.NORMAL, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          1L, 2L, UserRole.NORMAL, Some("companyName2")
        )

        val repo = inject[ThirdPartyTokenRepo]
        repo.get(ThirdPartyTokenKind(0), user1.id.get) === None

        repo.create(ThirdPartyTokenKind(0), user1.id.get, "TOKEN0", None)
        repo.create(ThirdPartyTokenKind(1), user1.id.get, "TOKEN1", Some(Instant.ofEpochMilli(123L)))

        doWith(repo.get(ThirdPartyTokenKind(0), user1.id.get).get) { tk =>
          tk.kind === ThirdPartyTokenKind(0)
          tk.storeUserId === user1.id.get
          tk.token === "TOKEN0"
          tk.expires === None
        }
        doWith(repo.get(ThirdPartyTokenKind(1), user1.id.get).get) { tk =>
          tk.kind === ThirdPartyTokenKind(1)
          tk.storeUserId === user1.id.get
          tk.token === "TOKEN1"
          tk.expires === Some(Instant.ofEpochMilli(123L))
        }
        repo.get(ThirdPartyTokenKind(2), user1.id.get) === None
        repo.get(ThirdPartyTokenKind(0), user2.id.get) === None
      }
    }
  }
}

