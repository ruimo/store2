package models

import java.sql.Connection

case class ChangeShippingBox(
  id: Long,
  siteId: Long,
  itemClass: Long,
  boxSize: Int,
  boxName: String
) {
  def save(implicit shippingBoxRepo: ShippingBoxRepo, conn: Connection) {
    ExceptionMapper.mapException {
      shippingBoxRepo.update(id, siteId, itemClass, boxSize, boxName)
    }
  }
}
