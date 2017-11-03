package models

import org.specs2.mutable._

import anorm._
import play.api.test._
import play.api.test.Helpers._
import com.ruimo.csv.CsvRecord
import com.ruimo.csv.CsvHeader
import com.ruimo.csv.CsvParseException
import scala.util.{Try, Failure, Success}
import com.ruimo.scoins.Scoping._
import helpers.PasswordHash
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.Database
import java.time.Instant

class UserSpec extends Specification with InjectorSupport {
  "User" should {
    "User count should be zero when table is empty" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        inject[StoreUserRepo].count === 0
      }
    }

    "User count should reflect the number of records in the table" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        inject[StoreUserRepo].count === 1
      }
    }

    "User can be queried by username" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )

        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", None, "lastName2", "email2",
          1L, 2L, UserRole.ADMIN, None
        )

        inject[StoreUserRepo].findByUserName("userName").get === user1
        inject[StoreUserRepo].findByUserName("userName2").get === user2
      }
    }

    "Can query user metadata" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )

        val list = inject[StoreUserRepo].listUsers()
        list.records.size === 1
        list.records(0)._1.user === user1
        list.records(0)._2 === None

        val umd = UserMetadata.createNew(
          user1.id.get,
          Some("url"),
          Some("firstNameKana"), Some("middleNameKana"), Some("lastNameKana"),
          Some("tel00"), Some("tel01"), Some("tel02"),
          Some(Instant.ofEpochMilli(1234L)),
          Some(MonthDay(123)),
          Some("Comment")
        )

        val list2 = inject[StoreUserRepo].listUsers()
        list2.records.size === 1
        list2.records(0)._1.user === user1
        list2.records(0)._2 === Some(umd)
      }
    }

    "Can list only employee" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "1-userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.NORMAL, Some("companyName")
        )

        val user2 = inject[StoreUserRepo].create(
          "2-userName2", "firstName2", None, "lastName2", "email2",
          1L, 2L, UserRole.NORMAL, None
        )

        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 2
          records(0)._1.user === user1
          records(0)._2 === None
          records(1)._1.user === user2
          records(1)._2 === None
        }

        doWith(inject[StoreUserRepo].listUsers(employeeSiteId = Some(1)).records) { records =>
          records.size === 1
          records(0)._1.user === user1
          records(0)._2 === None
        }
          
        doWith(inject[StoreUserRepo].listUsers(employeeSiteId = Some(2)).records) { records =>
          records.size === 1
          records(0)._1.user === user2
          records(0)._2 === None
        }
      }
    }

    "Notification email user" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )

        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", None, "lastName2", "email2",
          1L, 2L, UserRole.ADMIN, None
        )

        val notification = inject[OrderNotificationRepo].createNew(user2.id.get)

        val u1 = inject[StoreUserRepo].withSite(user1.id.get)
        u1.sendNoticeMail === false

        val u2 = inject[StoreUserRepo].withSite(user2.id.get)
        u2.sendNoticeMail === true

        val list = inject[StoreUserRepo].listUsers()
        list.records.size === 2
        list.records(0)._1.user === user1
        list.records(0)._1.sendNoticeMail === false
        list.records(0)._2 === None

        list.records(1)._1.user === user2
        list.records(1)._1.sendNoticeMail === true
        list.records(1)._2 === None
      }
    }

    "listUsers list user ordered by user name" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )

        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", None, "lastName2", "email2",
          1L, 2L, UserRole.ADMIN, None
        )
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")

        val siteUser = inject[SiteUserRepo].createNew(user2.id.get, site1.id.get)

        val list = inject[StoreUserRepo].listUsers()
        list.records.size === 2
        list.records(0)._1.user === user1
        list.records(0)._1.siteUser === None
        list.records(0)._1.sendNoticeMail === false
        list.records(0)._2 === None

        list.records(1)._1.user === user2
        list.records(1)._1.siteUser.get === siteUser
        list.records(1)._1.sendNoticeMail === false
        list.records(1)._2 === None
      }
    }

    "Can add users by csv" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val header = CsvHeader("CompanyId", "EmployeeNo", "Password")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        inject[StoreUserRepo].maintainByCsv(Iterator(
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "01234567", "pass001"))),
          Success(CsvRecord(2, header, Vector(site1.id.get.toString, "98765432", "pass002")))
        ))

        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 2
          doWith(records.map {_._1.user}.map { r => (r.userName, r)}.toMap) { map =>
            doWith(map(site1.id.get.toString + "-01234567")) { rec =>
              rec.firstName === ""
              rec.middleName === None
              rec.lastName === ""
              rec.email === ""
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === None
              PasswordHash.generate("pass001", rec.salt) === rec.passwordHash
            }

            doWith(map(site1.id.get.toString + "-98765432")) { rec =>
              rec.firstName === ""
              rec.middleName === None
              rec.lastName === ""
              rec.email === ""
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === None
              PasswordHash.generate("pass002", rec.salt) === rec.passwordHash
            }
          }
        }
      }
    }

    "Exception should be thrown if an error is found in csv" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val header = CsvHeader("CompanyId", "EmployeeNo", "Password")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        inject[StoreUserRepo].maintainByCsv(Iterator(
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "01234567", "pass001"))),
          Failure(new CsvParseException("", new Exception, 1))
        )) must throwA[CsvParseException]

        // No record should changed.
        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 0
        }
      }
    }

    "Exiting record should not be changed by csv" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val header = CsvHeader("CompanyId", "EmployeeNo", "Password")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        // Existing record. This should not be changed.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-01234567", "first001", Some("middle001"), "last001",
          "email001", 123L, 234L, UserRole.NORMAL, Some("company001")
        )

        val (insCount, delCount) = inject[StoreUserRepo].maintainByCsv(Iterator(
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "01234567", "pass001"))),
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "98765432", "pass002")))
        ))

        insCount === 1
        delCount === 0

        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 2
          doWith(records.map {_._1.user}.map { r => (r.userName, r)}.toMap) { map =>
            doWith(map(site1.id.get.toString + "-01234567")) { rec =>
              rec.userName === site1.id.get.toString + "-01234567"
              rec.firstName === "first001"
              rec.middleName === Option("middle001")
              rec.lastName === "last001"
              rec.email === "email001"
              rec.passwordHash === 123L
              rec.salt === 234L
              rec.userRole === UserRole.NORMAL
              rec.companyName === Some("company001")
            }
          }
        }
      }
    }

    "Non exiting record should not be deleted by csv" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val header = CsvHeader("CompanyId", "EmployeeNo", "Password")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        // Existing record. This should not be deleted.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-01234567", "first001", Some("middle001"), "last001",
          "email001", 123L, 234L, UserRole.NORMAL, Some("company001")
        )

        // Existing record. This should not be changed.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-user002", "first002", Some("middle002"), "last002",
          "email002", 123L, 234L, UserRole.NORMAL, Some("company002")
        )

        val (insCount, delCount) = inject[StoreUserRepo].maintainByCsv(Iterator(
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "01234567", "pass001")))
        ))

        insCount === 0
        delCount === 1

        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 1
          doWith(records.map {_._1.user}.map { r => (r.userName, r)}.toMap) { map =>
            map.get(site1.id.get.toString + "-user002") === None
          }
        }
      }
    }

    "Admin users should not be affected by csv" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val header = CsvHeader("CompanyId", "EmployeeNo", "Password")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")

        // Existing record. This should be deleted.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-11111111", "first001", Some("middle001"), "last001",
          "email001", 123L, 234L, UserRole.NORMAL, Some("company001")
        )

        // Existing record. This should be deleted.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-22222222", "first002", Some("middle002"), "last002",
          "email002", 123L, 234L, UserRole.NORMAL, Some("company002")
        )

        // Existing record. This should not be changed.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-33333333", "first003", Some("middle003"), "last003",
          "email003", 123L, 234L, UserRole.NORMAL, Some("company003")
        )

          // Existing record. This should not be changed.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-44444444", "first004", Some("middle004"), "last004",
          "email004", 123L, 234L, UserRole.NORMAL, Some("company004")
        )

        // Existing record. This should not be changed because this is admin.
        inject[StoreUserRepo].create(
          site1.id.get.toString + "-55555555", "first005", Some("middle005"), "last005",
          "email005", 123L, 234L, UserRole.ADMIN, Some("company005")
        )

        val (insCount, delCount) = inject[StoreUserRepo].maintainByCsv(Iterator(
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "33333333", "pass003"))),
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "44444444", "pass004"))),
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "66666666", "pass006"))),
          Success(CsvRecord(1, header, Vector(site1.id.get.toString, "77777777", "pass007")))
        ))

        insCount === 2
        delCount === 2

        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 5
          doWith(records.map {_._1.user}.map { r => (r.userName, r)}.toMap) { map =>
            map.get(site1.id.get.toString + "-11111111") === None
            map.get(site1.id.get.toString + "-22222222") === None
            doWith(map(site1.id.get.toString + "-33333333")) { rec =>
              rec.userName === site1.id.get.toString + "-33333333"
              rec.firstName === "first003"
              rec.middleName === Option("middle003")
              rec.lastName === "last003"
              rec.email === "email003"
              rec.passwordHash === 123L
              rec.salt === 234L
              rec.userRole === UserRole.NORMAL
              rec.companyName === Some("company003")
            }

            doWith(map(site1.id.get.toString + "-44444444")) { rec =>
              rec.userName === site1.id.get.toString + "-44444444"
              rec.firstName === "first004"
              rec.middleName === Option("middle004")
              rec.lastName === "last004"
              rec.email === "email004"
              rec.passwordHash === 123L
              rec.salt === 234L
              rec.userRole === UserRole.NORMAL
              rec.companyName === Some("company004")
            }

            doWith(map(site1.id.get.toString + "-55555555")) { rec =>
              rec.userName === site1.id.get.toString + "-55555555"
              rec.firstName === "first005"
              rec.middleName === Option("middle005")
              rec.lastName === "last005"
              rec.email === "email005"
              rec.passwordHash === 123L
              rec.salt === 234L
              rec.userRole === UserRole.ADMIN
              rec.companyName === Some("company005")
            }

            doWith(map(site1.id.get.toString + "-66666666")) { rec =>
              rec.firstName === ""
              rec.middleName === None
              rec.lastName === ""
              rec.email === ""
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === None
              PasswordHash.generate("pass006", rec.salt) === rec.passwordHash
            }

            doWith(map(site1.id.get.toString + "-77777777")) { rec =>
              rec.firstName === ""
              rec.middleName === None
              rec.lastName === ""
              rec.email === ""
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === None
              PasswordHash.generate("pass007", rec.salt) === rec.passwordHash
            }
          }
        }
      }
    }

    "Can filter csv" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val header = CsvHeader("CompanyId", "EmployeeNo", "Password")
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店1")
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "商店2")
        val siteId = site1.id.get.toString

        // Existing record. This should not be affected.
        inject[StoreUserRepo].create(
          site2.id.get.toString + "-11111111", "first001", Some("middle001"), "last001",
          "email001", 123L, 234L, UserRole.NORMAL, Some("company001")
        )

        inject[StoreUserRepo].maintainByCsv(
          Iterator(
            Success(CsvRecord(1, header, Vector(site1.id.get.toString, "11111111", "pass001"))),
            Success(CsvRecord(2, header, Vector(site1.id.get.toString, "22222222", "pass002"))),
            Success(CsvRecord(3, header, Vector(site2.id.get.toString, "33333333", "pass003")))
          ),
          rec => rec('CompanyId) == siteId,
          Some("user_name like '" + siteId + "-%'")
        )

        doWith(inject[StoreUserRepo].listUsers().records) { records =>
          records.size === 3
          doWith(records.map {_._1.user}.map { r => (r.userName, r)}.toMap) { map =>
            map.get(site2.id.get.toString + "-33333333") === None
            doWith(map(site1.id.get.toString + "-11111111")) { rec =>
              rec.firstName === ""
              rec.middleName === None
              rec.lastName === ""
              rec.email === ""
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === None
              PasswordHash.generate("pass001", rec.salt) === rec.passwordHash
            }
            doWith(map(site1.id.get.toString + "-22222222")) { rec =>
              rec.firstName === ""
              rec.middleName === None
              rec.lastName === ""
              rec.email === ""
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === None
              PasswordHash.generate("pass002", rec.salt) === rec.passwordHash
            }
            doWith(map(site2.id.get.toString + "-11111111")) { rec =>
              rec.firstName === "first001"
              rec.middleName === Some("middle001")
              rec.lastName === "last001"
              rec.email === "email001"
              rec.deleted === false
              rec.userRole === UserRole.NORMAL
              rec.companyName === Some("company001")
            }
          }
        }
      }
    }

    "Can change password" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          111L, 222L, UserRole.ADMIN, Some("companyName2")
        )
          
        inject[StoreUserRepo].changePassword(user1.id.get, 123L, 234L)

        doWith(inject[StoreUserRepo].apply(user1.id.get)) { u =>
          u.userName === "userName"
          u.passwordHash === 123L
          u.salt === 234L
        }
      }
    }

    "Can query store user by email" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "userName", "firstName", Some("middleName"), "lastName", "email",
          1L, 2L, UserRole.ADMIN, Some("companyName")
        )
        val user2 = inject[StoreUserRepo].create(
          "userName2", "firstName2", Some("middleName2"), "lastName2", "email2",
          111L, 222L, UserRole.ADMIN, Some("companyName2")
        )

        inject[StoreUserRepo].getByEmail("email3") === None
        inject[StoreUserRepo].getByEmail("email2") === Some(user2)
      }
    }

    "Can query registered employees." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        // Not employee, registered
        val user1 = inject[StoreUserRepo].create(
          userName = "user01", // Not employee (n-mmmm)
          firstName = "firstName", // registered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL, // Normal user
          companyName = None
        )

        // Not employee, not registered
        val user2 = inject[StoreUserRepo].create(
          userName = "user02", // Not employee (n-mmmm)
          firstName = "", // unregistered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL, // Normal user
          companyName = None
        )

        // Employee, not registered
        val user3 = inject[StoreUserRepo].create(
          userName = "1-111111", // Employee (n-mmmm)
          firstName = "", // unregistered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL, // Normal user
          companyName = None
        )

        // Employee, registered
        val user4 = inject[StoreUserRepo].create(
          userName = "1-222222", // Employee (n-mmmm)
          firstName = "firstName", // registered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL, // Normal user
          companyName = None
        )

        // Super user, not registered
        val user5 = inject[StoreUserRepo].create(
          userName = "11-333333", // In employee format (n-mmmm), but role is not normal.
          firstName = "", // unregistered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.ADMIN, // Admin
          companyName = None
        )

        // Super user, registered
        val user6 = inject[StoreUserRepo].create(
          userName = "11-4444444", // In employee format (n-mmmm), but role is not normal.
          firstName = "firstName", // registered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.ADMIN, // Admin
          companyName = None
        )

        // Site owner, not registered
        val user7 = inject[StoreUserRepo].create(
          userName = "10-555555", // In employee format (n-mmmm), but site owner is not employee.
          firstName = "", // unregistered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "Store01")
        val siteUser1 = inject[SiteUserRepo].createNew(user7.id.get, site1.id.get)

        // Store owner, registered
        val user8 = inject[StoreUserRepo].create(
          userName = "10-66666666", // In employee format (n-mmmm), but site owner is not employee.
          firstName = "firstName", // registered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL,
          companyName = None
        )
        val site2 = inject[SiteRepo].createNew(localeInfo.Ja, "Store02")
        val siteUser2 = inject[SiteUserRepo].createNew(user8.id.get, site2.id.get)

        // Employee, registered
        val user9 = inject[StoreUserRepo].create(
          userName = "10-77777777", // In employee format (n-mmmm), but site owner is not employee.
          firstName = "firstName", // registered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        // Employee, unregistered
        val user10 = inject[StoreUserRepo].create(
          userName = "10-99999999", // In employee format (n-mmmm), but site owner is not employee.
          firstName = "", // unregistered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        // Employee, unregistered
        val user11 = inject[StoreUserRepo].create(
          userName = "10-12345678", // In employee format (n-mmmm), but site owner is not employee.
          firstName = "", // unregistered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        val sum = inject[StoreUserRepo].registeredEmployeeCount
        sum.size === 2

        doWith(sum(1)) { s=>
          s.registeredCount === 1
          s.allCount === 2
        }

        doWith(sum(10)) { s=>
          s.registeredCount === 1
          s.allCount === 3
        }
      }
    }

    "Can save/load supllemental email" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          userName = "user01", // Not employee (n-mmmm)
          firstName = "firstName", // registered
          middleName = None,
          lastName = "lastName",
          email = "null@ruimo.com",
          passwordHash = 0,
          salt = 0,
          userRole = UserRole.NORMAL, // Normal user
          companyName = None
        )

        SupplementalUserEmail.load(user1.id.get).size === 0
        SupplementalUserEmail.save(Set("email01", "email02"), user1.id.get)
        doWith(SupplementalUserEmail.load(user1.id.get)) { tbl =>
          tbl.size === 2
          doWith(tbl.map {e => (e.email, e.storeUserId)}) { t =>
            t.contains("email01" -> user1.id.get) === true
            t.contains("email02" -> user1.id.get) === true
          }
        }
      }
    }
  }
}

