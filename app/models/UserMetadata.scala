package models

import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import scala.language.postfixOps
import java.sql.Connection
import anorm._
import anorm.SqlParser
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoField

case class UserMetadataId(id: Long) extends AnyVal

// MMdd
case class MonthDay(value: Int) extends AnyVal {
  def month: Int = value / 100

  def day = value - 100 * month

  def monthDay: (Int, Int) = {
    val m = month
    (month, value - 100 * month)
  }

  // If the following condition is met, Some(days from now) will be returned. Otherwise None.
  // this day - day of now <= maxDays
  // 
  // Example:
  // MonthDay(1201).isNear(LocalDate.of(2017, 12, 1), 1) == Some(0)
  // MonthDay(1201).isNear(LocalDate.of(2017, 11, 30), 1) == Some(1)
  // MonthDay(1202).isNear(LocalDate.of(2017, 11, 30), 2) == Some(2)
  // MonthDay(1202).isNear(LocalDate.of(2017, 11, 30), 1) == None
  // MonthDay(1201).isNear(LocalDate.of(2017, 12, 2), 1) == None
  // MonthDay(101).isNear(LocalDate.of(2017, 12, 31), 1) == Some(1)
  // MonthDay(228).isNear(LocalDate.of(2016, 3, 1), 1) == None
  // MonthDay(228).isNear(LocalDate.of(2016, 3, 1), 2) == Some(1)
  // MonthDay(228).isNear(LocalDate.of(2017, 3, 1), 1) == Some(1)
  def isNear(now: LocalDate = LocalDate.now(), maxDays: Int): Option[Int] = {
    val (m: Int, d: Int) = monthDay
    val nowDays = now.toEpochDay

    def check(year: Int): Option[Int] = {
      val birthDays = LocalDate.of(year, m, d).toEpochDay
      if (nowDays <= birthDays && birthDays <= nowDays + maxDays) Some((birthDays - nowDays).toInt) else None
    }

    check(now.getYear).orElse(check(now.getYear + 1))
  }
}

case class UserMetadata(
  id: Option[UserMetadataId] = None,
  storeUserId: Long,
  photoUrl: Option[String],
  firstNameKana: Option[String],
  middleNameKana: Option[String],
  lastNameKana: Option[String],
  telNo0: Option[String],
  telNo1: Option[String],
  telNo2: Option[String],
  joinedDate: Option[Instant],
  birthMonthDay: Option[MonthDay],
  profileComment: Option[String]
) {
  lazy val fullKanaName = firstNameKana.getOrElse("") + middleNameKana.map(n => " " + n).getOrElse("") + " " + lastNameKana.getOrElse("")
}

object UserMetadata {
  val simple = {
    SqlParser.get[Option[Long]]("user_metadata.user_metadata_id") ~
    SqlParser.get[Long]("user_metadata.store_user_id") ~
    SqlParser.get[Option[String]]("user_metadata.photo_url") ~
    SqlParser.get[Option[String]]("user_metadata.first_name_kana") ~
    SqlParser.get[Option[String]]("user_metadata.middle_name_kana") ~
    SqlParser.get[Option[String]]("user_metadata.last_name_kana") ~
    SqlParser.get[Option[String]]("user_metadata.tel_no0") ~
    SqlParser.get[Option[String]]("user_metadata.tel_no1") ~
    SqlParser.get[Option[String]]("user_metadata.tel_no2") ~
    SqlParser.get[Option[Instant]]("user_metadata.joined_date") ~
    SqlParser.get[Option[Int]]("user_metadata.birth_month_day") ~
    SqlParser.get[Option[String]]("user_metadata.profile_comment") map {
      case id~userId~photoUrl~firstNameKana~middleNameKana~lastNameKana~telNo0~telNo1~telNo2~joindDate~birthMonthDay~profileComment =>
        UserMetadata(
          id.map(UserMetadataId.apply), userId, photoUrl, firstNameKana, middleNameKana, lastNameKana,
          telNo0, telNo1, telNo2, joindDate, birthMonthDay.map(MonthDay.apply), profileComment
        )
    }
  }

  def getByStoreUserId(storeUserId: Long)(implicit conn: Connection): Option[UserMetadata] = SQL(
    "select * from user_Metadata where store_user_id = {id}"
  ).on(
    'id -> storeUserId
  ).as(
    simple.singleOpt
  )

