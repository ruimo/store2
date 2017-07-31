package models

import java.sql.Connection

case class CreateItem(
  localeId: Long, siteId: Long, categoryId: Long, itemName: String, taxId: Long, 
  currency: CurrencyInfo, price: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal, description: String,
  isCoupon: Boolean
) {
  def save(hide: Boolean, itemRepo: ItemRepo) {
    itemRepo.createItem(this, hide)
  }

  def site(implicit conn: Connection) = Site(siteId)
}
