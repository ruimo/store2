package models

import java.sql.Connection

case class CreateSite(localeId: Long, siteName: String) {
  def save()(implicit conn: Connection) {
    Site.createNew(LocaleInfo(localeId), siteName)
  }
}
