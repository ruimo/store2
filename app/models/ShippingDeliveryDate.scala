package models

import java.sql.Connection
import java.time.LocalDateTime

case class ShippingDeliveryDate(
  shippingDate: LocalDateTime, deliveryDate: LocalDateTime
) {
  def save(
    siteUser: Option[SiteUser], transactionSiteId: Long
  )(
    implicit conn: Connection
  ) {
    TransactionShipStatus.updateShippingDeliveryDate(siteUser, transactionSiteId, shippingDate, deliveryDate)
    TransactionShipStatus.update(siteUser, transactionSiteId, TransactionStatus.CONFIRMED)
  }
}

