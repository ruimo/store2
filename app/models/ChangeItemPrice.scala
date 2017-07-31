package models

import org.joda.time.DateTime
import java.sql.Connection

case class ChangeItemPriceTable(
  itemPrices: Seq[ChangeItemPrice]
) {
  def update()(itemPriceHistoryRepo: ItemPriceHistoryRepo) {
    itemPrices.foreach {
      _.update(itemPriceHistoryRepo)
    }
  }
}

case class ChangeItemPrice(
  siteId: Long, itemPriceId: Long, itemPriceHistoryId: Long, taxId: Long,
  currencyId: Long, unitPrice: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal,
  validUntil: DateTime
) {
  def update(itemPriceHistoryRepo: ItemPriceHistoryRepo) {
    itemPriceHistoryRepo.update(itemPriceHistoryId, taxId, currencyId, unitPrice, listPrice, costPrice, validUntil)
  }

  def add(itemId: Long, itemPriceHistoryRepo: ItemPriceHistoryRepo) {
    ExceptionMapper.mapException {
      itemPriceHistoryRepo.add(ItemId(itemId), siteId, taxId, currencyId, unitPrice, listPrice, costPrice, validUntil)
    }
  }
}
