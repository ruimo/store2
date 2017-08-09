package models

import java.sql.Connection

case class ChangeSupplementalCategory(categoryId: Long) {
  def add(itemId: Long)(implicit conn: Connection, supplementalCategoryRepo: SupplementalCategoryRepo) {
    ExceptionMapper.mapException {
      supplementalCategoryRepo.createNew(ItemId(itemId), categoryId)
    }
  }

  def remove(itemId: Long)(implicit conn: Connection, supplementalCategoryRepo: SupplementalCategoryRepo) {
    supplementalCategoryRepo.remove(ItemId(itemId), categoryId)
  }
}
