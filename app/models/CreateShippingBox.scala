package models

import java.sql.Connection

case class CreateShippingBox(
  siteId: Long,
  itemClass: Long,
  boxSize: Int,
  boxName: String
) {
  def save(implicit conn: Connection) {
    ExceptionMapper.mapException {
      ShippingBox.createNew(siteId, itemClass, boxSize, boxName)
    }
  }
}
