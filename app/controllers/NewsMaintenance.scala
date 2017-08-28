package controllers

import helpers.Forms._
import helpers.Cache
import javax.inject.{Inject, Singleton}

import play.Logger
import controllers.NeedLogin.Authenticated
import models._
import play.api.data.validation.Constraints._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, Result}
import play.api.db.Database

@Singleton
class NewsMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  newsPictures: NewsPictures,
  val cache: Cache,
  implicit val newsRepo: NewsRepo,
  implicit val db: Database,
  implicit val siteRepo: SiteRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) with NewsCommon {
  def createForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "title" -> text.verifying(nonEmpty, maxLength(255)),
      "contents" ->  text.verifying(nonEmpty, maxLength(65535)),
      "releaseDate" -> instant(Messages("news.date.format")),
      "site" -> optional(longNumber)
    )(CreateNews.apply)(CreateNews.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    checkLogin(login) {
      Ok(views.html.admin.newsMaintenance())
    }
  }

  def startCreateNews = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn =>
      checkLogin(login) {
        Ok(views.html.admin.createNews(createForm, siteRepo.tableForDropDown))
      }
    }
  }

  def createNews = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    checkLogin(login) {
      createForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in NewsMaintenance.createNews. " + formWithErrors)
          db.withConnection { implicit conn =>
            BadRequest(views.html.admin.createNews(formWithErrors, siteRepo.tableForDropDown))
          }
        },
        news => db.withConnection { implicit conn =>
          news.save(login)
          Redirect(
            routes.NewsMaintenance.startCreateNews()
          ).flashing("message" -> Messages("newsIsCreated"))
        }
      )
    }
  }

  def editNews(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    checkLogin(login) {
      db.withConnection { implicit conn =>
        Ok(
          views.html.admin.editNews(
            newsRepo.list(
              page, pageSize, OrderBy(orderBySpec), newsRepo.MaxDate, if (login.isSuperUser) None else Some(login.userId)
            )
          )
        )
      }
    }
  }

  def modifyNewsStart(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    checkLogin(login) {
      db.withConnection { implicit conn =>
        val news = newsRepo(NewsId(id))
        // Only super user or a user he/she owns this news entry can modify it.
        if (login.isSuperUser || Some(login.userId) == news._1.userId) {
          Ok(
            views.html.admin.modifyNews(
              id,
              createForm.fill(
                CreateNews(
                  news._1.title, news._1.contents, news._1.releaseTime, news._1.siteId
                )
              ),
              siteRepo.tableForDropDown,
              newsPictures.retrieveAttachmentNames(id)
            )
          )
        }
        else {
          Redirect(routes.Application.index)
        }
      }
    }
  }

  def modifyNews(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    checkLogin(login) {
      createForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in NewsMaintenance.modifyNews.")
          BadRequest(
            views.html.admin.modifyNews(
              id, formWithErrors,
              db.withConnection { implicit conn =>
                siteRepo.tableForDropDown
              },
              newsPictures.retrieveAttachmentNames(id)
            )
          )
        },
        news => db.withConnection { implicit conn =>
          news.update(id, if (login.isSuperUser) None else Some(login.userId))
          Redirect(
            routes.NewsMaintenance.editNews()
          ).flashing("message" -> Messages("newsIsUpdated"))
        }
      )
    }
  }

  def deleteNews(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    checkLogin(login) {
      newsPictures.removeAllPictures(id)
      db.withConnection { implicit conn =>
        newsRepo.delete(NewsId(id), if (login.isSuperUser) None else Some(login.userId))
        Redirect(
          routes.NewsMaintenance.editNews()
        ).flashing("message" -> Messages("newsIsRemoved"))
      }
    }
  }
}
