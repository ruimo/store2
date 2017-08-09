package models

import java.sql.Connection

case class CreateShippingBox(
  siteId: Long,
  itemClass: Long,
  boxSize: Int,
  boxName: String
) {
  def save(implicit shippingBoxRepo: ShippingBoxRepo, conn: Connection) {
    ExceptionMapper.mapException {
      shippingBoxRepo.createNew(siteId, itemClass, boxSize, boxName)
    }
  }
}
