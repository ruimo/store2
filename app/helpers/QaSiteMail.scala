package helpers

import models._
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
class QaSiteMail @Inject() (
  system: ActorSystem, mailerClient: MailerClient,
  orderNotificationRepo: OrderNotificationRepo,
  conf: Configuration,
  implicit val ec: ExecutionContext
) extends HasLogger {
  val disableMailer = conf.getOptional[Boolean]("disable.mailer").getOrElse(false)
  val from = conf.get[String]("user.registration.email.from")

  def send(qa: CreateQaSite, user: StoreUser, site: Site)(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    if (! disableMailer) {
      orderNotificationRepo.listAdmin.foreach { admin =>
        sendTo(qa, site, admin.email, views.html.mail.qaSiteForAdmin(qa, site).toString)
      }
      orderNotificationRepo.listBySite(site.id.get).foreach { owner =>
        sendTo(qa, site, owner.email, views.html.mail.qaSiteForStoreOwner(qa, site).toString)
      }
      sendTo(qa, site, qa.email, views.html.mail.qaSiteForUser(qa, site).toString)
    }
    else {
      logger.info("QA site mail is not sent since mailer is disabled.")
    }
  }

  def sendTo(qa: CreateQaSite, site: Site, email: String, body: String)(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    logger.info("Sending QA site mail to " + email)
    system.scheduler.scheduleOnce(0.microsecond) {
      val mail = Email(
        subject = Messages("mail.qa.site.subject"),
        to = Seq(email),
        from = from,
        bodyText = Some(body)
      )
      mailerClient.send(mail)
      logger.info("QA site mail sent to " + email)
    }
  }
}
