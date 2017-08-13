package models

import org.specs2.mutable._
import java.time.Instant
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.db.Database
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport

class ItemInquirySpec extends Specification with InjectorSupport {
  "ItemInquiry" should {
    "Can create new record." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val site0 = inject[SiteRepo].createNew(localeInfo.Ja, "Site00")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "Site01")
        val cat0 = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant"))
        val cat1 = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "植木2", localeInfo.En -> "Plant2"))
        val item0 = inject[ItemRepo].createNew(cat0)
        val item1 = inject[ItemRepo].createNew(cat1)
        val user0 = inject[StoreUserRepo].create(
          "userName0", "firstName0", Some("middleName0"), "lastName0", "email0",
          1L, 2L, UserRole.ADMIN, Some("companyName0")
        )
        val user1 = inject[StoreUserRepo].create(
          "userName1", "firstName1", Some("middleName1"), "lastName1", "email1",
          2L, 3L, UserRole.NORMAL, Some("companyName1")
        )

        val rec0 = ItemInquiry.createNew(
          site0.id.get, item0.id.get, user0.id.get, ItemInquiryType.QUERY, "user0", "email0", Instant.ofEpochMilli(123L)
        )
        val rec1 = ItemInquiry.createNew(
          site1.id.get, item1.id.get, user1.id.get, ItemInquiryType.QUERY, "user1", "email1", Instant.ofEpochMilli(234L)
        )
          
        ItemInquiry.apply(rec0.id.get) === rec0
        ItemInquiry.apply(rec1.id.get) === rec1

        val fields = Map(
          'foo -> "Hello", 'bar -> "World"
        )
        ItemInquiryField.createNew(rec0.id.get, fields)
        ItemInquiryField.createNew(rec1.id.get, Map())

        ItemInquiryField(rec0.id.get) === fields
        ItemInquiryField(rec1.id.get).isEmpty === true
      }
    }
  }
}
