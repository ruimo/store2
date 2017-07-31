package models

import java.sql.Connection

case class ChangeItemCategory(
  categoryId: Long
) {
  def update(itemId: Long, itemRepo: ItemRepo) {
    itemRepo.changeCategory(ItemId(itemId), categoryId)
  }
}
