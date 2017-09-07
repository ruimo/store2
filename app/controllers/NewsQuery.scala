package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.OptAuthenticated
import play.api.data.validation.Constraints._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.i18n.Lang
import models._
import org.joda.time.DateTime
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}

@Singleton
class NewsQuery @Inject() (
  cc: MessagesControllerComponents,
  optAuthenticated: OptAuthenticated,
  implicit val db: Database,
  implicit val newsRepo: NewsRepo,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def list(
    page:Int, pageSize:Int, orderBySpec: String
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin = db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) }
    db.withConnection { implicit conn =>
      Ok(views.html.newsList(
        newsRepo.list(page, pageSize, OrderBy(orderBySpec), System.currentTimeMillis))
      )
    }
  }

  def pagedList(
    page:Int, pageSize:Int, orderBySpec: String
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin = db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) }
    db.withConnection { implicit conn =>
      Ok(views.html.newsPagedList(newsRepo.list(page, pageSize, OrderBy(orderBySpec), System.currentTimeMillis)))
    }
  }

  def show(id: Long) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin = db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) }
    db.withConnection { implicit conn =>
      Ok(views.html.news(newsRepo(NewsId(id))))
    }
  }
}
