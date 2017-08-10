package helpers

import play.api.Play

import scala.collection.immutable
import models._
import java.sql.Connection

import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.concurrent.duration._
import play.api.i18n.{Messages, MessagesProvider}
import play.api.libs.mailer._
import akka.actor._
import javax.inject._

import play.api.libs.mailer._
import scala.concurrent.ExecutionContext
import play.api.Configuration

@Singleton
class ItemInquiryMail @Inject() (
  system: ActorSystem, mailerClient: MailerClient,
  siteItemRepo: SiteItemRepo,
  orderNotificationRepo: OrderNotificationRepo,
  conf: Configuration,
  implicit val ec: ExecutionContext
) extends HasLogger {
  val disableMailer = conf.getOptional[Boolean]("disable.mailer").getOrElse(false)
  val from = conf.get[String]("user.registration.email.from")

  def send(
    user: StoreUser, inq: ItemInquiry, fields: immutable.Map[Symbol, String], locale: LocaleInfo
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val itemInfo: (Site, ItemName) = siteItemRepo.getWithSiteAndItem(inq.siteId, inq.itemId, locale).get

    sendToBuyer(user, locale, itemInfo, inq, fields)
    sendToStoreOwner(user, locale, itemInfo, inq, fields)
    sendToAdmin(user, locale, itemInfo, inq, fields)
  }

  def sendToBuyer(
    user: StoreUser, locale: LocaleInfo, itemInfo: (Site, ItemName), inq: ItemInquiry, fields: immutable.Map[Symbol, String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    logger.info("Sending item inquiry for buyer sent to " + inq.email)
    val body = inq.inquiryType match {
      case ItemInquiryType.QUERY =>
        views.html.mail.itemInquiryForBuyer(user, itemInfo, inq, fields).toString
      case ItemInquiryType.RESERVATION => 
        views.html.mail.itemReservationForBuyer(user, itemInfo, inq, fields).toString
      case t =>
        throw new Error("Unknown inquiry type " + t)
    }
    if (! disableMailer) {
      system.scheduler.scheduleOnce(0.microsecond) {
        val mail = Email(
          subject = Messages(
            inq.inquiryType match {
              case ItemInquiryType.QUERY => "mail.item.inquiry.buyer.subject"
              case ItemInquiryType.RESERVATION => "mail.item.reservation.buyer.subject"
            }
          ).format(inq.id.get.id),
          to = Seq(inq.email),
          from = from,
          bodyText = Some(body)
        )
        mailerClient.send(mail)
        logger.info("Item inquiry notification for buyer sent to " + inq.email)
      }
    }
  }

  def sendToStoreOwner(
    user: StoreUser, locale: LocaleInfo, itemInfo: (Site, ItemName), inq: ItemInquiry, fields: immutable.Map[Symbol, String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    orderNotificationRepo.listBySite(inq.siteId).foreach { owner =>
      logger.info("Sending item inquiry to site owner " + itemInfo._1 + " sent to " + inq.email)
      val body = inq.inquiryType match {
        case ItemInquiryType.QUERY =>
          views.html.mail.itemInquiryForSiteOwner(user, itemInfo, inq, fields).toString
        case ItemInquiryType.RESERVATION => 
          views.html.mail.itemReservationForSiteOwner(user, itemInfo, inq, fields).toString
        case t =>
          throw new Error("Unknown inquiry type " + t)
      }
      if (! disableMailer) {
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages(
              inq.inquiryType match {
                case ItemInquiryType.QUERY => "mail.item.inquiry.site.owner.subject"
                case ItemInquiryType.RESERVATION => "mail.item.reservation.site.owner.subject"
              }
            ).format(inq.id.get.id),
            to = Seq(owner.email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Item inquiry notification for site owner " + itemInfo._1 + " sent to " + inq.email)
        }
      }
    }
  }

  def sendToAdmin(
    user: StoreUser, locale: LocaleInfo, itemInfo: (Site, ItemName), inq: ItemInquiry, fields: immutable.Map[Symbol, String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    if (! disableMailer) {
      orderNotificationRepo.listAdmin.foreach { admin =>
        logger.info("Sending item inquiry for admin to " + admin.email)
        val body = inq.inquiryType match {
          case ItemInquiryType.QUERY =>
            views.html.mail.itemInquiryForAdmin(user, itemInfo, inq, fields).toString
          case ItemInquiryType.RESERVATION =>
            views.html.mail.itemReservationForAdmin(user, itemInfo, inq, fields).toString
        case t =>
          throw new Error("Unknown inquiry type " + t)
        }
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages(
              inq.inquiryType match {
                case ItemInquiryType.QUERY => "mail.item.inquiry.site.owner.subject"
                case ItemInquiryType.RESERVATION => "mail.item.reservation.site.owner.subject"
              }
            ).format(inq.id.get.id),
            to = Seq(admin.email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Item inquiry notification for admin to " + admin.email)
        }
      }
    }
    else {
      logger.info("Item inquiry notification mail is not sent since mailer is disabled.")
    }
  }
}
