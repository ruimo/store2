package helpers

import models.{OrderNotification, OrderNotificationRepo, UserRegistration}
import play.api.{Configuration, Play}
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import scala.concurrent.duration._
import play.api.libs.mailer._
import play.api.i18n.{Messages, MessagesProvider}
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext

@Singleton
class UserEntryMail @Inject() (
  system: ActorSystem, mailerClient: MailerClient, conf: Configuration,
  orderNotificationRepo: OrderNotificationRepo, implicit val ec: ExecutionContext
) extends HasLogger {
  val disableMailer = conf.getOptional[Boolean]("disable.mailer").getOrElse(false)
  val from = conf.get[String]("user.registration.email.from")

  def sendUserRegistration(ur: UserRegistration)(implicit conn: Connection, mp: MessagesProvider) {
    if (! disableMailer) {
      orderNotificationRepo.listAdmin.foreach { admin =>
        logger.info("Sending user registration mail to " + admin.email)
        val body = views.html.mail.userRegistration(admin, ur).toString
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.user.registration.subject"),
            to = Seq(admin.email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("User registration mail sent to " + admin.email)
        }
      }
    }
    else {
      logger.info("User registration mail is not sent since mailer is disabled.")
    }
  }
}
