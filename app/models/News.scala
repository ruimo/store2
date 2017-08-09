package models

import anorm._
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

case class NewsId(id: Long) extends AnyVal

case class News(
  id: Option[NewsId] = None,
  siteId: Option[Long],
  title: String,
  contents: String,
  releaseTime: LocalDateTime,
  updatedTime: LocalDateTime
)

@Singleton
class NewsRepo @Inject() (
  siteRepo: SiteRepo
) {
  val MaxDate: Long = java.sql.Date.valueOf("9999-12-31").getTime
  val simple = {
    SqlParser.get[Option[Long]]("news_id") ~
    SqlParser.get[Option[Long]]("site_id") ~
    SqlParser.get[String]("title") ~
    SqlParser.get[String]("contents") ~
    SqlParser.get[java.time.LocalDateTime]("release_time") ~
    SqlParser.get[java.time.LocalDateTime]("updated_time") map {
      case id~siteId~title~contents~releaseTime~updatedTime =>
        News(id.map(NewsId.apply), siteId, title, contents, releaseTime, updatedTime)
    }
  }

  val withSite = simple ~ (siteRepo.simple ?) map {
    case news~site => (news, site)
  }

  def apply(id: NewsId)(implicit conn: Connection): (News, Option[Site]) = SQL(
    """
    select * from news n
    left join site s on s.site_id = n.site_id
    where n.news_id = {id}
    """
  ).on(
    'id -> id.id
  ).as(withSite.single)

  def list(
    page: Int = 0,
    pageSize: Int = 10,
    orderBy: OrderBy = OrderBy("news.release_time desc"),
    now: Long = MaxDate
  )(
    implicit conn: Connection
  ): PagedRecords[(News, Option[Site])] = {
    val records: Seq[(News, Option[Site])] = SQL(
      """
      select * from news
      left join site s on s.site_id = news.site_id
      where release_time <= {now}
      order by """ + orderBy + """
      limit {pageSize} offset {offset}
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> page * pageSize,
      'now -> java.time.Instant.ofEpochMilli(now)
    ).as(
      withSite *
    )

    val count = SQL(
      "select count(*) from news"
    ).as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }

  def createNew(
    siteId: Option[Long], title: String, contents: String, releaseTime: LocalDateTime, updatedTime: LocalDateTime = LocalDateTime.now()
  )(implicit conn: Connection): News = {
    SQL(
      """
      insert into news (news_id, site_id, title, contents, release_time, updated_time) values (
        (select nextval('news_seq')), {siteId}, {title}, {contents}, {releaseTime}, {updatedTime}
      )
      """
    ).on(
      'siteId -> siteId,
      'title -> title,
      'contents -> contents,
      'releaseTime -> releaseTime,
      'updatedTime -> updatedTime
    ).executeUpdate()

    val newsId = SQL("select currval('news_seq')").as(SqlParser.scalar[Long].single)

    News(Some(NewsId(newsId)), siteId, title, contents, releaseTime, updatedTime)
  }

  def update(
    id: NewsId, siteId: Option[Long], title: String, contents: String, releaseTime: LocalDateTime, updatedTime: LocalDateTime = LocalDateTime.now()
  )(implicit conn: Connection): Int =
    SQL(
      """
      update news set
        site_id = {siteId},
        title = {title},
        contents = {contents},
        release_time = {releaseTime},
        updated_time = {updatedTime}
      where news_id = {newsId}
      """
    ).on(
      'newsId -> id.id,
      'siteId -> siteId,
      'title -> title,
      'contents -> contents,
      'releaseTime -> releaseTime,
      'updatedTime -> updatedTime
    ).executeUpdate()

  def delete(newsId: NewsId)(implicit conn: Connection): Int =
    SQL(
      """
      delete from news where news_id = {newsId}
      """
    ).on(
      'newsId -> newsId.id
    ).executeUpdate()
}

