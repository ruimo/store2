package models

import java.sql.Connection

case class ChangeItemCategory(
  categoryId: Long
) (
  implicit itemRepo: ItemRepo
) {
  def update(itemId: Long) {
    itemRepo.changeCategory(ItemId(itemId), categoryId)
  }
}
