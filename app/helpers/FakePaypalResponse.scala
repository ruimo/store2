package helpers

import javax.inject.{Inject, Singleton}

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.ws.{WSCookie, WSResponse}
import play.api.libs.json._

import scala.xml.Elem

@Singleton
class FakePaypalResponseRepo @Inject() (
  cache: Cache
) {
  val FakePaypalResponsBody: () => String = cache.config(
    _.getOptional[String]("fakePaypalRespons.body").getOrElse(
      throw new IllegalStateException("Specify fakePaypalRespons.body in configuration.")
    )
  )
  val FakePaypalResponsStatusCode: () => Int = cache.config(
    _.getOptional[Int]("fakePaypalRespons.statusCode").getOrElse(
      throw new IllegalStateException("Specify fakePaypalRespons.statusCode in configuration.")
    )
  )

  def apply(): FakePaypalResponse = FakePaypalResponse(
    FakePaypalResponsBody(),
    FakePaypalResponsStatusCode()
  )
}

case class FakePaypalResponse(
  body: String,
  status: Int
) extends WSResponse {
  def allHeaders: Map[String, Seq[String]] = Map()
  def bodyAsBytes: Array[Byte] = body.getBytes
  def cookie(name: String): Option[WSCookie] = None
  def cookies: Seq[WSCookie] = Seq()
  override def header(key: String): Option[String] = None
  def json: JsValue = JsNull
  def statusText: String = ""
  def underlying[T] = (new AnyRef).asInstanceOf[T]
  def xml: Elem = <resp/>

  override def headers: Map[String, Seq[String]] = allHeaders
  override def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(body))
}

