package helpers

import java.text.SimpleDateFormat
import play.api.data.Mapping
import java.time.{Instant, LocalDateTime, ZonedDateTime}
import play.api.data.format.{Formatter => PlayFormatter}
import play.api.data.format.Formats
import play.api.data.{Forms => PlayForms}

object Forms {
  def instantFormat(pattern: String, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): PlayFormatter[java.time.Instant] = new PlayFormatter[java.time.Instant] {
    def instantParse(data: String) = {
      try {
        Instant.ofEpochMilli(new SimpleDateFormat(pattern).parse(data).getTime)
      }
      catch {
        case t: Throwable =>
          t.printStackTrace
          throw t
      }
    }

    override val format = Some(("format.localDateTime", Seq(pattern)))

    def bind(key: String, data: Map[String, String]) = Formats.parsing(instantParse, "error.localDateTime", Nil)(key, data)

    def unbind(key: String, value: Instant) = Map(key -> new SimpleDateFormat(pattern).format(new java.util.Date(value.toEpochMilli)))
  }

  /**
   * Default formatter for `java.time.LocalDateTime` type with pattern `yyyy-MM-dd`.
   */
  implicit val instantFormat: PlayFormatter[java.time.Instant] = instantFormat("yyyy-MM-dd HH:mm:ss")

  val instant: Mapping[java.time.Instant] = PlayForms.of[java.time.Instant]
  def instant(pattern: String): Mapping[java.time.Instant] = PlayForms.of[java.time.Instant] as instantFormat(pattern)
}
