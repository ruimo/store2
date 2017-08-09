package controllers

import play.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models._
import play.api.data.Form
import play.api.mvc._

import scala.collection.immutable.LongMap
import helpers.{Csv, NotificationMail}
import java.io.{ByteArrayInputStream, StringWriter}
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.db.Database
import play.api.http.ContentTypes
import views.csv.{TransactionDetailBodyCsv, TransactionDetailCsv}

@Singleton
class TransactionMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  transactionMaintenance: TransactionMaintenance,
  notificationMail: NotificationMail,
  transactionPersister: TransactionPersister,
  implicit val transactionSummary: TransactionSummary,
  implicit val db: Database,
  implicit val transporterRepo: TransporterRepo,
  implicit val transactionLogShippingRepo: TransactionLogShippingRepo,
  implicit val transactionDetailRepo: TransactionDetailRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val transactionLogSiteRepo: TransactionLogSiteRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val changeStatusForm = Form(
    mapping(
      "transactionSiteId" -> longNumber,
      "status" -> number
    )(ChangeTransactionStatus.apply)(ChangeTransactionStatus.unapply)
  )

  def shippingDeliveryDateForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "shippingDate" -> localDateTime(Messages("shippingDateFormat")),
      "deliveryDate" -> localDateTime(Messages("deliveryDateFormat"))
    )(ShippingDeliveryDate.apply)(ShippingDeliveryDate.unapply)
  )

  val entryShippingInfoForm = Form(
    mapping(
      "transporterId" -> longNumber,
      "slipCode" -> text.verifying(nonEmpty, maxLength(128))
    )(ChangeShippingInfo.apply)(ChangeShippingInfo.unapply)
  )

  def statusDropDown(implicit mp: MessagesProvider): Seq[(String, String)] =
    classOf[TransactionStatus].getEnumConstants.foldLeft(List[(String, String)]()) {
      (list, e) => (e.ordinal.toString, Messages("transaction.status." + e.toString)) :: list
    }.reverse

  def index(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        val pagedRecords = transactionSummary.list(
          siteId = login.siteUser.map(_.siteId),
          page = page, pageSize = pageSize, orderBy = OrderBy(orderBySpec)
        )
        Ok(
          views.html.admin.transactionMaintenance(
            pagedRecords,
            changeStatusForm, statusDropDown,
            LongMap[Form[ChangeShippingInfo]]().withDefaultValue(entryShippingInfoForm),
            transporterRepo.tableForDropDown,
            transporterRepo.listWithName.foldLeft(LongMap[String]()) {
              (sum, e) => sum.updated(e._1.id.get, e._2.map(_.transporterName).getOrElse("-"))
            },
            LongMap[Form[ShippingDeliveryDate]]().withDefaultValue(
              shippingDeliveryDateForm
            )
          )
        )
      }
    }
  }

  def setStatus = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeStatusForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in TransactionMaintenance.setStatus. " + formWithErrors)
          db.withConnection { implicit conn =>
            val pagedRecords = transactionSummary.list(
              login.siteUser.map(_.siteId)
            )

            BadRequest(
              views.html.admin.transactionMaintenance(
                pagedRecords,
                changeStatusForm, statusDropDown,
                LongMap[Form[ChangeShippingInfo]]().withDefaultValue(entryShippingInfoForm),
                transporterRepo.tableForDropDown,
                transporterRepo.listWithName.foldLeft(LongMap[String]()) {
                  (sum, e) => sum.updated(e._1.id.get, e._2.map(_.transporterName).getOrElse("-"))
                },
                LongMap[Form[ShippingDeliveryDate]]().withDefaultValue(
                  shippingDeliveryDateForm
                )
              )
            )
          }
        },
        newStatus => {
          db.withConnection { implicit conn =>
            newStatus.save(login.siteUser)
            Redirect(routes.TransactionMaintenance.index())
          }
        }
      )
    }
  }

  def detail(tranSiteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        val entry = transactionSummary.get(login.siteUser.map(_.siteId), tranSiteId).get
        val boxNameByItemSize = transactionLogShippingRepo.listBySite(tranSiteId).foldLeft(LongMap.empty[String]) {
          (sum, e) => (sum + (e.itemClass -> e.boxName))
        }
        Ok(
          views.html.admin.transactionDetail(
            entry,
            transactionDetailRepo.show(tranSiteId, localeInfoRepo.getDefault(request.acceptLanguages.toList), login.siteUser),
            transactionMaintenance.changeStatusForm, transactionMaintenance.statusDropDown,
            LongMap[Form[ChangeShippingInfo]]().withDefaultValue(entryShippingInfoForm),
            transporterRepo.tableForDropDown,
            transporterRepo.listWithName.foldLeft(LongMap[String]()) {
              (sum, e) => sum.updated(e._1.id.get, e._2.map(_.transporterName).getOrElse("-"))
            },
            boxNameByItemSize,
            LongMap[Form[ShippingDeliveryDate]]().withDefaultValue(
              shippingDeliveryDateForm
            )
          )
        )
      }
    }
  }

  def entryShippingInfo(tranId: Long, tranSiteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      entryShippingInfoForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in TransactionMaintenance.entryShippingInfo. " + formWithErrors)
          db.withTransaction { implicit conn =>
            val pagedRecords = transactionSummary.list(login.siteUser.map(_.siteId))

            BadRequest(
              views.html.admin.transactionMaintenance(
                pagedRecords,
                changeStatusForm, statusDropDown,
                LongMap[Form[ChangeShippingInfo]]()
                  .withDefaultValue(entryShippingInfoForm)
                  .updated(tranSiteId, formWithErrors),
                transporterRepo.tableForDropDown,
                transporterRepo.listWithName.foldLeft(LongMap[String]()) {
                  (sum, e) => sum.updated(e._1.id.get, e._2.map(_.transporterName).getOrElse("-"))
                },
                LongMap[Form[ShippingDeliveryDate]]().withDefaultValue(
                  shippingDeliveryDateForm
                )
              )
            )
          }
        },
        newShippingInfo => {
          db.withTransaction { implicit conn =>
            newShippingInfo.save(login.siteUser, tranSiteId) {
              val status = TransactionShipStatus.byTransactionSiteId(tranSiteId)
              sendNotificationMail(tranId, tranSiteId, newShippingInfo, localeInfoRepo.getDefault(request.acceptLanguages.toList), status)
            }
            Redirect(routes.TransactionMaintenance.index())
          }
        }
      )
    }
  }

  def entryShippingDeliveryDate(tranId: Long, tranSiteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      shippingDeliveryDateForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in TransactionMaintenance.entryShippingDeliveryDate. " + formWithErrors)
          db.withTransaction { implicit conn =>
            val pagedRecords = transactionSummary.list(login.siteUser.map(_.siteId))

            BadRequest(
              views.html.admin.transactionMaintenance(
                pagedRecords,
                changeStatusForm, statusDropDown,
                LongMap[Form[ChangeShippingInfo]]().withDefaultValue(entryShippingInfoForm),
                transporterRepo.tableForDropDown,
                transporterRepo.listWithName.foldLeft(LongMap[String]()) {
                  (sum, e) => sum.updated(e._1.id.get, e._2.map(_.transporterName).getOrElse("-"))
                },
                LongMap[Form[ShippingDeliveryDate]]()
                  .withDefaultValue(shippingDeliveryDateForm)
                  .updated(tranSiteId, formWithErrors)
              )
            )
          }
        },
        newShippingDeliveryDate => {
          db.withTransaction { implicit conn =>
            newShippingDeliveryDate.save(login.siteUser, tranSiteId)
            val status = TransactionShipStatus.byTransactionSiteId(tranSiteId)
            sendShippingDeliveryNotificationMail(tranId, tranSiteId, newShippingDeliveryDate, localeInfoRepo.getDefault(request.acceptLanguages.toList), status)
            Redirect(routes.TransactionMaintenance.index())
          }
        }
      )
    }
  }

  def cancelShipping(tranId: Long, tranSiteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withTransaction { implicit conn =>
        TransactionShipStatus.update(login.siteUser, tranSiteId, TransactionStatus.CANCELED)
        val status = TransactionShipStatus.byTransactionSiteId(tranSiteId)
        if (! status.mailSent) {
          TransactionShipStatus.mailSent(tranSiteId)
          sendCancelMail(tranId, tranSiteId, localeInfoRepo.getDefault(request.acceptLanguages.toList), status)
        }
        Redirect(routes.TransactionMaintenance.index())
      }
    }
  }

  def sendNotificationMail(
    tranId: Long, tranSiteId: Long, info: ChangeShippingInfo, locale: LocaleInfo, status: TransactionShipStatus
  )(implicit loginSession: LoginSession, mp: MessagesProvider) {
    db.withConnection { implicit conn =>
      val tran = transactionPersister.load(tranId, locale)
      val address = Address.byId(tran.shippingTable.head._2.head.addressId)
      val siteId = transactionLogSiteRepo.byId(tranSiteId).siteId
      val transporters = transporterRepo.mapWithName(locale)
      notificationMail.shipCompleted(loginSession, siteId, tran, address, status, transporters)
    }
  }

  def sendShippingDeliveryNotificationMail(
    tranId: Long, tranSiteId: Long, newShippingDeliveryDate: ShippingDeliveryDate, locale: LocaleInfo, status: TransactionShipStatus
  )(implicit loginSession: LoginSession, mp: MessagesProvider) {
    db.withConnection { implicit conn =>
      val tran = transactionPersister.load(tranId, locale)
      val address = Address.byId(tran.shippingTable.head._2.head.addressId)
      val siteId = transactionLogSiteRepo.byId(tranSiteId).siteId
      val transporters = transporterRepo.mapWithName(locale)
      notificationMail.shipPrepared(loginSession, siteId, tran, address, status, transporters)
    }
  }

  def sendCancelMail(
    tranId: Long, tranSiteId: Long, locale: LocaleInfo, status: TransactionShipStatus
  )(implicit loginSession: LoginSession, mp: MessagesProvider) {
    db.withConnection { implicit conn =>
      val tran = transactionPersister.load(tranId, locale)
      val address = Address.byId(tran.shippingTable.head._2.head.addressId)
      val siteId = transactionLogSiteRepo.byId(tranSiteId).siteId
      val transporters = transporterRepo.mapWithName(locale)
      notificationMail.shipCanceled(loginSession, siteId, tran, address, status, transporters)
    }
  }

  def downloadCsv(tranId: Long, tranSiteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      implicit val cs = play.api.mvc.Codec.javaSupported("Windows-31j")
      val fileName = "tranDetail" + tranId + "-" + tranSiteId + ".csv"

      Ok(
        createCsv(tranId, tranSiteId)
      ).as(
        "text/csv charset=Shift_JIS"
      ).withHeaders(
        CONTENT_DISPOSITION -> ("""attachment; filename="%s"""".format(fileName))
      )
    }
  }

  def createCsv(tranId: Long, tranSiteId: Long)(
    implicit mp: MessagesProvider,
    lang: Lang,
    loginSession: LoginSession
  ): String = {
    val (entry, details, shipping) = db.withConnection { implicit conn =>
      (
        transactionSummary.get(loginSession.siteUser.map(_.siteId), tranSiteId).get,
        transactionDetailRepo.show(tranSiteId, localeInfoRepo.byLang(lang), loginSession.siteUser),
        transactionLogShippingRepo.listBySite(tranSiteId)
      )
    }
    val writer = new StringWriter
    val csvWriter = TransactionDetailCsv.instance.createWriter(writer)
    val csvDetail = new TransactionDetailBodyCsv(csvWriter)
    details.foreach {
      detail => csvDetail.print(tranId, entry, detail)
    }
    shipping.foreach {
      s => csvDetail.printShipping(tranId, entry, s)
    }
    writer.toString
  }
}
