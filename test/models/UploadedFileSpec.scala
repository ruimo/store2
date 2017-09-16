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

class UploadedFileSpec extends Specification with InjectorSupport {
  "Uploaded file" should {
    "Can create category" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
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

        val id1 = UploadedFile.create(
          user2.id.get, "fileName999", Some("cont1"), Instant.ofEpochMilli(5), "category01"
        )
        val id2 = UploadedFile.create(
          user4.id.get, "fileName888", None, Instant.ofEpochMilli(6), "category01"
        )
        val id3 = UploadedFile.create(
          user3.id.get, "fileName777", Some("cont2"), Instant.ofEpochMilli(7), "category01"
        )
        val id4 = UploadedFile.create(
          user1.id.get, "fileName666", Some("cont3"), Instant.ofEpochMilli(4), "category01"
        )

        doWith(
          UploadedFile.list(
            categoryName = "category01", orderBy = OrderBy("file_name asc")
          )
        ) { list =>
          list.records === Seq(
            UploadedFile.get(id4).get,
            UploadedFile.get(id3).get,
            UploadedFile.get(id2).get,
            UploadedFile.get(id1).get
          )
        }

        doWith(
          UploadedFile.list(
            categoryName = "category01", orderBy = OrderBy("file_name desc")
          )
        ) { list =>
          list.records === Seq(
            UploadedFile.get(id1).get,
            UploadedFile.get(id2).get,
            UploadedFile.get(id3).get,
            UploadedFile.get(id4).get
          )
        }

        doWith(
          UploadedFile.list(
            categoryName = "category01", orderBy = OrderBy("created_time asc")
          )
        ) { list =>
          list.records === Seq(
            UploadedFile.get(id4).get,
            UploadedFile.get(id1).get,
            UploadedFile.get(id2).get,
            UploadedFile.get(id3).get
          )
        }

        doWith(
          UploadedFile.list(
            categoryName = "category01", orderBy = OrderBy("store_user_id asc")
          )
        ) { list =>
          list.records === Seq(
            UploadedFile.get(id4).get,
            UploadedFile.get(id1).get,
            UploadedFile.get(id3).get,
            UploadedFile.get(id2).get
          )
        }

        doWith(
          UploadedFile.list(
            categoryName = "category01", page = 0, pageSize = 3, orderBy = OrderBy("file_name desc")
          )
        ) { list =>
          list.records === Seq(
            UploadedFile.get(id1).get,
            UploadedFile.get(id2).get,
            UploadedFile.get(id3).get
          )
        }

        doWith(
          UploadedFile.list(
            categoryName = "category01", page = 1, pageSize = 3, orderBy = OrderBy("file_name desc")
          )
        ) { list =>
          list.records === Seq(
            UploadedFile.get(id4).get
          )
        }
      }
    }

    "Specify category name" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName1", "firstName1", Some("middleName1"), "lastName1", "email1",
          1L, 2L, UserRole.NORMAL, Some("companyName1")
        )
        val id1 = UploadedFile.create(
          user1.id.get, "fileName999", Some("cont1"), Instant.ofEpochMilli(5), "category01"
        )
        val id2 = UploadedFile.create(
          user1.id.get, "fileName888", None, Instant.ofEpochMilli(6), "category01"
        )
        val id3 = UploadedFile.create(
          user1.id.get, "fileName777", Some("cont2"), Instant.ofEpochMilli(7), "category01"
        )
        val id4 = UploadedFile.create(
          user1.id.get, "fileName666", Some("cont3"), Instant.ofEpochMilli(4), "category02"
        )
        val id5 = UploadedFile.create(
          user1.id.get, "fileName555", Some("cont4"), Instant.ofEpochMilli(4), "category02"
        )

        doWith(
          UploadedFile.list(
            page = 0, pageSize = 2, categoryName = "category01", orderBy = OrderBy("file_name asc")
          )
        ) { list =>
          list.currentPage === 0
          list.pageSize === 2
          list.pageCount === 2
        }

        doWith(
          UploadedFile.list(
            page = 0, pageSize = 2, categoryName = "category02", orderBy = OrderBy("file_name asc")
          )
        ) { list =>
          list.currentPage === 0
          list.pageSize === 2
          list.pageCount === 1
        }
      }
    }
  }
}
