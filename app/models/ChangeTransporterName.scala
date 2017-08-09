package models

import java.sql.Connection

case class ChangeTransporterNameTable(
  transporterNames: Seq[ChangeTransporterName]
) {
  def update(id: Long)(implicit conn: Connection) {
    transporterNames.foreach {
      _.update(id)
    }
  }
}

case class ChangeTransporterName(
  localeId: Long, transporterName: String
)(
  implicit transporterNameRepo: TransporterNameRepo
) {
  def update(id: Long)(implicit conn: Connection) {
    transporterNameRepo.update(id, localeId, transporterName)
  }

  def add(id: Long)(implicit conn: Connection) {
    ExceptionMapper.mapException {
      transporterNameRepo.add(id, localeId, transporterName)
    }
  }
}
