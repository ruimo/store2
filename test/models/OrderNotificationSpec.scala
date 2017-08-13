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

class OrderNotificationSpec extends Specification with InjectorSupport {
  "OrderNotification" should {
    "Can create record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )

        val on1 = inject[OrderNotificationRepo].createNew(user1.id.get)
        on1.storeUserId === user1.id.get

        val list = inject[OrderNotificationRepo].list()
        list.size === 1
        list.head === on1
      }
    }

    "List site owner notification record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

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
        val user4 = inject[StoreUserRepo].create(
          "userName4", "firstName4", Some("middleName4"), "lastName4", "email4",
          1L, 2L, UserRole.ADMIN, Some("companyName4")
        )

        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)
        val siteUser2 = inject[SiteUserRepo].createNew(user2.id.get, site1.id.get)
        val siteUser3 = inject[SiteUserRepo].createNew(user3.id.get, site2.id.get)

        val on1 = inject[OrderNotificationRepo].createNew(user1.id.get)
        val on2 = inject[OrderNotificationRepo].createNew(user3.id.get)

        val list1 = inject[OrderNotificationRepo].listBySite(site1.id.get)
        val list2 = inject[OrderNotificationRepo].listBySite(site2.id.get)

        list1.size === 1
        list2.size === 1

        list1.head === user1
        list2.head === user3
      }
    }

    "List admin notification record" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")

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
        val user4 = inject[StoreUserRepo].create(
          "userName4", "firstName4", Some("middleName4"), "lastName4", "email4",
          1L, 2L, UserRole.NORMAL, Some("companyName4")
        )

        val siteUser1 = inject[SiteUserRepo].createNew(user1.id.get, site1.id.get)

        val on1 = inject[OrderNotificationRepo].createNew(user1.id.get)
        val on2 = inject[OrderNotificationRepo].createNew(user2.id.get)

        val list1 = inject[OrderNotificationRepo].listAdmin

        list1.size === 1

        list1.head === user2
      }
    }
  }
}
