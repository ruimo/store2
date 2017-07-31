package helpers

import play.api.Logger

trait HasLogger {
  val logger = Logger(getClass)
}
