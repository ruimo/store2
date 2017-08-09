package models

import java.sql.Connection

import play.api.db.Database

case class ChangeItemNameTable(
  itemNames: Seq[ChangeItemName]
) {
  def update(itemId: Long)(implicit conn: Connection, itemNameRepo: ItemNameRepo) {
    itemNames.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeItemName(
  localeId: Long, itemName: String
) {
  def update(itemId: Long)(implicit conn: Connection, itemNameRepo: ItemNameRepo) {
    itemNameRepo.update(ItemId(itemId), localeId, itemName)
  }

  def add(itemId: Long)(implicit db: Database, itemNameRepo: ItemNameRepo) {
    ExceptionMapper.mapException {
      db.withTransaction { implicit conn =>
        itemNameRepo.add(ItemId(itemId), localeId, itemName)
      }
    }
  }
}
