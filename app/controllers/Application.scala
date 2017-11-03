package controllers

import java.time.Instant
import java.time.temporal.ChronoUnit
import models._
import play.api.mvc._
import javax.inject.{Inject, Singleton}

import models.{LoginSessionRepo, ShoppingCartItemRepo}
import play.api.db.Database

@Singleton
class Application @Inject() (
  cc: MessagesControllerComponents,
  optAuthenticated: NeedLogin.OptAuthenticated,
  loginSessionRepo: LoginSessionRepo,
  val storeUserRepo: StoreUserRepo,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def index = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      implicit val optLogin = loginSessionRepo.fromRequest(request)
      Ok(
        views.html.index(
          UserMetadata.recentlyJoindUsers(Instant.now().plus(-30, ChronoUnit.DAYS)).flatMap { um =>
            storeUserRepo.get(um.storeUserId).map {u => (u, um)}
          },
          UserMetadata.nearBirthDayUsers().flatMap { um =>
            storeUserRepo.get(um.storeUserId).map {u => (u, um)}
          }
        )
      )
    }
  }

  def notFound(path: String) = Action {
    Results.NotFound
  }
}
