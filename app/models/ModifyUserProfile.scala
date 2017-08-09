package models

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import helpers.PasswordHash

case class ModifyUserProfile(
  firstName: String,
  middleName: Option[String],
  lastName: String,
  email: String,
  password: String
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
  }
}

@Singleton
class ModifyUserProfileRepo @Inject() (
  implicit entryUserRegistrationRepo: EntryUserRegistrationRepo,
  storeUserRepo: StoreUserRepo
) {
  def apply(login: LoginSession): ModifyUserProfile = ModifyUserProfile(
    login.storeUser.firstName,
    login.storeUser.middleName,
    login.storeUser.lastName,
    login.storeUser.email,
    ""
  )
}
