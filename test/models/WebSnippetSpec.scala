package models

import org.specs2.mutable._

import com.ruimo.scoins.Scoping._
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.Database
import java.time.Instant

class WebSnippetSpec extends Specification with InjectorSupport {
  "WebSnippet" should {
    "Can create new record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")

        inject[WebSnippetRepo].createNew(site1.id.get, "code01", "site11", 111L, 3)
        inject[WebSnippetRepo].createNew(site1.id.get, "code01", "site12", 111L, 3)
        inject[WebSnippetRepo].createNew(site1.id.get, "code01", "site13", 111L, 3)

        inject[WebSnippetRepo].createNew(site1.id.get, "code01", "site14", 111L, 3) must throwA(new MaxWebSnippetCountException(site1.id.get))

        inject[WebSnippetRepo].createNew(site2.id.get, "code01", "site21", 111L, 2)
        inject[WebSnippetRepo].createNew(site2.id.get, "code01", "site22", 111L, 2)

        inject[WebSnippetRepo].createNew(site2.id.get, "code01", "site23", 111L, 2) must throwA(new MaxWebSnippetCountException(site2.id.get))
      }
    }

    "Can list newest of every site." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.En, "Shop2")

        inject[WebSnippetRepo].createNew(site1.id.get, "code01", "site11", 111L)
        inject[WebSnippetRepo].createNew(site1.id.get, "code01", "site12", 122L)
        inject[WebSnippetRepo].createNew(site1.id.get, "code02", "site13", 133L)

        inject[WebSnippetRepo].createNew(site2.id.get, "code01", "site21", 120L)
        inject[WebSnippetRepo].createNew(site2.id.get, "code02", "site22", 130L)
        inject[WebSnippetRepo].createNew(site2.id.get, "code02", "site23", 140L)

        val list: Seq[WebSnippet] = inject[WebSnippetRepo].listNewerBySite(Some("code01"), 1)
        list.size === 2
        doWith(list(0)) { w =>
          w.siteId === site1.id.get
          w.contentCode === "code01"
          w.content === "site12"
          w.updatedTime === Instant.ofEpochMilli(122L)
        }
        doWith(list(1)) { w =>
          w.siteId === site2.id.get
          w.contentCode === "code01"
          w.content === "site21"
          w.updatedTime === Instant.ofEpochMilli(120L)
        }
      }
    }
  }
}
