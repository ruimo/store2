package models

import java.sql.Connection

case class CreateItem(
  localeId: Long, siteId: Long, categoryId: Long, itemName: String, taxId: Long, 
  currencyId: Long, price: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal, description: String,
  isCoupon: Boolean
)(
  implicit itemRepo: ItemRepo,
  siteRepo: SiteRepo
) {
  def save(hide: Boolean)(implicit conn: Connection) {
    itemRepo.createItem(this, hide)
  }

  def site(implicit conn: Connection) = siteRepo(siteId)
}
