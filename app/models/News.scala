package models

import scala.language.postfixOps
import anorm._
import java.sql.{Connection, Timestamp}
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

case class NewsId(id: Long) extends AnyVal

case class News(
  id: Option[NewsId] = None,
  userId: Option[Long],
  siteId: Option[Long],
  title: String,
  contents: String,
  releaseTime: Instant,
  updatedTime: Instant
)

@Singleton
class NewsRepo @Inject() (
  siteRepo: SiteRepo,
  storeUserRepo: StoreUserRepo
) {
  val MaxDate: Long = java.sql.Date.valueOf("9999-12-31").getTime
  val simple = {
    SqlParser.get[Option[Long]]("news_id") ~
    SqlParser.get[Option[Long]]("store_user_id") ~
    SqlParser.get[Option[Long]]("site_id") ~
    SqlParser.get[String]("title") ~
    SqlParser.get[String]("contents") ~
    SqlParser.get[java.time.Instant]("release_time") ~
    SqlParser.get[java.time.Instant]("updated_time") map {
      case id~userId~siteId~title~contents~releaseTime~updatedTime =>
        News(id.map(NewsId.apply), userId, siteId, title, contents, releaseTime, updatedTime)
    }
  }

  val withSite = simple ~ (SiteRepo.simple ?) map {
    case news~site => (news, site)
  }

  val withSiteUser = simple ~ (SiteRepo.simple ?) ~ (storeUserRepo.simple ?) map {
    case news~site~user => (news, site, user)
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
    now: Long = MaxDate,
    specificUser: Option[Long] = None
  )(
    implicit conn: Connection
  ): PagedRecords[(News, Option[Site], Option[StoreUser])] = {
    val records: Seq[(News, Option[Site], Option[StoreUser])] = SQL(
      """
      select * from news
      left join site s on s.site_id = news.site_id
      left join store_user u on u.store_user_id = news.store_user_id
      where release_time <= {now}
      """ +
        (specificUser.map { uid =>
          "and news.store_user_id = " + uid
        }).getOrElse("") +
      """
      order by """ + orderBy + """
      limit {pageSize} offset {offset}
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> page * pageSize,
      'now -> java.time.Instant.ofEpochMilli(now)
    ).as(
      withSiteUser *
    )

    val count = SQL(
      "select count(*) from news"
    ).as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }

  def createNew(
    userId: Long,
    siteId: Option[Long], title: String, contents: String, releaseTime: Instant, updatedTime: Instant = Instant.now()
  )(implicit conn: Connection): News = {
    SQL(
      """
      insert into news (news_id, store_user_id, site_id, title, contents, release_time, updated_time) values (
        (select nextval('news_seq')), {userId}, {siteId}, {title}, {contents}, {releaseTime}, {updatedTime}
      )
      """
    ).on(
      'userId -> userId,
      'siteId -> siteId,
      'title -> title,
      'contents -> contents,
      'releaseTime -> releaseTime,
      'updatedTime -> updatedTime
    ).executeUpdate()

    val newsId = SQL("select currval('news_seq')").as(SqlParser.scalar[Long].single)

    News(Some(NewsId(newsId)), Some(userId), siteId, title, contents, releaseTime, updatedTime)
  }

  def update(
    id: NewsId, userId: Option[Long], siteId: Option[Long], title: String, contents: String,
    releaseTime: Instant, updatedTime: Instant = Instant.now()
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
      """ +
      (userId.map(uid => "and store_user_id = " + uid).getOrElse(""))
    ).on(
      'newsId -> id.id,
      'siteId -> siteId,
      'title -> title,
      'contents -> contents,
      'releaseTime -> releaseTime,
      'updatedTime -> updatedTime
    ).executeUpdate()

  def delete(newsId: NewsId, userId: Option[Long])(implicit conn: Connection): Int =
    SQL(
      """
      delete from news where news_id = {newsId}
      """ +
      (userId.map(uid => "and store_user_id = " + uid).getOrElse(""))
    ).on(
      'newsId -> newsId.id
    ).executeUpdate()
}

