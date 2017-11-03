package models

import java.time.Instant
import java.sql.Connection

case class UpdateUserMetadata(
  photoUrl: Option[String],
  firstNameKana: Option[String],
  middleNameKana: Option[String],
  lastNameKana: Option[String],
  telNo0: Option[String],
  telNo1: Option[String],
  telNo2: Option[String],
  joinedDate: Option[Instant],
  birthMonthDay: Option[String],
  profileComment: Option[String]
) {
  def update(storeUserId: Long)(implicit conn: Connection) {
    UserMetadata.getByStoreUserId(storeUserId) match {
      case None =>
        UserMetadata.createNew(
          storeUserId,
          photoUrl,
          firstNameKana, middleNameKana, lastNameKana,
          telNo0, telNo1, telNo2,
          joinedDate, birthMonthDay.map(md => MonthDay(md.toInt)), profileComment
        )
      case Some(um) =>
        UserMetadata.update(
          storeUserId,
          photoUrl,
          firstNameKana, middleNameKana, lastNameKana,
          telNo0, telNo1, telNo2,
          joinedDate, birthMonthDay.map(md => MonthDay(md.toInt)), profileComment
        )
    }
  }
}

