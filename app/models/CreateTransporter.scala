package models

import java.sql.Connection

case class CreateTransporter(localeId: Long, transporterName: String)(
  implicit transporterNameRepo: TransporterNameRepo,
  transporterRepo: TransporterRepo
) {
  def save(implicit conn: Connection, localeInfoRepo: LocaleInfoRepo) {
    ExceptionMapper.mapException {
      val trans = transporterRepo.createNew
      transporterNameRepo.createNew(trans.id.get, localeInfoRepo(localeId), transporterName)
    }
  }
}

