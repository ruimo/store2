package controllers

import play.api.i18n.Lang
import play.api.mvc._
import helpers.RecommendEngine
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import models._
import play.api.libs.json.{JsValue, Json}
import helpers.ViewHelpers
import play.api.Play.current
import com.ruimo.recoeng.json.SalesItem

import scala.concurrent.Future
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.Configuration
import play.api.db.Database

@Singleton
class Recommendation @Inject() (
  cc: MessagesControllerComponents,
  config: Configuration,
  authenticated: Authenticated,
  recommendEngine: RecommendEngine,
  implicit val db: Database,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val itemDetailRepo: ItemDetailRepo,
  implicit val itemPriceStrategyRepo: ItemPriceStrategyRepo,
  implicit val recommendByAdminRepo: RecommendByAdminRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def maxRecommendCount: Int = config.getOptional[Int]("recommend.maxCount").getOrElse(5)

  def byItemJson(siteId: Long, itemId: Long) = authenticated.async { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head

    scala.concurrent.Future {
      db.withConnection { implicit conn =>
        val items = byItems(
          Seq(SalesItem(siteId.toString, itemId.toString, 1)),
          localeInfoRepo.getDefault(request.acceptLanguages.toList), login
        )

        Ok(Json.toJson(Map("recommended" -> items)))
      }
    }
  }

  def byItems(
    shoppingCartItems: Seq[SalesItem], locale: LocaleInfo, loginSession: LoginSession
  )(
    implicit conn: Connection, lang: Lang
  ): Seq[JsValue] = {
    val byTransaction: Seq[models.ItemDetail] = recommendEngine.recommendByItem(
      shoppingCartItems
    ).map {
      it => itemDetailRepo.show(
        it.storeCode.toLong, it.itemCode.toLong, locale, 
        itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(loginSession))
      )
    }.flatMap {
      x => x
    }.filter {
      _.siteItemNumericMetadata.get(SiteItemNumericMetadataType.HIDE).map {
        _.metadata != 1
      }.getOrElse(true)
    }

    val byBoth = if (byTransaction.size < maxRecommendCount) {
      byTransaction ++ byAdmin(shoppingCartItems, maxRecommendCount - byTransaction.size, locale, loginSession)
    }
    else {
      byTransaction
    }

    byBoth.map {
      detail => Json.obj(
        "siteId" -> detail.siteId,
        "itemId" -> detail.itemId,
        "name" -> detail.name,
        "siteName" -> detail.siteName,
        "price" -> ViewHelpers.toAmount(detail.price)
      )
    }
  }

  def byAdmin(
    shoppingCartItems: Seq[SalesItem], maxCount: Int, locale: LocaleInfo, loginSession: LoginSession
  )(
    implicit conn: Connection
  ): Seq[models.ItemDetail] = calcByAdmin(
    shoppingCartItems, maxCount, 
    (maxRecordCount: Int) => recommendByAdminRepo.listByScore(
      showDisabled = false, locale, page = 0, pageSize = maxRecordCount
    ),
    (siteId: Long, itemId: Long) => itemDetailRepo.show(
      siteId, itemId, locale,
      itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(loginSession))
    ).get
  )

  def calcByAdmin(
    shoppingCartItems: Seq[SalesItem], maxCount: Int,
    queryRecommendByAdmin: Int => PagedRecords[(RecommendByAdmin, Option[ItemName], Option[Site])],
    queryItemDetail: (Long, Long) => models.ItemDetail
  ): Seq[models.ItemDetail] = {
    val salesItemsSet = shoppingCartItems.map { it => (it.storeCode.toLong, it.itemCode.toLong) }.toSet
    
    queryRecommendByAdmin(shoppingCartItems.size + maxCount).records.filter { t =>
      ! salesItemsSet.contains((t._1.siteId, t._1.itemId))
    }.take(maxCount).map { t =>
      queryItemDetail(t._1.siteId, t._1.itemId)
    }
  }

  def byShoppingCartJson() = authenticated.async { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head

    scala.concurrent.Future {
      db.withConnection { implicit conn =>
        val items = byItems(
          shoppingCartItemRepo.listAllItemsForUser(login.storeUser.id.get),
          localeInfoRepo.getDefault(request.acceptLanguages.toList), login
        )
          
        Ok(Json.toJson(Map("recommended" -> items)))
      }
    }
  }

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.recommendationMenu())
    }
  }
}
