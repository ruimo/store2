package models

import play.api.db.Database
import java.time.Instant
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import helpers.PasswordHash

case class ModifyUserProfile(
  firstName: String,
  middleName: Option[String],
  lastName: String,
  email: String,
  password: String,
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
)(
  implicit entryUserRegistrationRepo: EntryUserRegistrationRepo,
  storeUserRepo: StoreUserRepo
) {
  def save(login: LoginSession)(implicit conn: Connection) {
    val salt = entryUserRegistrationRepo.tokenGenerator.next
    val stretchCount: Int = storeUserRepo.PasswordHashStretchCount()
    val passwordHash = PasswordHash.generate(password, salt, stretchCount)

    storeUserRepo.update(
      login.storeUser.copy(
        firstName = firstName,
        middleName = middleName,
        lastName = lastName,
        email = email,
        salt = salt,
        passwordHash = passwordHash,
        stretchCount = stretchCount
      )
    )

println("photoUrl = " + photoUrl)
    UpdateUserMetadata(
      photoUrl,
      firstNameKana, middleNameKana, lastNameKana,
      telNo0, telNo1, telNo2,
      joinedDate,
      birthMonthDay,
      profileComment
    ).update(login.storeUser.id.get)
  }
}

@Singleton
class ModifyUserProfileRepo @Inject() (
  implicit entryUserRegistrationRepo: EntryUserRegistrationRepo,
  storeUserRepo: StoreUserRepo,
  implicit val db: Database
) {
  def apply(login: LoginSession): ModifyUserProfile = {
    val um: Option[UserMetadata] = db.withConnection { implicit conn =>
      UserMetadata.getByStoreUserId(login.userId)
    }

    ModifyUserProfile(
      login.storeUser.firstName,
      login.storeUser.middleName,
      login.storeUser.lastName,
      login.storeUser.email,
      "",
      um.flatMap(_.photoUrl),
      um.flatMap(_.firstNameKana),
      um.flatMap(_.middleNameKana),
      um.flatMap(_.lastNameKana),
      um.flatMap(_.telNo0),
      um.flatMap(_.telNo1),
      um.flatMap(_.telNo2),
      um.flatMap(_.joinedDate),
      um.flatMap(_.birthMonthDay.map(_.toString)),
      um.flatMap(_.profileComment)
    )
  }
}
