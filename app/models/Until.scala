package models

import java.time.LocalDateTime

object Until {
  val EverStr: String = "9999-12-31 23:59:59"
  val Ever: Long = java.sql.Timestamp.valueOf(EverStr).getTime
  val EverLocalDateTime: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59)
}
