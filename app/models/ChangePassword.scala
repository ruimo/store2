package models

import anorm._
import anorm.SqlParser
import play.api.db._
import scala.language.postfixOps
import java.sql.Connection
import helpers.RandomTokenGenerator
import helpers.{PasswordHash, TokenGenerator, RandomTokenGenerator}

case class ChangePassword(
  currentPassword: String,
  passwords: (String, String)
)(
  implicit storeUserRepo: StoreUserRepo
) {
  def changePassword(storeUserId: Long)(implicit conn: Connection): Boolean = {
    val stretchCount = storeUserRepo.PasswordHashStretchCount()
    val salt = ChangePassword.tokenGenerator.next
    val hash = PasswordHash.generate(passwords._1, salt, stretchCount)
    storeUserRepo.changePassword(storeUserId, hash, salt, stretchCount) != 0
  }
}

object ChangePassword {
  val tokenGenerator: TokenGenerator = RandomTokenGenerator()
}
