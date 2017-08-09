package models

import helpers.{PasswordHash, RandomTokenGenerator, TokenGenerator}
import java.sql.Connection

trait CreateUser extends CreateUserBase {
  val role: UserRole
  val storeUserRepo: StoreUserRepo

  def save(implicit conn: Connection): StoreUser = {
    val salt = RandomTokenGenerator().next
    val hash = PasswordHash.generate(password, salt)
    val user = storeUserRepo.create(
      userName, firstName, middleName, lastName, email, hash, salt, role, Some(companyName)
    )
    SupplementalUserEmail.save(supplementalEmails.toSet, user.id.get)
    user
  }
}

trait CreateUserObject {
  def toForm(m: CreateUser) = Some(
    m.userName, m.firstName, m.middleName, m.lastName, m.email, 
    m.supplementalEmails.map {e => Some(e)},
    (m.password, m.password), m.companyName
  )
}
