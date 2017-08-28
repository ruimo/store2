package models

import org.joda.time.DateTime
import java.sql.Connection
import java.time.Instant

case class CreateNews(
  title: String,
  contents: String,
  releaseTime: Instant,
  siteId: Option[Long]
) (
  implicit newsRepo: NewsRepo
) {
  def save(login: LoginSession)(implicit conn: Connection): News = newsRepo.createNew(
    login.userId,
    siteId,
    title,
    contents,
    releaseTime
  )

  def update(id: Long, userId: Option[Long])(implicit conn: Connection): Int = newsRepo.update(
    NewsId(id),
    userId,
    siteId,
    title,
    contents,
    releaseTime = releaseTime
  )
}

