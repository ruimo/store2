package models

import java.sql.Connection

case class ChangeTransactionStatus(transactionSiteId: Long, status: Int) {
  def save(siteUser: Option[SiteUser])(implicit conn: Connection) {
    TransactionShipStatus.update(siteUser, transactionSiteId, TransactionStatus.byIndex(status))
  }
}
