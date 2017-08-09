package models

import java.time.LocalDateTime

case class FeeMaintenance(
  boxId: Long,
  now: LocalDateTime
)
