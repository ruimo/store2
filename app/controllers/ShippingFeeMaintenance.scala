package controllers


import java.time.LocalDateTime

import play.Logger
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc._
import models._
import play.api.data.Form
import models.CreateShippingBox
import org.joda.time.DateTime
import play.api.db.Database
import play.api.{Configuration, Play}

@Singleton
class ShippingFeeMaintenance @Inject() (
  cc: MessagesControllerComponents,
  config: Configuration,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val shippingFeeHistoryRepo: ShippingFeeHistoryRepo,
  implicit val shippingBoxRepo: ShippingBoxRepo,
  implicit val shippingFeeRepo: ShippingFeeRepo,
  implicit val taxRepo: TaxRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  lazy val ShippingToCountries = config.get[Seq[String]]("ship.to.countries").map {
    CountryCode.byName(_)
  }
  def countryDropDown(implicit mp: MessagesProvider) = ShippingToCountries.map {
    c => String.valueOf(c.code()) -> Messages("country." + c.name)
  }
  val LocationCodeTable = Map(
    CountryCode.JPN -> Address.JapanPrefecturesIntSeq
  )

  val createShippingBoxForm = Form(
    mapping(
      "siteId" -> longNumber,
      "itemClass" -> longNumber,
      "boxSize" -> number,
      "boxName" -> text.verifying(nonEmpty, maxLength(32))
    ) (CreateShippingBox.apply)(CreateShippingBox.unapply)
  )

  def feeMaintenanceForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "boxId" -> longNumber,
      "now" -> localDateTime(Messages("shipping.fee.maintenance.date.format"))
    )(FeeMaintenance.apply)(FeeMaintenance.unapply)
  )

  val changeFeeHistoryForm = Form(
    mapping(
      "histories" -> seq(
        mapping(
          "historyId" -> longNumber,
          "taxId" -> longNumber,
          "fee" -> bigDecimal.verifying(min(BigDecimal(0))),
          "costFee" -> optional(bigDecimal.verifying(min(BigDecimal(0)))),
          "validUntil" -> localDateTime("yyyy-MM-dd HH:mm:ss")
        ) (ChangeFeeHistory.apply)(ChangeFeeHistory.unapply)
      )
    ) (ChangeFeeHistoryTable.apply)(ChangeFeeHistoryTable.unapply)
  )

  val addFeeHistoryForm = Form(
    mapping(
      "historyId" -> ignored(0L),
      "taxId" -> longNumber,
      "fee" -> bigDecimal.verifying(min(BigDecimal(0))),
      "costFee" -> optional(bigDecimal.verifying(min(BigDecimal(0)))),
      "validUntil" -> localDateTime("yyyy-MM-dd HH:mm:ss")
    ) (ChangeFeeHistory.apply)(ChangeFeeHistory.unapply)
  )

  val createShippingFeeForm = Form(
    mapping(
      "feeId" -> longNumber,
      "countryCode" -> number,
      "locationCodeTable" -> list(number)
    ) (CreateShippingFee.apply)(CreateShippingFee.unapply)
  )

  val removeHistoryForm = Form(
    "historyId" -> longNumber
  )

  val removeFeeForm = Form(
    "feeId" -> longNumber
  )

  val createFeeForm = Form(
    tuple(
      "boxId" -> longNumber,
      "countryCode" -> longNumber
    )
  )

  def createFeeHistoryForm(feeId: Long): Form[ChangeFeeHistoryTable] = {
    db.withConnection { implicit conn => {
      val histories = shippingFeeHistoryRepo.list(feeId).map {
        h => ChangeFeeHistory(h.id.get, h.taxId, h.fee, h.costFee, h.validUntil)
      }.toSeq

      changeFeeHistoryForm.fill(ChangeFeeHistoryTable(histories))
    }}
  }

  def startFeeMaintenanceNow(boxId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      feeMaintenance(boxId, LocalDateTime.now())
    }
  }

  def startFeeMaintenance = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      feeMaintenanceForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ShippingFeeMaintenance.startFeeMaintenance.")
          BadRequest(
            views.html.admin.shippingFeeMaintenance(
              formWithErrors, None, List(), createFeeForm, countryDropDown
            )
          )
        },
        newDate => feeMaintenance(newDate.boxId, newDate.now)
      )
    }
  }

  private def feeMaintenance(boxId: Long, now: LocalDateTime)(
    implicit req: MessagesRequest[AnyContent],
    loginSession: LoginSession
  ): Result = {
    db.withConnection { implicit conn =>
      shippingBoxRepo.getWithSite(boxId) match {
        case Some(rec) => {
          val list = shippingFeeRepo.listWithHistory(boxId, now)
          Ok(
            views.html.admin.shippingFeeMaintenance(
              feeMaintenanceForm.fill(FeeMaintenance(boxId, now)),
              Some(rec),
              list,
              createFeeForm,
              countryDropDown
            )
          )
        }
        case None =>
          Redirect(
            routes.ShippingBoxMaintenance.editShippingBox()
          ).flashing("message" -> Messages("record.already.deleted"))
      }
    }
  }

  def editHistory(feeId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head

    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        val fee = shippingFeeRepo(feeId)
        val box = shippingBoxRepo(fee.shippingBoxId)
        val form = createFeeHistoryForm(feeId)

        Ok(
          views.html.admin.shippingFeeHistoryMaintenance(
            box, fee, form, addFeeHistoryForm, taxRepo.tableForDropDown
          )
        )
      }
    }
  }

  def changeHistory(feeId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head

    NeedLogin.assumeSuperUser(login) {
      db.withTransaction { implicit conn =>
        val fee = shippingFeeRepo(feeId)

        changeFeeHistoryForm.bindFromRequest.fold(
          formWithErrors => {
            Logger.error("Validation error in ShippingFeeMaintenance.changeHistory." + formWithErrors + ".")
            BadRequest(
              views.html.admin.shippingFeeHistoryMaintenance(
                shippingBoxRepo(fee.shippingBoxId),
                fee,
                formWithErrors, addFeeHistoryForm, taxRepo.tableForDropDown
              )
            )
          },
          newHistories => {
            newHistories.update(feeId)
            Redirect(
              routes.ShippingFeeMaintenance.editHistory(feeId)
            ).flashing("message" -> Messages("shippingFeeHistoryUpdated"))
          }
        )
      }
    }
  }

  def addHistory(feeId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head

    NeedLogin.assumeSuperUser(login) {
      addFeeHistoryForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ShippingFeeMaintenance.addHistory." + formWithErrors + ".")
          db.withConnection { implicit conn =>
            val fee = shippingFeeRepo(feeId)

            BadRequest(
              views.html.admin.shippingFeeHistoryMaintenance(
                shippingBoxRepo(fee.shippingBoxId),
                fee,
                createFeeHistoryForm(feeId), formWithErrors, taxRepo.tableForDropDown
              )
            )
          }
        },
        newHistory => {
          db.withTransaction { implicit conn =>
            newHistory.add(feeId)
          }
          Redirect(
            routes.ShippingFeeMaintenance.editHistory(feeId)
          ).flashing("message" -> Messages("shippingFeeHistoryAdded"))
        }
      )
    }
  }

  def removeHistory = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val historyId = removeHistoryForm.bindFromRequest.get
      db.withConnection { implicit conn =>
        val his = shippingFeeHistoryRepo(historyId)
        shippingFeeHistoryRepo.remove(historyId)

        Redirect(
          routes.ShippingFeeMaintenance.editHistory(his.shippingFeeId)
        ).flashing("message" -> Messages("shippingFeeHistoryRemoved"))
      }
    }
  }

  def startCreateShippingFee = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val (boxId, cc) = createFeeForm.bindFromRequest.get
      val countryCode = CountryCode.byIndex(cc.toInt)
      val existingLocations = db.withConnection { implicit conn =>
        shippingFeeRepo.list(boxId, countryCode).map { fee =>
          fee.locationCode
        }.toSet
      }
    
      Ok(
        views.html.admin.createShippingFee(
          boxId,
          createShippingFeeForm.fill(
            CreateShippingFee(
              boxId.toLong, countryCode.code(), existingLocations.toList
            )
          ),
          existingLocations,
          LocationCodeTable(countryCode)
        )
      )
    }
  }

  def createShippingFee(boxId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      createShippingFeeForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ShippingFeeMaintenance.createShippingFeeForm." + formWithErrors + ".")
          Redirect(
            routes.ShippingFeeMaintenance.startFeeMaintenance()
          )
        },
        newFee => {
          db.withConnection { implicit conn =>
            newFee.update(boxId)
            Redirect(
              routes.ShippingFeeMaintenance.startFeeMaintenanceNow(boxId)
            ).flashing("message" -> Messages("shippingFeeIsUpdated"))
          }
        }
      )
    }
  }

  def removeFee(boxId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val feeId = removeFeeForm.bindFromRequest.get
      db.withTransaction { implicit conn =>
        shippingFeeRepo.removeWithHistories(feeId)
        Redirect(
          routes.ShippingFeeMaintenance.startFeeMaintenanceNow(boxId)
        ).flashing("message" -> Messages("shippingFeeIsRemoved"))
      }
    }
  }
}
