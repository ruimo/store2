package controllers

import javax.inject.{Inject, Singleton}

import play.Logger
import controllers.NeedLogin.Authenticated
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models._
import play.api.data.Form
import play.api.mvc.{AnyContent, Controller, MessagesAbstractController, MessagesControllerComponents}
import play.api.i18n.Messages
import models.CreateShippingBox
import play.api.db.Database
import play.api.i18n.Lang

@Singleton
class ShippingBoxMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val siteRepo: SiteRepo,
  implicit val shippingBoxRepo: ShippingBoxRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createShippingBoxForm = Form(
    mapping(
      "siteId" -> longNumber,
      "itemClass" -> longNumber,
      "boxSize" -> number,
      "boxName" -> text.verifying(nonEmpty, maxLength(32))
    ) (CreateShippingBox.apply)(CreateShippingBox.unapply)
  )

  val changeShippingBoxForm = Form(
    mapping(
      "id" -> longNumber,
      "siteId" -> longNumber,
      "itemClass" -> longNumber,
      "boxSize" -> number,
      "boxName" -> text.verifying(nonEmpty, maxLength(32))
    ) (ChangeShippingBox.apply)(ChangeShippingBox.unapply)
  )
    
  val removeBoxForm = Form(
    "boxId" -> longNumber
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.shippingBoxMaintenance())
    }
  }

  def startCreateShippingBox = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(views.html.admin.createNewShippingBox(createShippingBoxForm, siteRepo.tableForDropDown))
      }
    }
  }

  def createNewShippingBox = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val form = createShippingBoxForm.bindFromRequest

      form.fold(
        formWithErrors => {
          Logger.error("Validation error in ShippingBoxMaintenance.createNewShippingBox. " + formWithErrors)
          db.withConnection { implicit conn =>
            BadRequest(views.html.admin.createNewShippingBox(formWithErrors, siteRepo.tableForDropDown))
          }
        },
        newShippingBox => db.withConnection { implicit conn =>
          try {
            newShippingBox.save

            Redirect(
              routes.ShippingBoxMaintenance.startCreateShippingBox
            ).flashing("message" -> Messages("shippingBoxIsCreated"))
          }
          catch {
            case e: UniqueConstraintException =>
              BadRequest(
                views.html.admin.createNewShippingBox(
                  form.withError("itemClass", Messages("duplicatedItemClass")), siteRepo.tableForDropDown
                )
              )
          }
        }
      )
    }
  }

  def editShippingBox(start: Int, size: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(views.html.admin.editShippingBox(shippingBoxRepo.list))
      }
    }
  }

  def removeShippingBox = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val boxId = removeBoxForm.bindFromRequest.get
      db.withTransaction { implicit conn =>
        shippingBoxRepo.removeWithChildren(boxId)
      }

      Redirect(
        routes.ShippingBoxMaintenance.editShippingBox()
      ).flashing("message" -> Messages("shippingBoxIsRemoved"))
    }
  }

  def startChangeShippingBox(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        val rec = shippingBoxRepo(id)
        Ok(
          views.html.admin.changeShippingBox(
            changeShippingBoxForm.fill(
              ChangeShippingBox(
                id, rec.siteId, rec.itemClass, rec.boxSize, rec.boxName
              )
            ),
            siteRepo.tableForDropDown
          )
        )
      }
    }
  }

  def changeShippingBox = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      changeShippingBoxForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ShippingBoxMaintenance.changeShippingBox.")
          db.withConnection { implicit conn =>
            BadRequest(views.html.admin.changeShippingBox(formWithErrors, siteRepo.tableForDropDown))
          }
        },
        newShippingBox => db.withConnection { implicit conn =>
          try {
            newShippingBox.save
            Redirect(
              routes.ShippingBoxMaintenance.editShippingBox()
            ).flashing("message" -> Messages("shippingBoxIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException =>
              BadRequest(
                views.html.admin.changeShippingBox(
                  changeShippingBoxForm.fill(newShippingBox).withError(
                    "itemClass", Messages("unique.constraint.violation")
                  ),
                  siteRepo.tableForDropDown
                )
              )
          }
        }
      )
    }
  }
}
