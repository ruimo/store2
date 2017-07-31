package models

import java.sql.Connection
import play.api.db.Database

case class ChangeItemDescriptionTable(
  itemDescriptions: Seq[ChangeItemDescription]
) {
  def update(itemId: Long)(implicit conn: Connection) {
    itemDescriptions.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeItemDescription(
  siteId: Long, localeId: Long, itemDescription: String
) {
  def update(itemId: Long)(implicit conn: Connection) {
    ItemDescription.update(siteId, ItemId(itemId), localeId, itemDescription)
  }

  def add(itemId: Long)(implicit db: Database) {
    ExceptionMapper.mapException {
      db.withTransaction { implicit conn =>
        ItemDescription.add(siteId, ItemId(itemId), localeId, itemDescription)
      }
    }
  }
}