  def createNew(
    storeUserId: Long,
    photoUrl: Option[String] = None,
    firstNameKana: Option[String] = None,
    middleNameKana: Option[String] = None,
    lastNameKana: Option[String] = None,
    telNo0: Option[String] = None,
    telNo1: Option[String] = None,
    telNo2: Option[String] = None,
    joinedDate: Option[Instant] = None,
    birthMonthDay: Option[MonthDay] = None,
    profileComment: Option[String] = None
  )(implicit conn: Connection): UserMetadata = {
    SQL(
      """
      insert into user_metadata (
        user_metadata_id,
        store_user_id,
        photo_url,
        first_name_kana,
        middle_name_kana,
        last_name_kana,
        tel_no0,
        tel_no1,
        tel_no2,
        joined_date,
        birth_month_day,
        profile_comment
      ) values (
        (select nextval('user_metadata_seq')),
        {storeUserId},
        {photoUrl},
        {firstNameKana},
        {middleNameKana},
        {lastNameKana},
        {telNo0},
        {telNo1},
        {telNo2},
        {joinedDate},
        {birthMonthDay},
        {profileComment}
      )
      """
    ).on(
      'storeUserId -> storeUserId,
      'photoUrl -> photoUrl,
      'firstNameKana -> firstNameKana,
      'middleNameKana -> middleNameKana,
      'lastNameKana -> lastNameKana,
      'telNo0 -> telNo0,
      'telNo1 -> telNo1,
      'telNo2 -> telNo2,
      'joinedDate -> joinedDate,
      'birthMonthDay -> birthMonthDay.map(_.value),
      'profileComment -> profileComment
    ).executeUpdate()

    val id = SQL("select currval('user_metadata_seq')").as(SqlParser.scalar[Long].single)

    UserMetadata(
      Some(UserMetadataId(id)),
      storeUserId, photoUrl, firstNameKana, middleNameKana, lastNameKana,
      telNo0, telNo1, telNo2,
      joinedDate, birthMonthDay, profileComment
    )
  }

  def recentlyJoindUsers(
    dateAfter: Instant
  )(implicit conn: Connection): Seq[UserMetadata] = SQL(
    """
    select * from user_metadata where joined_date > {dateAfter}
    order by joined_date desc
    """
  ).on(
    'dateAfter -> dateAfter
  ).as(
    simple *
  )

  def nearBirthDayUsers(
    now: LocalDateTime = LocalDateTime.now()
  )(implicit conn: Connection): Seq[UserMetadata] = {
    val days: Seq[Int] = (0 to 7).map { day =>
      val befo = now.plus(day, ChronoUnit.DAYS)
      befo.getMonthValue() * 100 + befo.getDayOfMonth()
    }

    SQL(
      """
      select * from user_metadata where birth_month_day in ({day0}, {day1}, {day2}, {day3}, {day4}, {day5}, {day6}, {day7})
      """
    ).on(
      'day0 -> days(0),
      'day1 -> days(1),
      'day2 -> days(2),
      'day3 -> days(3),
      'day4 -> days(4),
      'day5 -> days(5),
      'day6 -> days(6),
      'day7 -> days(7),
    ).as(
      simple *
    )
  }

  def update(
    storeUserId: Long,
    photoUrl: Option[String] = None,
    firstNameKana: Option[String] = None,
    middleNameKana: Option[String] = None,
    lastNameKana: Option[String] = None,
    telNo0: Option[String] = None,
    telNo1: Option[String] = None,
    telNo2: Option[String] = None,
    joinedDate: Option[Instant] = None,
    birthMonthDay: Option[MonthDay] = None,
    profileComment: Option[String] = None
  )(implicit conn: Connection) = {
    SQL(
      """
      update user_metadata set
        photo_url = {photoUrl},
        first_name_kana = {firstNameKana},
        middle_name_kana = {middleNameKana},
        last_name_kana = {lastNameKana},
        tel_no0 = {telNo0},
        tel_no1 = {telNo1},
        tel_no2 = {telNo2},
        joined_date = {joinedDate},
        birth_month_day = {birthMonthDay},
        profile_comment = {profileComment}
      where store_user_id = {storeUserId}
      """
    ).on(
      'storeUserId -> storeUserId,
      'photoUrl -> photoUrl,
      'firstNameKana -> firstNameKana,
      'middleNameKana -> middleNameKana,
      'lastNameKana -> lastNameKana,
      'telNo0 -> telNo0,
      'telNo1 -> telNo1,
      'telNo2 -> telNo2,
      'joinedDate -> joinedDate,
      'birthMonthDay -> birthMonthDay.map(_.value),
      'profileComment -> profileComment
    ).executeUpdate()
  }
}
