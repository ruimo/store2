package models

import java.sql.Connection

case class ChangeSiteItemTable(
  sites: Seq[ChangeSiteItem]
)

case class ChangeSiteItem(
  siteId: Long
) {
  def add(itemId: Long)(implicit conn: Connection, siteItemRepo: SiteItemRepo, itemPriceRepo: ItemPriceRepo) {
    ExceptionMapper.mapException {
      siteItemRepo.add(ItemId(itemId), siteId)
      itemPriceRepo.add(ItemId(itemId), siteId)
    }
  }
}
