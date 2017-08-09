package models

import java.sql.Connection

case class ChangeSite(siteId: Long, localeId: Long, siteName: String) {
  def update()(implicit conn: Connection, localeInfoRepo: LocaleInfoRepo, siteRepo: SiteRepo) {
    siteRepo.update(siteId, localeInfoRepo(localeId), siteName)
  }
}

object ChangeSite {
  def apply(site: Site): ChangeSite = ChangeSite(site.id.get, site.localeId, site.name)
}
