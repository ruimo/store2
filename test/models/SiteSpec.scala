package models

import org.specs2.mutable._

import com.ruimo.scoins.Scoping._
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import helpers.InjectorSupport
import play.api.Application
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder

class SiteSpec extends Specification with InjectorSupport {
  "Site" should {
    "Can create new site." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")

        val list = inject[SiteRepo].listByName()
        list.size === 2
        list(0).name === "Shop2"
        list(1).name === "商店1"
      }      
    }

    "Can create dropdown items." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
          
        implicit val storeUserRepo = inject[StoreUserRepo]
        implicit val login = LoginSession(user1, None, 0L)
        val list = inject[SiteRepo].tableForDropDown
        list.size === 2
        list(0)._1 === site2.id.get.toString
        list(0)._2 === site2.name

        list(1)._1 === site1.id.get.toString
        list(1)._2 === site1.name
      }      
    }

    "Can create dropdown items for site owner." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)
          
        implicit val storeUserRepo = inject[StoreUserRepo]
        implicit val login = LoginSession(user1, Some(siteUser), 0L)
        val list = inject[SiteRepo].tableForDropDown
        list.size === 1
        list(0)._1 === site1.id.get.toString
        list(0)._2 === site1.name
      }      
    }

    "Can create dropdown by item." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")
        val site3 = inject[SiteRepo].createNew(localeInfo.Ja, "商店3")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item1)
          
        implicit val storeUserRepo = inject[StoreUserRepo]
        implicit val login = LoginSession(user1, None, 0L)
        val list = inject[SiteRepo].tableForDropDown(item1.id.get.id)
        list.size === 2
        list(0)._1 === site2.id.get.toString
        list(0)._2 === site2.name

        list(1)._1 === site1.id.get.toString
        list(1)._2 === site1.name
      }      
    }

    "Can create dropdown by item for site owner." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")
        val site3 = inject[SiteRepo].createNew(localeInfo.Ja, "商店3")
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val siteUser = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        inject[SiteItemRepo].createNew(site1, item1)
        inject[SiteItemRepo].createNew(site2, item1)
          
        implicit val storeUserRepo = inject[StoreUserRepo]
        implicit val login = LoginSession(user1, Some(siteUser), 0L)
        val list = inject[SiteRepo].tableForDropDown(item1.id.get.id)
        list.size === 1
        list(0)._1 === site1.id.get.toString
        list(0)._2 === site1.name
      }      
    }

    "Can retrieve record by id." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")

        site1 === inject[SiteRepo].apply(site1.id.get)
      }
    }

    "Deleted record should be omitted from results." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        implicit val storeUserRepo = inject[StoreUserRepo]
        implicit val login = LoginSession(user1, None, 0L)
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val item1 = inject[ItemRepo].createNew(cat1)
        inject[SiteItemRepo].createNew(site1, item1)

        inject[SiteRepo].listByName().size === 1
        inject[SiteRepo].tableForDropDown.size === 1
        inject[SiteRepo].tableForDropDown(item1.id.get.id).size === 1
        inject[SiteRepo].listAsMap.size === 1
        inject[SiteRepo].get(site1.id.get) === Some(site1)
        inject[SiteRepo].apply(site1.id.get) === site1

        inject[SiteRepo].delete(site1.id.get)

        inject[SiteRepo].listByName().size === 0
        inject[SiteRepo].tableForDropDown.size === 0
        inject[SiteRepo].tableForDropDown(item1.id.get.id).size === 0
        inject[SiteRepo].listAsMap.size === 0
        inject[SiteRepo].get(site1.id.get) === None
        inject[SiteRepo].apply(site1.id.get) must throwA[anorm.AnormException]
      }
    }

    "Can update record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val currencyInfo = inject[CurrencyRegistry]
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        doWith(inject[SiteRepo].listByName()) { list =>
          list.size === 1
          list(0).localeId === localeInfo.Ja.id
          list(0).name === "商店1"
        }

        inject[SiteRepo].update(site.id.get, localeInfo.En, "Shop1")
        doWith(inject[SiteRepo].listByName()) { list =>
          list.size === 1
          list(0).localeId === localeInfo.En.id
          list(0).name === "Shop1"
        }
      }      
    }
  }
}
