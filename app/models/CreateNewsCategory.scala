package models

import org.joda.time.DateTime
import java.sql.Connection
import java.time.Instant

case class CreateNewsCategory(
  categoryName: String,
  iconUrl: String
) (
  implicit newsCategoryRepo: NewsCategoryRepo
) {
  def save()(implicit conn: Connection): NewsCategory = newsCategoryRepo.createNew(
    categoryName,
    iconUrl
  )

  def update(id: Long)(implicit conn: Connection): Int = newsCategoryRepo.update(
    NewsCategoryId(id),
    categoryName,
    iconUrl
  )
}

