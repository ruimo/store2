package helpers

import play.api.data.Mapping
import java.time.{Instant, LocalDateTime}
import play.api.data.format.{Formatter => PlayFormatter}
import play.api.data.format.Formats
import play.api.data.{Forms => PlayForms}

object Forms {
  def instantFormat(pattern: String, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): PlayFormatter[java.time.Instant] = new PlayFormatter[java.time.Instant] {

    val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(zoneId)
    def instantParse(data: String) = LocalDateTime.parse(data, formatter).atZone(zoneId).toInstant()

    override val format = Some(("format.localDateTime", Seq(pattern)))

    def bind(key: String, data: Map[String, String]) = Formats.parsing(instantParse, "error.localDateTime", Nil)(key, data)

    def unbind(key: String, value: Instant) = Map(key -> formatter.format(value))
  }

  /**
   * Default formatter for `java.time.LocalDateTime` type with pattern `yyyy-MM-dd`.
   */
  implicit val instantFormat: PlayFormatter[java.time.Instant] = instantFormat("yyyy-MM-dd HH:mm:ss")

  val instant: Mapping[java.time.Instant] = PlayForms.of[java.time.Instant]
  def instant(pattern: String): Mapping[java.time.Instant] = PlayForms.of[java.time.Instant] as instantFormat(pattern)
}
