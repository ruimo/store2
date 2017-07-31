package models

import java.sql.Connection

case class CreateTransporter(localeId: Long, transporterName: String) {
  def save(implicit conn: Connection) {
    ExceptionMapper.mapException {
      val trans = Transporter.createNew
      TransporterName.createNew(trans.id.get, LocaleInfo(localeId), transporterName)
    }
  }
}

