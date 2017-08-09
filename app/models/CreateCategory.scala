package models

import java.sql.Connection

case class CreateCategory(localeId: Long, categoryName: String, parent: Option[Long])(
  implicit localeInfoRepo: LocaleInfoRepo,
  categoryRepo: CategoryRepo
) {
  def save()(implicit conn: Connection) {
    parent match {
      case Some(p) => categoryRepo.createNew(categoryRepo.get(p), Map(localeInfoRepo(localeId) -> categoryName)) 
      case _       => categoryRepo.createNew(Map(localeInfoRepo(localeId) -> categoryName)) 
    }
  }
}

