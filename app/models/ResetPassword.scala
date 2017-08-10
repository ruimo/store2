package models

import java.time.Instant
import anorm._
import anorm.SqlParser

import scala.language.postfixOps
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import helpers.RandomTokenGenerator
import helpers.{PasswordHash, RandomTokenGenerator, TokenGenerator}

case class ResetPasswordId(id: Long) extends AnyVal

case class ResetPassword(
  id: Option[ResetPasswordId] = None,
  storeUserId: Long,
  token: Long,
  resetTime: Instant
)

@Singleton
class ResetPasswordRepo @Inject() (
  storeUserRepo: StoreUserRepo
) {
  val tokenGenerator: TokenGenerator = RandomTokenGenerator()

  val simple = {
    SqlParser.get[Option[Long]]("reset_password.reset_password_id") ~
    SqlParser.get[Long]("reset_password.store_user_id") ~
    SqlParser.get[Long]("reset_password.token") ~
    SqlParser.get[java.time.Instant]("reset_password.reset_time") map {
      case id~storeUserId~token~resetTime =>
        ResetPassword(id.map {ResetPasswordId.apply}, storeUserId, token, resetTime)
    }
  }

  def createNew(storeUserId: Long)(implicit conn: Connection): ResetPassword = createNew(
    storeUserId,
    Instant.now(),
    createToken()
  )

  def createNew(storeUserId: Long, now: Instant, token: Long)(implicit conn: Connection): ResetPassword = {
    SQL(
      """
      insert into reset_password (reset_password_id, store_user_id, token, reset_time)
      values (
        (select nextval('reset_password_seq')),
        {storeUserId}, {token}, {resetTime}
      )
      """
    ).on(
      'storeUserId -> storeUserId,
      'token ->token,
      'resetTime -> now
    ).executeUpdate()

    val id = ResetPasswordId(
      SQL("select currval('reset_password_seq')").as(SqlParser.scalar[Long].single)
    )

    ResetPassword(Some(id), storeUserId, token, now)
  }

  def createToken(): Long = RandomTokenGenerator().next

  def apply(id: ResetPasswordId)(implicit conn: Connection): ResetPassword =
    SQL(
      """
      select * from reset_password where reset_password_id = {id}
      """
    ).on(
      'id -> id.id
    ).as(simple.single)

  def get(id: ResetPasswordId)(implicit conn: Connection): Option[ResetPassword] =
    SQL(
      """
      select * from reset_password where reset_password_id = {id}
      """
    ).on(
      'id -> id.id
    ).as(simple.singleOpt)

  def removeByStoreUserId(storeUserId: Long)(implicit conn: Connection): Long = SQL(
    """
    delete from reset_password where store_user_id = {id}
    """
  ).on(
    'id -> storeUserId
  ).executeUpdate()

  def isValid(storeUserId: Long, token: Long, timeout: Long)(implicit conn: Connection): Boolean = SQL(
    """
    select count(*) from reset_password
    where store_user_id = {storeUserId}
    and token = {token}
    and reset_time > {resetTime}
    """
  ).on(
    'storeUserId -> storeUserId,
    'token -> token,
    'resetTime -> new java.sql.Timestamp(timeout)
  ).as(SqlParser.scalar[Long].single) != 0

  def changePassword(
    storeUserId: Long, token: Long, timeout: Long, password: String
  )(
    implicit conn: Connection
  ): Boolean = if (
    SQL(
      """
      delete from reset_password
      where store_user_id = {storeUserId}
      and token = {token}
      and reset_time > {resetTime}
      """
    ).on(
      'storeUserId -> storeUserId,
      'token -> token,
      'resetTime -> new java.sql.Timestamp(timeout)
    ).executeUpdate() != 0
  ) {
    val salt = tokenGenerator.next
    storeUserRepo.changePassword(storeUserId, PasswordHash.generate(password, salt), salt) != 0
  }
  else {
    false
  }

  def getByStoreUserId(storeUserId: Long)(implicit conn: Connection): Option[ResetPassword] = SQL(
    """
    select * from reset_password
    where store_user_id = {id}
    """
  ).on(
    'id -> storeUserId
  ).as(
    simple.singleOpt
  )
}
