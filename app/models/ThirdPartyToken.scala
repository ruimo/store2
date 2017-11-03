package models

import play.Logger
import java.time.Instant
import java.io.{InputStream, FileInputStream}
import com.ruimo.scoins.LoanPattern._
import java.time.Instant
import java.nio.file.Path
import anorm._
import java.sql.Connection
import com.ruimo.scoins.ImmutableByteArray
import javax.inject.Singleton
import javax.inject.Inject

case class ThirdPartyTokenId(value: Long) extends AnyVal

case class ThirdPartyTokenKind(value: Int) extends AnyVal

case class ThirdPartyToken(
  id: Option[ThirdPartyTokenId],
  kind: ThirdPartyTokenKind,
  storeUserId: Long,
  token: String,
  expires: Option[Instant]
)

@Singleton
class ThirdPartyTokenRepo @Inject() (
) {
  val simple = {
    SqlParser.get[Option[Long]]("third_party_token.third_party_token_id") ~
    SqlParser.get[Int]("third_party_token.kind") ~
    SqlParser.get[Long]("third_party_token.store_user_id") ~
    SqlParser.get[String]("third_party_token.token") ~
    SqlParser.get[Option[Instant]]("third_party_token.expires") map {
      case id~kind~storeUserId~token~expires => ThirdPartyToken(
        id.map(ThirdPartyTokenId.apply), ThirdPartyTokenKind(kind),
        storeUserId, token, expires
      )
    }
  }

  def create(
    kind: ThirdPartyTokenKind, userId: Long, token: String, expires: Option[Instant]
  )(
    implicit conn: Connection
  ): ThirdPartyToken = {
    SQL(
      """
      insert into third_party_token(
        third_party_token_id, kind, store_user_id, token, expires
      ) values (
        (select nextval('third_party_token_seq')),
        {kind}, {storeUserId}, {token}, {expires}
      )
      """
    ).on(
      'kind -> kind.value,
      'storeUserId -> userId,
      'token -> token,
      'expires -> expires
    ).executeUpdate()

    val id = SQL("select currval('third_party_token_seq')").as(SqlParser.scalar[Long].single)

    ThirdPartyToken(
      Some(ThirdPartyTokenId(id)), kind, userId, token, expires
    )
  }

  def get(
    kind: ThirdPartyTokenKind, userId: Long
  )(
    implicit conn: Connection
  ): Option[ThirdPartyToken] = SQL(
    """
    select * from third_party_token where kind = {kind} and store_user_id = {userId}
    """
  ).on(
    'kind -> kind.value,
    'userId -> userId
  ).as(
    simple.singleOpt
  )
}
