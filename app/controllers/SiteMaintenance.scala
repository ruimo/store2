package controllers

import javax.inject.{Inject, Singleton}

import play.Logger
import controllers.NeedLogin.Authenticated
import play.api.libs.json.{JsObject, Json}
import models._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form
import play.api.db.Database
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class SiteMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val db: Database,
  implicit val siteRepo: SiteRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createSiteForm = Form(
    mapping(
      "langId" -> longNumber,
      "siteName" -> text.verifying(nonEmpty, maxLength(32))
    ) (CreateSite.apply)(CreateSite.unapply)
  )

  val changeSiteForm = Form(
    mapping(
      "siteId" -> longNumber,
      "langId" -> longNumber,
      "siteName" -> text.verifying(nonEmpty, maxLength(32))
    ) (ChangeSite.apply)(ChangeSite.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.siteMaintenance())
    }
  }

  def startCreateNewSite = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.createNewSite(createSiteForm, localeInfoRepo.localeTable))
    }
  }

  def createNewSite = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      createSiteForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in SiteMaintenance.createNewSite.")
          BadRequest(views.html.admin.createNewSite(formWithErrors, localeInfoRepo.localeTable))
        },
        newSite => db.withConnection { implicit conn =>
          newSite.save
          Redirect(
            routes.SiteMaintenance.startCreateNewSite()
          ).flashing("message" -> Messages("siteIsCreated"))
        }
      )
    }
  }

  def editSite = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(views.html.admin.editSite(siteRepo.listByName()))
      }
    }
  }

  def changeSiteStart(siteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        val site = siteRepo(siteId)
        Ok(
          views.html.admin.changeSite(
            changeSiteForm.fill(ChangeSite(site)),
            localeInfoRepo.localeTable
          )
        )
      }
    }
  }

  def changeSite = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      changeSiteForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in SiteMaintenance.changeSite.")
          BadRequest(views.html.admin.changeSite(formWithErrors, localeInfoRepo.localeTable))
        },
        newSite => db.withConnection { implicit conn =>
          newSite.update()
          Redirect(
            routes.SiteMaintenance.editSite()
          ).flashing("message" -> Messages("siteIsChanged"))
        }
      )
    }
  }

  def deleteSite(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        siteRepo.delete(id)
      }
      Redirect(routes.SiteMaintenance.editSite())
    }
  }

  def sitesAsJson = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(Json.obj(
          "sites" -> siteRepo.tableForDropDown.map { t => t._2 }.toSeq
        ))
      }
    }
  }
}
