package controllers

import javax.inject.{Inject, Singleton}

import play.Logger
import controllers.NeedLogin.Authenticated
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form
import models._
import play.api.i18n.Messages
import models.CreateTransporter
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

case class ChangeTransporter(
  id: Long,
  langTable: Seq[(String, String)],
  transporterNameTableForm: Form[ChangeTransporterNameTable],
  newTransporterNameForm: Form[ChangeTransporterName]
)

@Singleton
class TransporterMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val db: Database,
  implicit val transporterRepo: TransporterRepo,
  implicit val transporterNameRepo: TransporterNameRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createTransporterForm = Form(
    mapping(
      "langId" -> longNumber,
      "transporterName" -> text.verifying(nonEmpty, maxLength(64))
    ) (CreateTransporter.apply)(CreateTransporter.unapply)
  )

  val addTransporterNameForm = Form(
    mapping(
      "localeId" -> longNumber,
      "transporterName" -> text.verifying(nonEmpty, maxLength(64))
    ) (ChangeTransporterName.apply)(ChangeTransporterName.unapply)
  )

  val changeTransporterNameForm = Form(
    mapping(
      "transporterNames" -> seq(
        mapping(
          "localeId" -> longNumber,
          "transporterName" -> text.verifying(nonEmpty, maxLength(64))
        ) (ChangeTransporterName.apply)(ChangeTransporterName.unapply)
      )
    ) (ChangeTransporterNameTable.apply)(ChangeTransporterNameTable.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.transporterMaintenance())
    }
  }

  def startCreateNewTransporter = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.createNewTransporter(createTransporterForm, localeInfoRepo.localeTable))
    }
  }

  def createNewTransporter = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      createTransporterForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in TransporterMaintenance.createNewTransporter.")
          BadRequest(views.html.admin.createNewTransporter(formWithErrors, localeInfoRepo.localeTable))
        },
        newTransporter => db.withTransaction { implicit conn =>
          try {
            newTransporter.save
            Redirect(
              routes.TransporterMaintenance.startCreateNewTransporter
            ).flashing("message" -> Messages("transporterIsCreated"))
          }
          catch {
            case e: UniqueConstraintException =>
              BadRequest(
                views.html.admin.createNewTransporter(
                  createTransporterForm.fill(newTransporter).withError("transporterName", "unique.constraint.violation"),
                  localeInfoRepo.localeTable
                )
              )
          }
        }
      )
    }
  }

  def editTransporter = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(views.html.admin.editTransporter(transporterRepo.listWithName))
      }
    }
  }

  def startChangeTransporter(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.changeTransporter(
        ChangeTransporter(
          id,
          localeInfoRepo.localeTable,
          createTransporterNameTable(id),
          addTransporterNameForm
        )
      ))
    }
  }

  def createTransporterNameTable(id: Long): Form[ChangeTransporterNameTable] = {
    db.withConnection { implicit conn => {
      val transporterNames = transporterNameRepo.list(id).values.map {
        n => ChangeTransporterName(n.localeId, n.transporterName)
      }.toSeq

      changeTransporterNameForm.fill(ChangeTransporterNameTable(transporterNames))
    }}
  }

  def removeTransporterName(id: Long, localeId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        transporterNameRepo.remove(id, localeId)
      }

      Redirect(
        routes.TransporterMaintenance.startChangeTransporter(id)
      ).flashing("message" -> Messages("transporterIsUpdated"))
    }
  }

  def changeTransporterName(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      changeTransporterNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation errro in TransporterMaintenance.changeTransporterName." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeTransporter(
              ChangeTransporter(
                id,
                localeInfoRepo.localeTable,
                formWithErrors,
                addTransporterNameForm
              )
            )
          )
        },
        newTransporter => {
          db.withTransaction { implicit conn =>
            newTransporter.update(id)
          }
          Redirect(
            routes.TransporterMaintenance.startChangeTransporter(id)
          ).flashing("message" -> Messages("transporterIsUpdated"))
        }
      )
    }
  }

  def addTransporterName(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      addTransporterNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in TransporterMaintenance.addItemName." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeTransporter(
              ChangeTransporter(
                id,
                localeInfoRepo.localeTable,
                createTransporterNameTable(id),
                formWithErrors
              )
            )
          )
        },
        newTransporter => {
          try {
            db.withConnection { implicit conn =>
              newTransporter.add(id)
            }
            Redirect(
              routes.TransporterMaintenance.startChangeTransporter(id)
            ).flashing("message" -> Messages("transporterIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeTransporter(
                  ChangeTransporter(
                    id,
                    localeInfoRepo.localeTable,
                    createTransporterNameTable(id),
                    addTransporterNameForm.fill(newTransporter).withError("localeId", "unique.constraint.violation")
                  )
                )
              )
            }
          }
        }
      )
    }
  }
}
