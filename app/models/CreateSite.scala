package models

import java.sql.Connection

case class CreateSite(localeId: Long, siteName: String) {
  def save()(
    implicit conn: Connection,
    siteRepo: SiteRepo,
    localeInfoRepo: LocaleInfoRepo
  ) {
    siteRepo.createNew(localeInfoRepo(localeId), siteName)
  }
}
