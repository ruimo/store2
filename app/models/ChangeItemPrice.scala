package models

import java.time.LocalDateTime

case class ChangeItemPriceTable(
  itemPrices: Seq[ChangeItemPrice]
) (
  implicit val itemPriceHistoryRepo: ItemPriceHistoryRepo
) {
  def update() {
    itemPrices.foreach {
      _.update(itemPriceHistoryRepo)
    }
  }
}

case class ChangeItemPrice(
  siteId: Long, itemPriceId: Long, itemPriceHistoryId: Long, taxId: Long,
  currencyId: Long, unitPrice: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal,
  validUntil: LocalDateTime
)(
  implicit val itemPriceHistoryRepo: ItemPriceHistoryRepo
) {
  def update(itemPriceHistoryRepo: ItemPriceHistoryRepo) {
    itemPriceHistoryRepo.update(itemPriceHistoryId, taxId, currencyId, unitPrice, listPrice, costPrice, validUntil)
  }

  def add(itemId: Long) {
    ExceptionMapper.mapException {
      itemPriceHistoryRepo.add(ItemId(itemId), siteId, taxId, currencyId, unitPrice, listPrice, costPrice, validUntil)
    }
  }
}
