package models

import java.sql.Connection

case class UpdateCategoryNameTable(
  categoryNames: Seq[UpdateCategoryName]
) {
  def save()(implicit conn: Connection) {
    categoryNames.foreach {
      _.save()
    }
  }
}

case class UpdateCategoryName(
  categoryId: Long, localeId: Long, name: String
)(
  implicit categoryNameRepo: CategoryNameRepo
) {
  def save()(implicit conn: Connection) {
    categoryNameRepo.update(categoryId, localeId, name)
  }
  def create()(implicit conn: Connection) {
    ExceptionMapper.mapException {
      categoryNameRepo.createNew(categoryId, localeId, name)
    }
  }
}
