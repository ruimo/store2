package models

import java.time.LocalDateTime
import scala.collection.{immutable => imm}
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
import java.time.temporal.ChronoUnit

class UserMetadataSpec extends Specification with InjectorSupport {
  "ItemInquiry" should {
    "Can query recently joind users." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val now = Instant.now()

      inject[Database].withConnection { implicit conn =>
        val user01 = inject[StoreUserRepo].create(
          userName = "uno01",
          firstName = "",
          middleName = None,
          lastName = "",
          email = "",
          passwordHash = 0L,
          salt = 0L,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        val user02 = inject[StoreUserRepo].create(
          userName = "uno02",
          firstName = "",
          middleName = None,
          lastName = "",
          email = "",
          passwordHash = 0L,
          salt = 0L,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        val user03 = inject[StoreUserRepo].create(
          userName = "uno03",
          firstName = "",
          middleName = None,
          lastName = "",
          email = "",
          passwordHash = 0L,
          salt = 0L,
          userRole = UserRole.NORMAL,
          companyName = None
        )

        UserMetadata.getByStoreUserId(user01.id.get) === None

        val um01 = UserMetadata.createNew(
          user01.id.get,
          Some("url01"),
          Some("firstNameKana01"),
          Some("middleNameKana01"),
          Some("lastNameKana01"),
          Some("tel01"),
          Some("tel02"),
          Some("tel03"),
          joinedDate = Some(now.plus(-30, ChronoUnit.DAYS)),
          birthMonthDay = Some(123),
          profileComment = Some("comment01")
        )

        UserMetadata.getByStoreUserId(user01.id.get) === Some(um01)

        val um02 = UserMetadata.createNew(
          user02.id.get,
          Some("url02"),
          Some("firstNameKana02"),
          Some("middleNameKana02"),
          Some("lastNameKana02"),
          Some("tel01"),
          Some("tel02"),
          Some("tel03"),
          joinedDate = Some(now.plus(-29, ChronoUnit.DAYS)),
          birthMonthDay = Some(123),
          profileComment = Some("comment02")
        )

        val um03 = UserMetadata.createNew(
          user03.id.get,
          Some("url03"),
          Some("firstNameKana03"),
          Some("middleNameKana03"),
          Some("lastNameKana03"),
          Some("tel01"),
          Some("tel02"),
          Some("tel03"),
          joinedDate = None,
          birthMonthDay = Some(123),
          profileComment = Some("comment03")
        )

        UserMetadata.recentlyJoindUsers(now.plus(-28, ChronoUnit.DAYS)).size === 0
        UserMetadata.recentlyJoindUsers(now.plus(-29, ChronoUnit.DAYS)).size === 0
        UserMetadata.recentlyJoindUsers(now.plus(-30, ChronoUnit.DAYS)) === Seq(um02)
        UserMetadata.recentlyJoindUsers(now.plus(-31, ChronoUnit.DAYS)) === Seq(um02, um01)
      }
    }

    "Can query recently birthday users." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val now = Instant.now()

      inject[Database].withConnection { implicit conn =>
        val user01 = inject[StoreUserRepo].create(
          userName = "uno01",
          firstName = "", middleName = None, lastName = "",
          email = "", passwordHash = 0L, salt = 0L, userRole = UserRole.NORMAL, companyName = None
        )

        val user02 = inject[StoreUserRepo].create(
          userName = "uno02",
          firstName = "", middleName = None, lastName = "",
          email = "", passwordHash = 0L, salt = 0L, userRole = UserRole.NORMAL, companyName = None
        )

        val user03 = inject[StoreUserRepo].create(
          userName = "uno03",
          firstName = "", middleName = None, lastName = "",
          email = "", passwordHash = 0L, salt = 0L, userRole = UserRole.NORMAL, companyName = None
        )

        val um01 = UserMetadata.createNew(
          user01.id.get,
          Some("url01"),
          Some("firstNameKana01"),
          Some("middleNameKana01"),
          Some("lastNameKana01"),
          Some("tel01"),
          Some("tel02"),
          Some("tel03"),
          joinedDate = Some(now.plus(-30, ChronoUnit.DAYS)),
          birthMonthDay = Some(131),
          profileComment = Some("comment01")
        )

        val um02 = UserMetadata.createNew(
          user02.id.get,
          Some("url02"),
          Some("firstNameKana02"),
          Some("middleNameKana02"),
          Some("lastNameKana02"),
          Some("tel01"),
          Some("tel02"),
          Some("tel03"),
          joinedDate = Some(now.plus(-29, ChronoUnit.DAYS)),
          birthMonthDay = Some(201),
          profileComment = Some("comment02")
        )

        val um03 = UserMetadata.createNew(
          user03.id.get,
          Some("url03"),
          Some("firstNameKana03"),
          Some("middleNameKana03"),
          Some("lastNameKana03"),
          Some("tel01"),
          Some("tel02"),
          Some("tel03"),
          joinedDate = None,
          birthMonthDay = Some(202),
          profileComment = Some("comment03")
        )

        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 23, 0, 0)).toSet === imm.HashSet()
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 24, 0, 0)).toSet === imm.HashSet(um01)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 25, 0, 0)).toSet === imm.HashSet(um01, um02)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 26, 0, 0)).toSet === imm.HashSet(um01, um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 27, 0, 0)).toSet === imm.HashSet(um01, um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 28, 0, 0)).toSet === imm.HashSet(um01, um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 29, 0, 0)).toSet === imm.HashSet(um01, um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 30, 0, 0)).toSet === imm.HashSet(um01, um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 1, 31, 0, 0)).toSet === imm.HashSet(um01, um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 2, 1, 0, 0)).toSet === imm.HashSet(um02, um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 2, 2, 0, 0)).toSet === imm.HashSet(um03)
        UserMetadata.nearBirthDayUsers(LocalDateTime.of(2000, 2, 3, 0, 0)).toSet === imm.HashSet()
      }
    }
  }
}

