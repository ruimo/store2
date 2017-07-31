package models

import java.sql.Connection

case class ChangeShippingBox(
  id: Long,
  siteId: Long,
  itemClass: Long,
  boxSize: Int,
  boxName: String
) {
  def save(implicit conn: Connection) {
    ExceptionMapper.mapException {
      ShippingBox.update(id, siteId, itemClass, boxSize, boxName)
    }
  }
}
