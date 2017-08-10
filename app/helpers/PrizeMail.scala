package helpers

import play.api.Play
import java.sql.Connection
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.duration._
import play.api.i18n.{Messages, MessagesProvider}
import models.{StoreUser, CreatePrize}
import play.api.libs.mailer._
import javax.inject._
import akka.actor.{ActorSystem}
import scala.concurrent.ExecutionContext
import play.api.Configuration

@Singleton
class PrizeMail @Inject() (
  system: ActorSystem, mailerClient: MailerClient,
  conf: Configuration,
  implicit val ec: ExecutionContext
) extends HasLogger {
  val disableMailer = conf.getOptional[Boolean]("disable.mailer").getOrElse(false)
  val from = conf.get[String]("prize.email.from")
  val to = conf.get[String]("prize.email.to")

  def send(itemName: String, user: StoreUser, prize: CreatePrize)(
    implicit mp: MessagesProvider
  ) {
    if (! disableMailer) {
      sendTo(itemName, user, prize, to, views.html.mail.prizeForAdmin(itemName, user, prize).toString)
      sendTo(itemName, user, prize, user.email, views.html.mail.prize(itemName, user, prize).toString)
    }
    else {
      logger.info("Prize mail is not sent since mailer is disabled.")
    }
  }

  def sendTo(itemName: String, user: StoreUser, prize: CreatePrize, sendTo: String, body: String)(
    implicit mp: MessagesProvider
  ) {
    logger.info("Sending Prize mail to " + sendTo)
    system.scheduler.scheduleOnce(0.microsecond) {
      val mail = Email(
        subject = Messages("mail.prize.subject"),
        to = Seq(sendTo),
        from = from,
        bodyText = Some(body)
      )
      mailerClient.send(mail)
      logger.info("Prize mail sent to " + sendTo)
    }
  }
}
