package models

import com.ruimo.scoins.Scoping._
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

class UserGroupSpec extends Specification with InjectorSupport {
  "User group" should {
    "Can create user group." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val repo = inject[UserGroupRepo]
        val g1 = repo.create("group1")
        val g2 = repo.create("group2")
        val g3 = repo.create("group3")

        doWith(repo.list().records) { records =>
          records.size === 3
          records(0) === g1
          records(1) === g2
          records(2) === g3
        }

        doWith(repo.list(orderBy = OrderBy("user_group.name desc")).records) { records =>
          records.size === 3
          records(0) === g3
          records(1) === g2
          records(2) === g1
        }

        doWith(repo.list(pageSize = 2).records) { records =>
          records.size === 2
          records(0) === g1
          records(1) === g2
        }

        doWith(repo.list(page = 1, pageSize = 2).records) { records =>
          records.size === 1
          records(0) === g3
        }
      }
    }

    "Can create user group membership." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val grepo = inject[UserGroupRepo]
        val mrepo = inject[UserGroupMemberRepo]

        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          1L, 2L, UserRole.ADMIN, Some("companyName2")
        )
        val user3 = inject[StoreUserRepo].create(
          "userName3", "firstName3", Some("middleName3"), "lastName3", "email3",
          1L, 2L, UserRole.ADMIN, Some("companyName3")
        )

        val g1 = grepo.create("group1")
        val g2 = grepo.create("group2")

        mrepo.create(g1.id.get, user3.id.get)
        mrepo.create(g1.id.get, user1.id.get)
        mrepo.create(g1.id.get, user2.id.get)

        mrepo.create(g2.id.get, user2.id.get)
        mrepo.create(g2.id.get, user3.id.get)

        doWith(mrepo.listByUserGroupId(userGroupId = g1.id.get).records) { records =>
          records.size === 3
          records(0)._1 === g1
          records(0)._2 === user1
          records(1)._1 === g1
          records(1)._2 === user2
          records(2)._1 === g1
          records(2)._2 === user3
        }
      }
    }
  }
}
