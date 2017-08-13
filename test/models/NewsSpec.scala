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

class NewsSpec extends Specification with InjectorSupport {
  "News" should {
    "Can create site's record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val news = inject[NewsRepo].createNew(Some(site.id.get), "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L))
        news === inject[NewsRepo].apply(news.id.get)._1
        site === inject[NewsRepo].apply(news.id.get)._2.get
        val list = inject[NewsRepo].list()
        list.records.size === 1
        list.records(0)._1 === news
        list.records(0)._2 === Some(site)
      }
    }

    "Can create admin's record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val news = inject[NewsRepo].createNew(None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L))
        news === inject[NewsRepo].apply(news.id.get)._1
        val list = inject[NewsRepo].list()
        list.records.size === 1
        list.records(0)._1 === news
        list.records(0)._2 === None
      }
    }

    "Can order records." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val news = Vector(
          inject[NewsRepo].createNew(None, "title01", "contents01", releaseTime = Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L)),
          inject[NewsRepo].createNew(site.id, "title02", "contents02", releaseTime = Instant.ofEpochMilli(234L), Instant.ofEpochMilli(222L)),
          inject[NewsRepo].createNew(None, "title03", "contents03", releaseTime = Instant.ofEpochMilli(345L), Instant.ofEpochMilli(111L))
        )
        val list = inject[NewsRepo].list()
        list.records.size === 3
        list.records(0)._1 === news(2)
        list.records(0)._2 === None
        list.records(1)._1 === news(1)
        list.records(1)._2 === Some(site)
        list.records(2)._1 === news(0)
        list.records(2)._2 === None
      }
    }

    "Can update record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val news = inject[NewsRepo].createNew(None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L))
        news === inject[NewsRepo].apply(news.id.get)._1
        val site = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        inject[NewsRepo].update(news.id.get, Some(site.id.get), "title02", "contents02", Instant.ofEpochMilli(111L), Instant.ofEpochMilli(222L)) === 1
        val list = inject[NewsRepo].list()
        list.records.size === 1
        list.records(0)._1 === news.copy(
          siteId = Some(site.id.get),
          title = "title02",
          contents = "contents02",
          releaseTime = Instant.ofEpochMilli(111L),
          updatedTime = Instant.ofEpochMilli(222L)
        )
        list.records(0)._2 === Some(site)
      }
    }

    "Can delete record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val news = inject[NewsRepo].createNew(None, "title01", "contents01", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(234L))
        news === inject[NewsRepo].apply(news.id.get)._1
        inject[NewsRepo].delete(news.id.get)

        val list = inject[NewsRepo].list()
        list.records.size === 0
      }
    }
  }
}
