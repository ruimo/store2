package models

import java.time.Instant

case class FeeMaintenance(
  boxId: Long,
  now: Instant
)
