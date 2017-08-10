package helpers

import models.{OrderNotification, OrderNotificationRepo, QaEntry}
import play.api.Play
import java.sql.Connection

import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.concurrent.duration._
import play.api.libs.mailer._
import play.api.i18n.{Messages, MessagesProvider}
import javax.inject._

import akka.actor.ActorSystem
import play.api.Configuration
import scala.concurrent.ExecutionContext

@Singleton
class QaMail @Inject() (
  system: ActorSystem, mailerClient: MailerClient,
  orderNotificationRepo: OrderNotificationRepo,
  conf: Configuration,
  implicit val ec: ExecutionContext
) extends HasLogger {
  val disableMailer = conf.getOptional[Boolean]("disable.mailer").getOrElse(false)
  val from = conf.get[String]("user.registration.email.from")

  def send(qa: QaEntry)(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    if (! disableMailer) {
      orderNotificationRepo.listAdmin.foreach { admin =>
        logger.info("Sending QA mail to " + admin.email)
        val body = views.html.mail.qa(admin, qa).toString
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.qa.subject"),
            to = Seq(admin.email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("QA mail sent to " + admin.email)
        }
      }
    }
    else {
      logger.info("QA mail is not sent since mailer is disabled.")
    }
  }
}
