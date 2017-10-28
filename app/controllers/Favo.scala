package controllers

import scala.collection.immutable
import helpers.Cache
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.data.Forms._
import models._
import play.api.data.Form

import collection.immutable.LongMap
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, RequestHeader}
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.db.Database

@Singleton
class Favo @Inject() (
  cc: MessagesControllerComponents,
  authenticated: NeedLogin.Authenticated,
  favoRepo: FavoRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val db: Database,
) extends MessagesAbstractController(cc) {
  def show(kindId: Int, contentId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    
    db.withConnection { implicit conn =>
      val isFav = favoRepo.isFav(FavoKind.byIndex(kindId), ContentId(contentId), login.userId)
      Ok(
        views.html.showFavoCount(
          kindId, contentId,
          favoRepo.count(FavoKind.byIndex(kindId), ContentId(contentId)),
          isFav
        )
      )
    }
  }

  def inc(kindId: Int, contentId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    db.withConnection { implicit conn =>
      favoRepo.create(FavoKind.byIndex(kindId), ContentId(contentId), login.userId)
    }
    Redirect(routes.Favo.show(kindId, contentId))
  }

  def dec(kindId: Int, contentId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    db.withConnection { implicit conn =>
      favoRepo.remove(FavoKind.byIndex(kindId), ContentId(contentId), login.userId)
    }
    Redirect(routes.Favo.show(kindId, contentId))
  }
}
