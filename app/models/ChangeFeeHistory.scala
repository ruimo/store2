package models

import org.joda.time.DateTime
import java.sql.Connection
import java.time.Instant

case class ChangeFeeHistoryTable(
  histories: Seq[ChangeFeeHistory]
) {
  def update(feeId: Long)(implicit shippingFeeHistoryRepo: ShippingFeeHistoryRepo, conn: Connection) {
    histories.foreach {
      _.update()
    }
  }
}

case class ChangeFeeHistory(
  historyId: Long, taxId: Long, fee: BigDecimal, costFee: Option[BigDecimal], validUntil: Instant
) {
  def update()(implicit shippingFeeHistoryRepo: ShippingFeeHistoryRepo, conn: Connection) {
    shippingFeeHistoryRepo.update(historyId, taxId, fee, costFee, validUntil)
  }

  def add(feeId: Long)(implicit shippingFeeHistoryRepo: ShippingFeeHistoryRepo, conn: Connection) {
    ExceptionMapper.mapException {
      shippingFeeHistoryRepo.createNew(
        feeId, taxId, fee, costFee, validUntil
      )
    }
  }
}
