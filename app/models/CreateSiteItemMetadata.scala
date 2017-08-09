package models

import org.joda.time.DateTime
import java.sql.Connection
import java.time.LocalDateTime

case class CreateSiteItemMetadata(
  siteId: Long, metadataType: Int, metadata: Long, validUntil: LocalDateTime
) (
  implicit siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo
) {
  def add(itemId: Long)(implicit conn: Connection) {
    ExceptionMapper.mapException {
      siteItemNumericMetadataRepo.createNew(
        siteId, ItemId(itemId), SiteItemNumericMetadataType.byIndex(metadataType), metadata, validUntil
      )
    }
  }
}
