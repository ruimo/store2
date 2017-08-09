package helpers

import models._
import play.api.i18n.{Messages, MessagesProvider}
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._
import play.api.Play.current
import play.api.i18n.Messages
import models.PersistedTransaction
import java.sql.Connection

import play.api.{Configuration, Play}

import collection.immutable
import javax.inject._

import play.api.libs.mailer._
import akka.actor.ActorSystem

@Singleton
class NotificationMail @Inject() (
  storeUserRepo: StoreUserRepo,
  orderNotificationRepo: OrderNotificationRepo,
  system: ActorSystem, mailerClient: MailerClient,
  conf: Configuration,
  implicit val siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo
) extends HasLogger {
  val disableMailer = conf.getOptional[Boolean]("disable.mailer").getOrElse(false)
  val from = conf.get[String]("order.email.from")

  def orderCompleted(
    login: LoginSession, tran: PersistedTransaction, addr: Option[Address]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]] = retrieveMetadata(tran)

    val supplementalEmails = SupplementalUserEmail.load(login.storeUser.id.get).map(_.email)
    sendToBuyer(login, tran, addr, metadata, supplementalEmails)
    sendToStoreOwner(login, tran, addr, metadata)
    sendToAdmin(login, tran, addr, metadata)
  }

  def shipPrepared(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]] = retrieveMetadata(tran)

    sendShippingPreparedNotificationToBuyer(
      login, siteId, tran, addr, metadata, status, transporters
    )
    sendShippingPreparedNotificationToStoreOwner(login, siteId, tran, addr, metadata, status, transporters)
    sendShippingPreparedNotificationToAdmin(login, siteId, tran, addr, metadata, status, transporters)
  }

  def shipCompleted(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]] = retrieveMetadata(tran)

    sendShippingNotificationToBuyer(
      login, siteId, tran, addr, metadata, status, transporters
    )
    sendShippingNotificationToStoreOwner(login, siteId, tran, addr, metadata, status, transporters)
    sendShippingNotificationToAdmin(login, siteId, tran, addr, metadata, status, transporters)
  }

  def shipCanceled(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]] = retrieveMetadata(tran)

    sendCancelNotificationToBuyer(
      login, siteId, tran, addr, metadata, status, transporters
    )
    sendCancelNotificationToStoreOwner(login, siteId, tran, addr, metadata, status, transporters)
    sendCancelNotificationToAdmin(login, siteId, tran, addr, metadata, status, transporters)
  }

  def retrieveMetadata(
    tran: PersistedTransaction
  )(
    implicit conn: Connection, mp: MessagesProvider
  ): Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]] = {
    val buf = scala.collection.mutable.HashMap[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]]()
    tran.itemTable.foreach {
      e =>
        val siteId = e._1
        val items = e._2
        items.foreach {
          it =>
            val tranItem = it._2
            val itemId = tranItem.itemId
            buf.update(siteId -> itemId, siteItemNumericMetadataRepo.all(siteId, ItemId(tranItem.itemId)))
        }
    }
    val metadata = buf.toMap
    metadata
  }

  def sendToBuyer(
    login: LoginSession, tran: PersistedTransaction, addr: Option[Address],
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    supplementalEmails: immutable.Set[String]
  )(
    implicit mp: MessagesProvider
  ) {
    val primaryEmail = addr.map(_.email).filter(!_.isEmpty).getOrElse(login.storeUser.email)

    (supplementalEmails + primaryEmail).foreach { email =>
      logger.info("Sending confirmation for buyer sent to " + email)
      val body = views.html.mail.forBuyer(login, tran, addr, metadata).toString
      if (! disableMailer) {
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.buyer.subject").format(tran.header.id.get),
            to = Seq(email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Ordering confirmation for buyer sent to " + email)
        }
      }
    }
  }

  def sendShippingPreparedNotificationToBuyer(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    val primaryEmail = if (addr.email.isEmpty) buyer.email else addr.email
    val supplementalEmails = SupplementalUserEmail.load(tran.header.userId).map(_.email)

    (supplementalEmails + primaryEmail).foreach { email =>
      logger.info("Sending shipping prepared notification for buyer sent to " + email)
      val body = views.html.mail.shippingPreparedNotificationForBuyer(
        login, siteId, tran, addr, metadata, buyer, status, transporters
      ).toString

      if (! disableMailer) {
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.shipping.prepared.buyer.subject").format(tran.header.id.get),
            to = Seq(email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Shipping prepared notification for buyer sent to " + email)
        }
      }
    }
  }

  def sendShippingNotificationToBuyer(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    val primaryEmail = if (addr.email.isEmpty) buyer.email else addr.email
    val supplementalEmails = SupplementalUserEmail.load(tran.header.userId).map(_.email)

    (supplementalEmails + primaryEmail).foreach { email =>
      logger.info("Sending shipping notification for buyer sent to " + email)
      val body = views.html.mail.shippingNotificationForBuyer(
        login, siteId, tran, addr, metadata, buyer, status, transporters
      ).toString

      if (! disableMailer) {
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.shipping.buyer.subject").format(tran.header.id.get),
            to = Seq(email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Shipping notification for buyer sent to " + email)
        }
      }
    }
  }

  def sendCancelNotificationToBuyer(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    val primaryEmail = if (addr.email.isEmpty) buyer.email else addr.email
    val supplementalEmails = SupplementalUserEmail.load(tran.header.userId).map(_.email)

    (supplementalEmails + primaryEmail).foreach { email =>
      logger.info("Sending cancel notification for buyer sent to " + email)
      val body = views.html.mail.cancelNotificationForBuyer(
        login, siteId, tran, addr, metadata, buyer, status, transporters
      ).toString

      if (! disableMailer) {
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.cancel.buyer.subject").format(tran.header.id.get),
            to = Seq(email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Shipping cancel notification for buyer sent to " + email)
        }
      }
    }
  }

  def sendToStoreOwner(
    login: LoginSession, tran: PersistedTransaction, addr: Option[Address],
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    tran.siteTable.foreach { site =>
      orderNotificationRepo.listBySite(site.id.get).foreach { owner =>
        logger.info("Sending ordering confirmation for site owner " + site + " sent to " + owner.email)
        val body = views.html.mail.forSiteOwner(login, site, owner, tran, addr, metadata).toString
        if (! disableMailer) {
          system.scheduler.scheduleOnce(0.microsecond) {
            val mail = Email(
              subject = Messages("mail.site.owner.subject").format(tran.header.id.get),
              to = Seq(owner.email),
              from = from,
              bodyText = Some(body)
            )
            mailerClient.send(mail)
            logger.info("Ordering confirmation for site owner " + site + " sent to " + owner.email)
          }
        }
      }
    }
  }

  def sendShippingPreparedNotificationToStoreOwner(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    tran.siteTable.foreach { site =>
      if (site.id.get == siteId) {
        orderNotificationRepo.listBySite(site.id.get).foreach { owner =>
          logger.info("Sending shipping prepared confirmation for site owner " + site + " sent to " + owner.email)
          val body = views.html.mail.shippingPreparedNotificationForSiteOwner(
            login, site, owner, tran, addr, metadata, buyer, status, transporters
          ).toString
          if (! disableMailer) {
            system.scheduler.scheduleOnce(0.microsecond) {
              val mail = Email(
                subject = Messages("mail.shipping.prepared.site.owner.subject").format(tran.header.id.get),
                to = Seq(owner.email),
                from = from,
                bodyText = Some(body)
              )
              mailerClient.send(mail)
              logger.info("Shipping prepared confirmation for site owner " + site + " sent to " + owner.email)
            }
          }
        }
      }
    }
  }

  def sendShippingNotificationToStoreOwner(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    tran.siteTable.foreach { site =>
      if (site.id.get == siteId) {
        orderNotificationRepo.listBySite(site.id.get).foreach { owner =>
          logger.info("Sending shipping confirmation for site owner " + site + " sent to " + owner.email)
          val body = views.html.mail.shippingNotificationForSiteOwner(
            login, site, owner, tran, addr, metadata, buyer, status, transporters
          ).toString
          if (! disableMailer) {
            system.scheduler.scheduleOnce(0.microsecond) {
              val mail = Email(
                subject = Messages("mail.shipping.site.owner.subject").format(tran.header.id.get),
                to = Seq(owner.email),
                from = from,
                bodyText = Some(body)
              )
              mailerClient.send(mail)
              logger.info("Shipping confirmation for site owner " + site + " sent to " + owner.email)
            }
          }
        }
      }
    }
  }

  def sendCancelNotificationToStoreOwner(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    tran.siteTable.foreach { site =>
      if (site.id.get == siteId) {
        orderNotificationRepo.listBySite(site.id.get).foreach { owner =>
          logger.info("Sending cancel confirmation for site owner " + site + " sent to " + owner.email)
          val body = views.html.mail.cancelNotificationForSiteOwner(
            login, site, owner, tran, addr, metadata, buyer, status, transporters
          ).toString
          if (! disableMailer) {
            system.scheduler.scheduleOnce(0.microsecond) {
              val mail = Email(
                subject = Messages("mail.cancel.site.owner.subject").format(tran.header.id.get),
                to = Seq(owner.email),
                from = from,
                bodyText = Some(body)
              )
              mailerClient.send(mail)
              logger.info("Shipping cancel confirmation for site owner " + site + " sent to " + owner.email)
            }
          }
        }
      }
    }
  }

  def sendToAdmin(
    login: LoginSession, tran: PersistedTransaction, addr: Option[Address],
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    orderNotificationRepo.listAdmin.foreach { admin =>
      logger.info("Sending ordering confirmation for admin sent to " + admin.email)
      val body = views.html.mail.forAdmin(login, admin, tran, addr, metadata).toString
      if (! disableMailer) {
        system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages("mail.admin.subject").format(tran.header.id.get),
            to = Seq(admin.email),
            from = from,
            bodyText = Some(body)
          )
          mailerClient.send(mail)
          logger.info("Ordering confirmation for admin sent to " + admin.email)
        }
      }
    }
  }

  def sendShippingPreparedNotificationToAdmin(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    tran.siteTable.foreach { site =>
      if (site.id.get == siteId) {
        orderNotificationRepo.listAdmin.foreach { admin =>
          logger.info("Sending shipping prepared notification for admin sent to " + admin.email)
          val body = views.html.mail.shippingPreparedNotificationForAdmin(
            login, site, admin, tran, addr, metadata, buyer, status, transporters
          ).toString
          if (! disableMailer) {
            system.scheduler.scheduleOnce(0.microsecond) {
              val mail = Email(
                subject = Messages("mail.shipping.prepared.admin.subject").format(tran.header.id.get),
                to = Seq(admin.email),
                from = from,
                bodyText = Some(body)
              )
              mailerClient.send(mail)
              logger.info("Shipping prepared notification for admin sent to " + admin.email)
            }
          }
        }
      }
    }
  }

  def sendShippingNotificationToAdmin(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    tran.siteTable.foreach { site =>
      if (site.id.get == siteId) {
        orderNotificationRepo.listAdmin.foreach { admin =>
          logger.info("Sending shipping notification for admin sent to " + admin.email)
          val body = views.html.mail.shippingNotificationForAdmin(
            login, site, admin, tran, addr, metadata, buyer, status, transporters
          ).toString
          if (! disableMailer) {
            system.scheduler.scheduleOnce(0.microsecond) {
              val mail = Email(
                subject = Messages("mail.shipping.admin.subject").format(tran.header.id.get),
                to = Seq(admin.email),
                from = from,
                bodyText = Some(body)
              )
              mailerClient.send(mail)
              logger.info("Shipping notification for admin sent to " + admin.email)
            }
          }
        }
      }
    }
  }

  def sendCancelNotificationToAdmin(
    login: LoginSession, siteId: Long, tran: PersistedTransaction, addr: Address,
    metadata: Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    status: TransactionShipStatus, transporters: immutable.LongMap[String]
  )(
    implicit conn: Connection, mp: MessagesProvider
  ) {
    val buyer = storeUserRepo(tran.header.userId)
    tran.siteTable.foreach { site =>
      if (site.id.get == siteId) {
        orderNotificationRepo.listAdmin.foreach { admin =>
          logger.info("Sending cancel notification for admin sent to " + admin.email)
          val body = views.html.mail.cancelNotificationForAdmin(
            login, site, admin, tran, addr, metadata, buyer, status, transporters
          ).toString
          if (! disableMailer) {
            system.scheduler.scheduleOnce(0.microsecond) {
              val mail = Email(
                subject = Messages("mail.cancel.admin.subject").format(tran.header.id.get),
                to = Seq(admin.email),
                from = from,
                bodyText = Some(body)
              )
              mailerClient.send(mail)
              logger.info("Shipping cancel notification for admin sent to " + admin.email)
            }
          }
        }
      }
    }
  }

  def sendResetPasswordConfirmation(
    user: StoreUser, rec: ResetPassword
  )(
    implicit mp: MessagesProvider
  ) {
    logger.info("Sending reset password confirmation to " + user.email)
    val body = views.html.mail.resetPassword(user, rec).toString
    if (! disableMailer) {
      system.scheduler.scheduleOnce(0.microsecond) {
        val mail = Email(
          subject = Messages("resetPassword.mail.subject"),
          to = Seq(user.email),
          from = from,
          bodyText = Some(body)
        )
        mailerClient.send(mail)
        logger.info("Reset password confirmation sent to " + user.email)
      }
    }
  }    
}
