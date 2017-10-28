package models

import anorm._
import anorm.SqlParser
import scala.language.postfixOps
import play.api.i18n.Lang
import java.sql.Connection
import javax.inject.Singleton
import javax.inject.Inject

case class FavoId(value: Long) extends AnyVal

case class ContentId(value: Long) extends AnyVal

case class Favo(
  id: Option[FavoId],
  kind: FavoKind,
  contentId: ContentId,
  storeUserId: Long
)

@Singleton
class FavoRepo @Inject() (
  storeUserRepo: StoreUserRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("favo.favo_id") ~
    SqlParser.get[Int]("favo.kind") ~
    SqlParser.get[Long]("favo.content_id") ~
    SqlParser.get[Long]("favo.store_user_id") map {
      case id~kind~contentId~userId => Favo(
        id.map(FavoId.apply), FavoKind.byIndex(kind), ContentId(contentId), userId
      )
    }
  }

  def create(
    kind: FavoKind, contentId: ContentId, storeUserId: Long
  )(
    implicit conn: Connection
  ): Favo = {
    SQL(
      """
      insert into favo (
        favo_id, kind, content_id, store_user_id
      ) values (
        (select nextval('favo_seq')),
        {kind}, {contentId}, {storeUserId}
      )
      """
    ).on(
      'kind -> kind.ordinal,
      'contentId -> contentId.value,
      'storeUserId -> storeUserId
    ).executeUpdate()

    val id = SQL("select currval('favo_seq')").as(SqlParser.scalar[Long].single)

    Favo(Some(FavoId(id)), kind, contentId, storeUserId)
  }

  def remove(
    kind: FavoKind, contentId: ContentId, storeUserId: Long
  )(
    implicit conn: Connection
  ): Int = {
    SQL(
      """
      delete from favo where kind = {kind} and content_id = {contentId} and store_user_id = {storeUserId}
      """
    ).on(
      'kind -> kind.ordinal,
      'contentId -> contentId.value,
      'storeUserId -> storeUserId
    ).executeUpdate()
  }

  def count(
    kind: FavoKind, contentId: ContentId
  )(
    implicit conn: Connection
  ): Long = SQL(
    """
    select count(*) from favo where kind = {kind} and content_id = {contentId}
    """
  ).on(
    'kind -> kind.ordinal,
    'contentId -> contentId.value
  ).as(SqlParser.scalar[Long].single)

  def isFav(
    kind: FavoKind, contentId: ContentId, storeUserId: Long
  )(
    implicit conn: Connection
  ): Boolean = SQL(
    """
    select exists(
      select * from favo where kind = {kind} and content_id = {contentId} and store_user_id = {storeUserId}
    )
    """
  ).on(
    'kind -> kind.ordinal,
    'contentId -> contentId.value,
    'storeUserId -> storeUserId
  ).as(SqlParser.scalar[Boolean].single)

  def list(
    page: Int, pageSize: Int, orderBy: OrderBy, kind: FavoKind, contentId: ContentId
  )(
    implicit conn: Connection
  ): PagedRecords[StoreUser] = {
    val records = SQL(
      s"""
      select * from favo
      inner join store_user on favo.store_user_id = store_user.store_user_id
      where favo.kind = {kind} and favo.content_id = {contentId}
      order by $orderBy
      limit {pageSize} offset {offset}
      """
    ).on(
      'kind -> kind.ordinal,
      'contentId -> contentId.value,
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      storeUserRepo.simple *
    )

    val n = count(kind, contentId)

    PagedRecords(page, pageSize, (n + pageSize - 1) / pageSize, orderBy, records)
  }
}
