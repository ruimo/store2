package models

import java.sql.Connection
import play.api.db.Database

case class ChangeItemDescriptionTable(
  itemDescriptions: Seq[ChangeItemDescription]
) {
  def update(itemId: Long)(implicit conn: Connection, itemDescriptionRepo: ItemDescriptionRepo) {
    itemDescriptions.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeItemDescription(
  siteId: Long, localeId: Long, itemDescription: String
) {
  def update(itemId: Long)(implicit conn: Connection, itemDescriptionRepo: ItemDescriptionRepo) {
    itemDescriptionRepo.update(siteId, ItemId(itemId), localeId, itemDescription)
  }

  def add(itemId: Long)(implicit db: Database, itemDescriptionRepo: ItemDescriptionRepo) {
    ExceptionMapper.mapException {
      db.withTransaction { implicit conn =>
        itemDescriptionRepo.add(siteId, ItemId(itemId), localeId, itemDescription)
      }
    }
  }
}
