package models

import org.joda.time.DateTime
import java.sql.Connection
import java.time.Instant

case class ChangeSiteItemMetadataTable(
  siteItemMetadata: Seq[ChangeSiteItemMetadata]
) {
  def update(itemId: Long)(implicit conn: Connection) {
    siteItemMetadata.foreach {
      _.update(itemId)
    }
  }
}

case class ChangeSiteItemMetadata(
  id: Long, siteId: Long, metadataType: Int, metadata: Long, validUntil: Instant
)(
  implicit siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo
) {
  def update(itemId: Long)(implicit conn: Connection) {
    siteItemNumericMetadataRepo.update(id, metadata, validUntil)
  }
}
