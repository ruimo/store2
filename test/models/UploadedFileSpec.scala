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

        val id1 = inject[UploadedFileRepo].create(
          user2.id.get, "fileName999", Some("cont1"), Instant.ofEpochMilli(5), "category01", None
        )
        val id2 = inject[UploadedFileRepo].create(
          user4.id.get, "fileName888", None, Instant.ofEpochMilli(6), "category01", None
        )
        val id3 = inject[UploadedFileRepo].create(
          user3.id.get, "fileName777", Some("cont2"), Instant.ofEpochMilli(7), "category01", None
        )
        val id4 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName666", Some("cont3"), Instant.ofEpochMilli(4), "category01", None
        )

        doWith(
          inject[UploadedFileRepo].list(
            categoryName = "category01", orderBy = OrderBy("file_name asc")
          )
        ) { list =>
          list.records === Seq(
            (inject[UploadedFileRepo].get(id4).get, user1),
            (inject[UploadedFileRepo].get(id3).get, user3),
            (inject[UploadedFileRepo].get(id2).get, user4),
            (inject[UploadedFileRepo].get(id1).get, user2)
          )
        }

        doWith(
          inject[UploadedFileRepo].list(
            categoryName = "category01", orderBy = OrderBy("file_name desc")
          )
        ) { list =>
          list.records === Seq(
            (inject[UploadedFileRepo].get(id1).get, user2),
            (inject[UploadedFileRepo].get(id2).get, user4),
            (inject[UploadedFileRepo].get(id3).get, user3),
            (inject[UploadedFileRepo].get(id4).get, user1)
          )
        }

        doWith(
          inject[UploadedFileRepo].list(
            categoryName = "category01", orderBy = OrderBy("created_time asc")
          )
        ) { list =>
          list.records === Seq(
            (inject[UploadedFileRepo].get(id4).get, user1),
            (inject[UploadedFileRepo].get(id1).get, user2),
            (inject[UploadedFileRepo].get(id2).get, user4),
            (inject[UploadedFileRepo].get(id3).get, user3)
          )
        }

        doWith(
          inject[UploadedFileRepo].list(
            categoryName = "category01", orderBy = OrderBy("store_user_id asc")
          )
        ) { list =>
          list.records === Seq(
            (inject[UploadedFileRepo].get(id4).get, user1),
            (inject[UploadedFileRepo].get(id1).get, user2),
            (inject[UploadedFileRepo].get(id3).get, user3),
            (inject[UploadedFileRepo].get(id2).get, user4)
          )
        }

        doWith(
          inject[UploadedFileRepo].list(
            categoryName = "category01", page = 0, pageSize = 3, orderBy = OrderBy("file_name desc")
          )
        ) { list =>
          list.records === Seq(
            (inject[UploadedFileRepo].get(id1).get, user2),
            (inject[UploadedFileRepo].get(id2).get, user4),
            (inject[UploadedFileRepo].get(id3).get, user3)
          )
        }

        doWith(
          inject[UploadedFileRepo].list(
            categoryName = "category01", page = 1, pageSize = 3, orderBy = OrderBy("file_name desc")
          )
        ) { list =>
          list.records === Seq(
            (inject[UploadedFileRepo].get(id4).get, user1)
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
        val id1 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName999", Some("cont1"), Instant.ofEpochMilli(5), "category01", None
        )
        val id2 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName888", None, Instant.ofEpochMilli(6), "category01", None
        )
        val id3 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName777", Some("cont2"), Instant.ofEpochMilli(7), "category01", None
        )
        val id4 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName666", Some("cont3"), Instant.ofEpochMilli(4), "category02", None
        )
        val id5 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName555", Some("cont4"), Instant.ofEpochMilli(4), "category02", None
        )

        doWith(
          inject[UploadedFileRepo].list(
            page = 0, pageSize = 2, categoryName = "category01", orderBy = OrderBy("file_name asc")
          )
        ) { list =>
          list.currentPage === 0
          list.pageSize === 2
          list.pageCount === 2
        }

        doWith(
          inject[UploadedFileRepo].list(
            page = 0, pageSize = 2, categoryName = "category02", orderBy = OrderBy("file_name asc")
          )
        ) { list =>
          list.currentPage === 0
          list.pageSize === 2
          list.pageCount === 1
        }
      }
    }

    "ls" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName1", "firstName1", Some("middleName1"), "lastName1", "email1",
          1L, 2L, UserRole.NORMAL, Some("companyName1")
        )

        // Empty
        doWith(inject[UploadedFileRepo].ls(directory = Directory("/")).get) { recs =>
          recs.records.size === 0
        }

        val file999 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName999", Some("cont1"), Instant.ofEpochMilli(5), "category01", None
        )
        val file888 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName888", Some("cont2"), Instant.ofEpochMilli(5), "category01", None
        )
        val file777 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName777", Some("cont3"), Instant.ofEpochMilli(5), "category01", None
        )

        // 3 Files.
        doWith(inject[UploadedFileRepo].ls(directory = Directory("/")).get) { recs =>
          recs.records.size === 0
        }
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/")).get.records
        ) { recs =>
          recs.size === 3
          recs(0)._1.fileName === "fileName777"
          recs(1)._1.fileName === "fileName888"
          recs(2)._1.fileName === "fileName999"
        }

        // Non existing directory
        inject[UploadedFileRepo].ls(directory = Directory("/foo")) === None

        // Create dir /foo
        // /foo <dir>
        // fileName777
        // fileName888
        // fileName999
        val fooDir = inject[UploadedDirectoryRepo].create(
          user1.id.get, Directory("/foo"), Instant.ofEpochMilli(10), "category01"
        )

        // 1 Dir, 3 Files.
        doWith(inject[UploadedFileRepo].ls(directory = Directory("/")).get) { recs =>
          recs.records.size === 0
        }
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/")).get.records
        ) { recs =>
          recs.size === 4
          recs(0)._1.fileName === "/foo"
          recs(1)._1.fileName === "fileName777"
          recs(2)._1.fileName === "fileName888"
          recs(3)._1.fileName === "fileName999"
        }

        // Create file under /foo
        // /foo <dir>
        //   fileName666
        //   fileName555
        //   fileName444
        // fileName777
        // fileName888
        // fileName999
        val file666 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName666", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(fooDir)
        )
        val file555 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName555", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(fooDir)
        )
        val file444 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName444", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(fooDir)
        )

        // 1 Dir, 3 Files in /
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/")).get.records
        ) { recs =>
          recs.size === 4
          recs(0)._1.fileName === "/foo"
          recs(1)._1.fileName === "fileName777"
          recs(2)._1.fileName === "fileName888"
          recs(3)._1.fileName === "fileName999"
        }

        // 3 Files in /foo
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "", directory = Directory("/foo")).get.records
        ) { recs =>
          recs.size === 0
        }
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/foo")).get.records
        ) { recs =>
          recs.size === 3
          recs(0)._1.fileName === "fileName444"
          recs(1)._1.fileName === "fileName555"
          recs(2)._1.fileName === "fileName666"
        }

        // Create file in root
        val root = inject[UploadedDirectoryRepo].getByDirectory(Directory("/")).get
        val file333 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName333", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(root.id.get)
        )

        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/")).get.records
        ) { recs =>
          recs.size === 5
          recs(0)._1.fileName === "/foo"
          recs(1)._1.fileName === "fileName333"
          recs(2)._1.fileName === "fileName777"
          recs(3)._1.fileName === "fileName888"
          recs(4)._1.fileName === "fileName999"
        }

        val barDir = inject[UploadedDirectoryRepo].create(
          user1.id.get, Directory("/foo/bar"), Instant.ofEpochMilli(10), "category01"
        )

        val file222 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName222", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(barDir)
        )
        val file111 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName111", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(barDir)
        )
        val file000 = inject[UploadedFileRepo].create(
          user1.id.get, "fileName000", Some("cont1"), Instant.ofEpochMilli(5), "category01", Some(barDir)
        )

        // 1 Dir, 3 Files in /
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/")).get.records
        ) { recs =>
          recs.size === 5
          recs(0)._1.fileName === "/foo"
          recs(1)._1.fileName === "fileName333"
          recs(2)._1.fileName === "fileName777"
          recs(3)._1.fileName === "fileName888"
          recs(4)._1.fileName === "fileName999"
        }

        // 1 Dir, 3 Files in /foo
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/foo")).get.records
        ) { recs =>
          recs.size === 4
          recs(0)._1.fileName === "/foo/bar"
          recs(1)._1.fileName === "fileName444"
          recs(2)._1.fileName === "fileName555"
          recs(3)._1.fileName === "fileName666"
        }

        // 1 Dir, 3 Files in /foo
        doWith(inject[UploadedFileRepo].ls(
          categoryName = "category01", directory = Directory("/foo/bar")).get.records
        ) { recs =>
          recs.size === 3
          recs(0)._1.fileName === "fileName000"
          recs(1)._1.fileName === "fileName111"
          recs(2)._1.fileName === "fileName222"
        }
      }
    }
  }
}
