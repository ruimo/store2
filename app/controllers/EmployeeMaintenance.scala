package controllers

import play.Logger
import javax.inject.{Inject, Singleton}

import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, RequestHeader}
import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import helpers.Cache
import models._
import play.api.db.Database
import play.api.mvc.MessagesControllerComponents
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.Form

@Singleton
class EmployeeMaintenance @Inject() (
  cc: MessagesControllerComponents,
  storeUserRepo: StoreUserRepo,
  authenticated: Authenticated,
  employeeRepo: EmployeeRepo,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createForm = Form(
    mapping(
      "siteId" -> longNumber
    )(CreateEmployment.apply)(CreateEmployment.unapply)
  )

  def startModify(userId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      Ok(
        db.withConnection { implicit conn =>
          views.html.admin.employeeMaintenance(
            userId,
            employeeRepo.siteTable(userId),
            employeeRepo.list(userId),
            createForm
          )
        }
      )
    }
  }

  def swap(userId: Long, index0: Int, index1: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      db.withTransaction { implicit conn =>
        employeeRepo.swapIndicies(userId, index0, index1)
      }
      Redirect(
        routes.EmployeeMaintenance.startModify(userId)
      )
    }
  }

  def remove(userId: Long, employeeId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      db.withTransaction { implicit conn =>
        employeeRepo.remove(EmployeeId(employeeId))
      }
      Redirect(
        routes.EmployeeMaintenance.startModify(userId)
      )
    }
  }

  def create(userId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      createForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in EmployeeMaintenance.create. " + formWithErrors)
          BadRequest(
            db.withConnection { implicit conn =>
              views.html.admin.employeeMaintenance(
                userId,
                employeeRepo.siteTable(userId),
                employeeRepo.list(userId),
                formWithErrors
              )
            }
          )
        },
        newRec => db.withConnection { implicit conn =>
          employeeRepo.createNew(newRec.siteId, userId)
          Redirect(
            routes.EmployeeMaintenance.startModify(userId)
          )
        }
      )
    }
  }
}
