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

class FavoSpec extends Specification with InjectorSupport {
  "Favo" should {
    "Can create favo" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val repo = inject[FavoRepo]

        val user1 = inject[StoreUserRepo].create(
          "userName1", "firstName1", Some("middleName1"), "lastName1", "email1",
          1L, 2L, UserRole.NORMAL, Some("companyName1")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          1L, 2L, UserRole.NORMAL, Some("companyName2")
        )
        val user3 = inject[StoreUserRepo].create(
          "userName3", "firstName3", Some("middleName3"), "lastName3", "email3",
          1L, 2L, UserRole.NORMAL, Some("companyName3")
        )
        val user4 = inject[StoreUserRepo].create(
          "userName4", "firstName4", Some("middleName4"), "lastName4", "email4",
          1L, 2L, UserRole.NORMAL, Some("companyName4")
        )

        repo.count(FavoKind.NEWS, ContentId(0)) === 0
        repo.list(0, 10, OrderBy("store_user.user_name"), FavoKind.NEWS, ContentId(0)).isEmpty === true

        repo.create(FavoKind.NEWS, ContentId(0), user1.id.get)

        repo.create(FavoKind.NEWS, ContentId(1), user1.id.get)
        repo.create(FavoKind.NEWS, ContentId(1), user3.id.get)
        repo.create(FavoKind.NEWS, ContentId(1), user4.id.get)

        repo.create(FavoKind.NEWS, ContentId(2), user2.id.get)
        repo.create(FavoKind.NEWS, ContentId(2), user4.id.get)

        repo.count(FavoKind.NEWS, ContentId(0)) === 1
        doWith(repo.list(0, 10, OrderBy("store_user.user_name"), FavoKind.NEWS, ContentId(0)).records) { recs =>
          recs.size === 1
          recs(0) === user1
        }

        repo.count(FavoKind.NEWS, ContentId(1)) === 3
        doWith(repo.list(0, 10, OrderBy("store_user.user_name"), FavoKind.NEWS, ContentId(1)).records) { recs =>
          recs.size === 3
          recs(0) === user1
          recs(1) === user3
          recs(2) === user4
        }
        repo.isFav(FavoKind.NEWS, ContentId(1), user1.id.get) === true
        repo.isFav(FavoKind.NEWS, ContentId(1), user2.id.get) === false
        repo.isFav(FavoKind.NEWS, ContentId(1), user3.id.get) === true
        repo.isFav(FavoKind.NEWS, ContentId(1), user4.id.get) === true

        // paging
        doWith(repo.list(0, 2, OrderBy("store_user.user_name"), FavoKind.NEWS, ContentId(1)).records) { recs =>
          recs.size === 2
          recs(0) === user1
          recs(1) === user3
        }
        doWith(repo.list(1, 2, OrderBy("store_user.user_name"), FavoKind.NEWS, ContentId(1)).records) { recs =>
          recs.size === 1
          recs(0) === user4
        }

        repo.count(FavoKind.NEWS, ContentId(2)) === 2
        doWith(repo.list(0, 10, OrderBy("store_user.user_name"), FavoKind.NEWS, ContentId(2)).records) { recs =>
          recs.size === 2
          recs(0) === user2
          recs(1) === user4
        }
      }
    }
  }
}
