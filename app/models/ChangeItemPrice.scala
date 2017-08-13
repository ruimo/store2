package models

import java.time.Instant
import java.sql.Connection

case class ChangeItemPriceTable(
  itemPrices: Seq[ChangeItemPrice]
) (
  implicit val itemPriceHistoryRepo: ItemPriceHistoryRepo
) {
  def update(implicit conn: Connection) {
    itemPrices.foreach {
      _.update(itemPriceHistoryRepo)
    }
  }
}

case class ChangeItemPrice(
  siteId: Long, itemPriceId: Long, itemPriceHistoryId: Long, taxId: Long,
  currencyId: Long, unitPrice: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal,
  validUntil: Instant
)(
  implicit val itemPriceHistoryRepo: ItemPriceHistoryRepo
) {
  def update(itemPriceHistoryRepo: ItemPriceHistoryRepo)(implicit conn: Connection) {
    itemPriceHistoryRepo.update(itemPriceHistoryId, taxId, currencyId, unitPrice, listPrice, costPrice, validUntil)
  }

  def add(itemId: Long)(implicit conn: Connection) {
    ExceptionMapper.mapException {
      itemPriceHistoryRepo.add(ItemId(itemId), siteId, taxId, currencyId, unitPrice, listPrice, costPrice, validUntil)
    }
  }
}
