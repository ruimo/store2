package models

import java.sql.Connection

case class UpdateCategoryCode(categoryCode: String)(
  implicit categoryRepo: CategoryRepo
) {
  def save(categoryId: Long)(implicit conn: Connection) {
    ExceptionMapper.mapException {
      categoryRepo.updateCategoryCode(categoryId, categoryCode)
    }
  }
}
