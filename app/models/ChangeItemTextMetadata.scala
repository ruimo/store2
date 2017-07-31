package models

import java.sql.Connection

import play.api.db.Database

case class ChangeItemTextMetadataTable(
  itemTextMetadatas: Seq[ChangeItemTextMetadata]
) {
  def update(itemId: Long)(implicit conn: Connection) {
    itemTextMetadatas.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeItemTextMetadata(
  metadataType: Int, metadata: String
) {
  def update(itemId: Long)(implicit conn: Connection) {
    ItemTextMetadata.update(ItemId(itemId), ItemTextMetadataType.byIndex(metadataType), metadata)
  }

  def add(itemId: Long)(implicit db: Database) {
    ExceptionMapper.mapException {
      db.withTransaction { implicit conn =>
        ItemTextMetadata.add(ItemId(itemId), ItemTextMetadataType.byIndex(metadataType), metadata)
      }
    }
  }
}
