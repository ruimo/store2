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

class NewsSpec extends Specification with InjectorSupport {
  "News" should {
    // "Can create site's record." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val news = inject[NewsRepo].createNew(
    //       user1.id.get, Some(site.id.get), None,
    //       "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     news === inject[NewsRepo].apply(news.id.get)._1
    //     site === inject[NewsRepo].apply(news.id.get)._2.get
    //     val list = inject[NewsRepo].list()
    //     list.records.size === 1
    //     list.records(0)._1 === news
    //     list.records(0)._2 === Some(site)
    //     list.records(0)._3 === Some(user1)
    //   }
    // }

    // "Can create admin's record." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val news = inject[NewsRepo].createNew(
    //       user1.id.get, None, None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     news === inject[NewsRepo].apply(news.id.get)._1
    //     val list = inject[NewsRepo].list()
    //     list.records.size === 1
    //     list.records(0)._1 === news
    //     list.records(0)._2 === None
    //   }
    // }

    // "Can order records." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val user2 = inject[StoreUserRepo].create(
    //       "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
    //       1L, 2L, UserRole.NORMAL, Some("companyName2")
    //     )
    //     val news = Vector(
    //       inject[NewsRepo].createNew(
    //         user1.id.get, None, None, "title01", "contents01", releaseTime = Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //       ),
    //       inject[NewsRepo].createNew(
    //         user2.id.get, site.id, None, "title02", "contents02", releaseTime = Instant.ofEpochMilli(234L), Instant.ofEpochMilli(222L)
    //       ),
    //       inject[NewsRepo].createNew(
    //         user1.id.get, None, None, "title03", "contents03", releaseTime = Instant.ofEpochMilli(345L), Instant.ofEpochMilli(111L)
    //       )
    //     )
    //     val list = inject[NewsRepo].list()
    //     list.records.size === 3
    //     list.records(0)._1 === news(2)
    //     list.records(0)._2 === None
    //     list.records(0)._3 === Some(user1)
    //     list.records(1)._1 === news(1)
    //     list.records(1)._2 === Some(site)
    //     list.records(1)._3 === Some(user2)
    //     list.records(2)._1 === news(0)
    //     list.records(2)._2 === None
    //     list.records(2)._3 === Some(user1)
    //   }
    // }

    // "Can update record." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val news = inject[NewsRepo].createNew(
    //       user1.id.get, None, None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     news === inject[NewsRepo].apply(news.id.get)._1
    //     val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
    //     inject[NewsRepo].update(
    //       news.id.get, Some(user1.id.get), Some(site.id.get), None, "title02", "contents02", Instant.ofEpochMilli(111L),
    //       Instant.ofEpochMilli(222L)
    //     ) === 1
    //     val list = inject[NewsRepo].list()
    //     list.records.size === 1
    //     list.records(0)._1 === news.copy(
    //       siteId = Some(site.id.get),
    //       title = "title02",
    //       contents = "contents02",
    //       releaseTime = Instant.ofEpochMilli(111L),
    //       updatedTime = Instant.ofEpochMilli(222L)
    //     )
    //     list.records(0)._2 === Some(site)
    //   }
    // }

    // "Can delete record." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val news = inject[NewsRepo].createNew(
    //       user1.id.get, None, None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     news === inject[NewsRepo].apply(news.id.get)._1
    //     inject[NewsRepo].delete(news.id.get, user1.id)

    //     val list = inject[NewsRepo].list()
    //     list.records.size === 0
    //   }
    // }

    // "Can set category." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val cat = inject[NewsCategoryRepo].createNew(
    //       "categoryName", "iconUrl"
    //     )
    //     val news = inject[NewsRepo].createNew(
    //       user1.id.get, None, cat.id, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     news === inject[NewsRepo].apply(news.id.get)._1
    //   }
    // }

    // "Can specify user group." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val user2 = inject[StoreUserRepo].create(
    //       "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
    //       1L, 2L, UserRole.NORMAL, Some("companyName2")
    //     )
    //     val user3 = inject[StoreUserRepo].create(
    //       "userName3", "firstName3", Some("middleName3"), "lastName3", "email3",
    //       1L, 2L, UserRole.NORMAL, Some("companyName3")
    //     )
    //     val news1 = inject[NewsRepo].createNew(
    //       user1.id.get, None, None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     val news2 = inject[NewsRepo].createNew(
    //       user2.id.get, None, None, "title02", "contents02", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(235L)
    //     )
    //     val news3 = inject[NewsRepo].createNew(
    //       user3.id.get, None, None, "title03", "contents03", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(236L)
    //     )

    //     val ug1 = inject[UserGroupRepo].create("group1")
    //     val ug2 = inject[UserGroupRepo].create("group2")

    //     inject[UserGroupMemberRepo].create(ug1.id.get, user1.id.get)
    //     inject[UserGroupMemberRepo].create(ug1.id.get, user2.id.get)

    //     inject[UserGroupMemberRepo].create(ug2.id.get, user2.id.get)
    //     inject[UserGroupMemberRepo].create(ug2.id.get, user3.id.get)

    //     doWith(inject[NewsRepo].list(specificUserGroupId = Some(ug1.id.get))) { list =>
    //       list.records.size === 2
    //       list.records(0)._1 === news2
    //       list.records(1)._1 === news1
    //     }

    //     doWith(inject[NewsRepo].list(specificUserGroupId = Some(ug2.id.get))) { list =>
    //       list.records.size === 2
    //       list.records(0)._1 === news3
    //       list.records(1)._1 === news2
    //     }
    //   }
    // }

    // "Can exclude specify user group." in {
    //   implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
    //   val localeInfo = inject[LocaleInfoRepo]
    //   val currencyInfo = inject[CurrencyRegistry]

    //   inject[Database].withConnection { implicit conn =>
    //     val user1 = inject[StoreUserRepo].create(
    //       "userName", "firstName", Some("middleName"), "lastName", "email",
    //       1L, 2L, UserRole.NORMAL, Some("companyName")
    //     )
    //     val user2 = inject[StoreUserRepo].create(
    //       "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
    //       1L, 2L, UserRole.NORMAL, Some("companyName2")
    //     )
    //     val user3 = inject[StoreUserRepo].create(
    //       "userName3", "firstName3", Some("middleName3"), "lastName3", "email3",
    //       1L, 2L, UserRole.NORMAL, Some("companyName3")
    //     )
    //     val news1 = inject[NewsRepo].createNew(
    //       user1.id.get, None, None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
    //     )
    //     val news2 = inject[NewsRepo].createNew(
    //       user2.id.get, None, None, "title02", "contents02", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(235L)
    //     )
    //     val news3 = inject[NewsRepo].createNew(
    //       user3.id.get, None, None, "title03", "contents03", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(236L)
    //     )

    //     val ug1 = inject[UserGroupRepo].create("group1")
    //     val ug2 = inject[UserGroupRepo].create("group2")

    //     // Group1: user1, user2
    //     inject[UserGroupMemberRepo].create(ug1.id.get, user1.id.get)
    //     inject[UserGroupMemberRepo].create(ug1.id.get, user2.id.get)

    //     // Group2: user2, user3
    //     inject[UserGroupMemberRepo].create(ug2.id.get, user2.id.get)
    //     inject[UserGroupMemberRepo].create(ug2.id.get, user3.id.get)

    //     doWith(inject[NewsRepo].list(specificUserGroupId = Some(ug1.id.get), excludeUserGroupId = Some(ug2.id.get))) { list =>
    //       list.records.size === 1
    //       list.records(0)._1 === news1
    //     }
    //   }
    // }

    "Can check if recently updated news exists." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
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
        val news1 = inject[NewsRepo].createNew(
          user1.id.get, None, None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)
        )
        val news2 = inject[NewsRepo].createNew(
          user2.id.get, None, None, "title02", "contents02", Instant.ofEpochMilli(234L), Instant.ofEpochMilli(235L)
        )
        val news3 = inject[NewsRepo].createNew(
          user3.id.get, None, None, "title03", "contents03", Instant.ofEpochMilli(345L), Instant.ofEpochMilli(236L)
        )

