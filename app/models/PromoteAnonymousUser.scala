package models

import java.sql.Connection

case class PromoteAnonymousUser(
  userName: String,
  firstName: String,
  middleName: Option[String],
  lastName: String,
  email: String,
  passwords: (String, String)
) {
  def isNaivePassword(implicit conn: Connection): Boolean =
    PasswordDictionary.isNaivePassword(passwords._1)

  def update(login: LoginSession)(implicit conn: Connection): Boolean =
    ExceptionMapper.mapException(
      login.storeUser.promoteAnonymousUser(userName, passwords._1, firstName, middleName, lastName, email)
    )
}
