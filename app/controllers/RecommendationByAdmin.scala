package controllers

import play.api.data.format.Formats._
import play.api.data.Form
import play.api.data.Forms
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.i18n.Lang
import helpers.RecommendEngine
import play.api.libs.json.Json
import models.SiteItemNumericMetadataType
import play.api.libs.json.{JsValue, Json}
import helpers.ViewHelpers
import com.ruimo.recoeng.json.SalesItem
import models.ShoppingCartItem

import scala.concurrent.Future
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import helpers.QueryString
import models._
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class RecommendationByAdmin @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val itemRepo: ItemRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val itemPriceStrategyRepo: ItemPriceStrategyRepo,
  implicit val recommendByAdminRepo: RecommendByAdminRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val changeRecordForm = Form(
    mapping(
      "id" -> longNumber,
      "score" -> longNumber(min = 0),
      "enabled" -> boolean
    )(ChangeRecommendationByAdmin.apply)(ChangeRecommendationByAdmin.unapply)
  )

  def startEditRecommendByAdmin = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.recommendationByAdminMenu())
    }
  }

  def selectItem(
    qs: List[String], pgStart: Int, pgSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val queryStr = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
      db.withConnection { implicit conn =>
        val list = itemRepo.listForMaintenance(
          siteUser = None, locale = localeInfoRepo.getDefault(request.acceptLanguages.toList), queryString = queryStr, page = pgStart,
          pageSize = pgSize, orderBy = OrderBy(orderBySpec)
        )

        Ok(
          views.html.admin.selectItemForRecommendByAdmin(
            queryStr, list,
            itemPriceStrategyRepo(ItemPriceStrategyContext(login))
          )
        )
      }
    }
  }

  def startUpdate(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        val records = recommendByAdminRepo.listByScore(
          true, localeInfoRepo.getDefault(request.acceptLanguages.toList), page, pageSize
        ).map { t =>
          (t._1, t._2, t._3, changeRecordForm.fill(ChangeRecommendationByAdmin(t._1.id.get, t._1.score, t._1.enabled)))
        }
        Ok(views.html.admin.editRecommendationByAdmin(records))
      }
    }
  }

  def addRecommendation(
    siteId: Long, itemId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        try {
          recommendByAdminRepo.createNew(siteId, itemId)
          Redirect(
            routes.RecommendationByAdmin.selectItem(List())
          ).flashing(
            "message" -> Messages("itemIsCreated")
          )
        }
        catch {
          case e: UniqueConstraintException =>
            Redirect(
              routes.RecommendationByAdmin.selectItem(List())
            ).flashing(
              "errorMessage" -> Messages("unique.constraint.violation")
            )
        }
      }
    }
  }

  def removeRecommendation(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        recommendByAdminRepo.remove(id)
        Redirect(
          routes.RecommendationByAdmin.startUpdate()
        ).flashing(
          "message" -> Messages("recommendationRemoved")
        )
      }
    }
  }

  def changeRecommendation(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      changeRecordForm.bindFromRequest.fold(
        formWithErrors => {
          db.withConnection { implicit conn =>
            val records = recommendByAdminRepo.listByScore(
              true, localeInfoRepo.getDefault(request.acceptLanguages.toList), page, pageSize
            ).map { t =>
              if (t._1.id.get == formWithErrors("id").value.get.toLong) {
                (t._1, t._2, t._3, formWithErrors)
              }
              else {
                (t._1, t._2, t._3,
                 changeRecordForm.fill(ChangeRecommendationByAdmin(t._1.id.get, t._1.score, t._1.enabled)))
              }
            }
            BadRequest(views.html.admin.editRecommendationByAdmin(records))
          }
        },
        newRecommendation => {
          db.withConnection { implicit conn =>
            recommendByAdminRepo.updateScoreAndEnabled(
              newRecommendation.id,
              newRecommendation.score,
              newRecommendation.enabled
            )
            Redirect(
              routes.RecommendationByAdmin.startUpdate(page, pageSize)
            ).flashing(
              "message" -> Messages("recommendationUpdated")
            )
          }
        }
      )
    }
  }
}
