package models

import java.sql.Connection

case class UpdateCategoryCode(categoryCode: String) {
  def save(categoryId: Long)(implicit conn: Connection) {
    ExceptionMapper.mapException {
      Category.updateCategoryCode(categoryId, categoryCode)
    }
  }
}
