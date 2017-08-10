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
  def save()(implicit conn: Connection): News = newsRepo.createNew(
    siteId,
    title,
    contents,
    releaseTime
  )

  def update(id: Long)(implicit conn: Connection): Int = newsRepo.update(
    NewsId(id),
    siteId,
    title,
    contents,
    releaseTime = releaseTime
  )
}

