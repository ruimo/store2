package models

import helpers.{PasswordHash, RandomTokenGenerator, TokenGenerator}
import java.sql.Connection

trait CreateUser extends CreateUserBase {
  val role: UserRole
  val storeUserRepo: StoreUserRepo

  def save(implicit conn: Connection): StoreUser = {
    val salt = RandomTokenGenerator().next
    val stretchCount = storeUserRepo.PasswordHashStretchCount()
    val hash = PasswordHash.generate(password, salt, stretchCount)
    val user = storeUserRepo.create(
      userName, firstName, middleName, lastName, email, hash, salt, role, Some(companyName),
      altFirstName, altMiddleName, altLastName, stretchCount
    )
    SupplementalUserEmail.save(supplementalEmails.toSet, user.id.get)
    user
  }
}

trait CreateUserObject {
  def toForm(m: CreateUser) = Some(
    m.userName, m.firstName, m.middleName, m.lastName, m.email, 
    m.supplementalEmails.map {e => Some(e)},
    (m.password, m.password), m.companyName,
    m.altFirstName, m.altMiddleName, m.altLastName
  )
}
