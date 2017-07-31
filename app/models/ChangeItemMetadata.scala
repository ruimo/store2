package models

import java.sql.Connection

import play.api.db.Database

case class ChangeItemMetadataTable(
  itemMetadatas: Seq[ChangeItemMetadata]
) {
  def update(itemId: Long)(implicit conn: Connection) {
    itemMetadatas.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeItemMetadata(
  metadataType: Int, metadata: Long
) {
  def update(itemId: Long)(implicit conn: Connection) {
    ItemNumericMetadata.update(ItemId(itemId), ItemNumericMetadataType.byIndex(metadataType), metadata)
  }

  def add(itemId: Long)(implicit db: Database) {
    ExceptionMapper.mapException {
      db.withTransaction { implicit conn =>
        ItemNumericMetadata.add(ItemId(itemId), ItemNumericMetadataType.byIndex(metadataType), metadata)
      }
    }
  }
}
