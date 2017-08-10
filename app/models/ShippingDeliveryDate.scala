package models

import java.sql.Connection
import java.time.Instant

case class ShippingDeliveryDate(
  shippingDate: Instant, deliveryDate: Instant
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