        val ug1 = inject[UserGroupRepo].create("group1")
        val ug2 = inject[UserGroupRepo].create("group2")

        // Group1: user1, user2
        inject[UserGroupMemberRepo].create(ug1.id.get, user1.id.get)
        inject[UserGroupMemberRepo].create(ug1.id.get, user2.id.get)

        // Group2: user2, user3
        inject[UserGroupMemberRepo].create(ug2.id.get, user2.id.get)
        inject[UserGroupMemberRepo].create(ug2.id.get, user3.id.get)

        val repo = inject[NewsRepo]

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(122L),
          from = Instant.ofEpochMilli(121L),
          specificUserGroupId = Some(ug1.id.get)
        ) === false

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(122L),
          from = Instant.ofEpochMilli(122L),
          specificUserGroupId = Some(ug1.id.get)
        ) === false

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(123L),
          from = Instant.ofEpochMilli(122L),
          specificUserGroupId = Some(ug1.id.get)
        ) === true

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(124L),
          from = Instant.ofEpochMilli(123L),
          specificUserGroupId = Some(ug1.id.get)
        ) === true
        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(124L),
          from = Instant.ofEpochMilli(123L),
          specificUserGroupId = Some(ug2.id.get)
        ) === false

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(124L),
          from = Instant.ofEpochMilli(124L),
          specificUserGroupId = Some(ug1.id.get)
        ) === false

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(233L),
          from = Instant.ofEpochMilli(124L),
          specificUserGroupId = Some(ug1.id.get)
        ) === false

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(234L),
          from = Instant.ofEpochMilli(124L),
          specificUserGroupId = Some(ug1.id.get)
        ) === true

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(235L),
          from = Instant.ofEpochMilli(234L),
          specificUserGroupId = Some(ug1.id.get)
        ) === true
        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(235L),
          from = Instant.ofEpochMilli(234L),
          specificUserGroupId = Some(ug2.id.get)
        ) === true

        repo.isRecentlyUpdated(
          now = Instant.ofEpochMilli(235L),
          from = Instant.ofEpochMilli(235L),
          specificUserGroupId = Some(ug1.id.get)
        ) === false
      }
    }
  }
}
