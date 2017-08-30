package models

import scala.language.postfixOps
import java.sql.Connection
import anorm._
import anorm.SqlParser
import java.time.Instant

case class UserMetadataId(id: Long) extends AnyVal

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
  birthMonthDay: Option[Int],
  profileComment: Option[String]
)

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
          telNo0, telNo1, telNo2, joindDate, birthMonthDay, profileComment
        )
    }
  }

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
    birthMonthDay: Option[Int] = None,
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
        {profileComment},
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
      'birthMonthDay -> birthMonthDay,
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
    """
  ).on(
    'dateAfter -> dateAfter
  ).as(
    simple *
  )
}
