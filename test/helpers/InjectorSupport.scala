package helpers

import play.api.Application
import scala.reflect.ClassTag
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._

trait InjectorSupport {
  def appl(conf: Map[String, Any] = inMemoryDatabase()): Application = GuiceApplicationBuilder().configure(conf).build()

  def inject[T](implicit app: Application, c: ClassTag[T]): T = app.injector.instanceOf[T]
}
