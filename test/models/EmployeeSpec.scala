package models

import org.specs2.mutable._
import com.ruimo.scoins.Scoping._
import anorm._
import anorm.SqlParser
import helpers.InjectorSupport
import play.api.Application
import play.api.test._
import play.api.test.Helpers._
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder

class EmployeeSpec extends Specification with InjectorSupport {
  "Employee" should {
    "Can create employee." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(inject[LocaleInfoRepo].Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(inject[LocaleInfoRepo].En, "Shop2")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.NORMAL, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          1L, 2L, UserRole.NORMAL, Some("companyName2")
        )
        val user3 = inject[StoreUserRepo].create(
          "userName3", "firstName3", Some("middleName3"), "lastName3", "email3",
          1L, 2L, UserRole.NORMAL, Some("companyName3")
        )

        val e1 = inject[EmployeeRepo].createNew(site1.id.get, user1.id.get)
        val e2 = inject[EmployeeRepo].createNew(site1.id.get, user2.id.get)
        val e3 = inject[EmployeeRepo].createNew(site2.id.get, user3.id.get)

        doWith(inject[EmployeeRepo].apply(e1.id.get)) { rec =>
          rec.siteId === site1.id.get
          rec.userId === user1.id.get
          rec.index === 1
        }

        doWith(inject[EmployeeRepo].apply(e2.id.get)) { rec =>
          rec.siteId === site1.id.get
          rec.userId === user2.id.get
          rec.index === 1
        }

        doWith(inject[EmployeeRepo].apply(e3.id.get)) { rec =>
          rec.siteId === site2.id.get
          rec.userId === user3.id.get
          rec.index === 1
        }
      }
    }

    "Can check if employee." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.NORMAL, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          1L, 2L, UserRole.NORMAL, Some("companyName2")
        )
        val user3 = inject[StoreUserRepo].create(
          "userName3", "firstName3", Some("middleName3"), "lastName3", "email3",
          1L, 2L, UserRole.NORMAL, Some("companyName3")
        )

        val e1 = inject[EmployeeRepo].createNew(site1.id.get, user1.id.get)
        val e2 = inject[EmployeeRepo].createNew(site1.id.get, user2.id.get)

        doWith(inject[EmployeeRepo].getBelonging(user1.id.get).get) { rec =>
          rec.siteId === site1.id.get
          rec.userId === user1.id.get
        }

        doWith(inject[EmployeeRepo].getBelonging(user2.id.get).get) { rec =>
          rec.siteId === site1.id.get
          rec.userId === user2.id.get
        }

        inject[EmployeeRepo].getBelonging(user3.id.get) === None
      }
    }

    "Can create employee belonging two sites." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(inject[LocaleInfoRepo].Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(inject[LocaleInfoRepo].En, "Shop2")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.NORMAL, Some("companyName")
        )

        val e1 = inject[EmployeeRepo].createNew(site1.id.get, user1.id.get)
        val e2 = inject[EmployeeRepo].createNew(site2.id.get, user1.id.get)

        doWith(inject[EmployeeRepo].apply(e1.id.get)) { rec =>
          rec.siteId === site1.id.get
          rec.userId === user1.id.get
          rec.index === 1
        }

        doWith(inject[EmployeeRepo].apply(e2.id.get)) { rec =>
          rec.siteId === site2.id.get
          rec.userId === user1.id.get
          rec.index === 2
        }

        // Can swap indicies
        inject[EmployeeRepo].swapIndicies(user1.id.get, 1, 2) === 1

        doWith(inject[EmployeeRepo].apply(e1.id.get)) { rec =>
          rec.siteId === site1.id.get
          rec.userId === user1.id.get
          rec.index === 2
        }

        doWith(inject[EmployeeRepo].apply(e2.id.get)) { rec =>
          rec.siteId === site2.id.get
          rec.userId === user1.id.get
          rec.index === 1
        }
      }
    }
  }
}

