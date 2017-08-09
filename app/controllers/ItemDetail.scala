package controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.{Lang, Messages}
import controllers.NeedLogin.OptAuthenticated
import models._
import play.api.db.Database
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}

@Singleton
class ItemDetail @Inject() (
  cc: MessagesControllerComponents,
  optAuthenticated: OptAuthenticated,
  itemDetailRepo: ItemDetailRepo,
  db: Database,
  localeInfoRepo: LocaleInfoRepo,
  itemPriceStrategyRepo: ItemPriceStrategyRepo,
  loginSessionRepo: LoginSessionRepo,
  itemPictures: ItemPictures,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def show(itemId: Long, siteId: Long) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      implicit val optLogin = loginSessionRepo.fromRequest(request)
      itemDetailRepo.show(
        siteId, itemId, localeInfoRepo.getDefault(request.acceptLanguages.toList),
        itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
      ) match {
        case None => Ok(views.html.itemDetailNotFound())
        case Some(itemDetail) =>
          if (itemDetail.siteItemNumericMetadata.get(SiteItemNumericMetadataType.HIDE).map(_.metadata).getOrElse(0) == 1) {
            Ok(views.html.itemDetailNotFound())
          }
          else {
            itemDetail.siteItemNumericMetadata.get(SiteItemNumericMetadataType.ITEM_DETAIL_TEMPLATE) match {
              case None => Ok(views.html.itemDetail(itemDetail, itemPictures))
              case Some(metadata) =>
                if (metadata.metadata == 0) Ok(views.html.itemDetail(itemDetail, itemPictures))
                else Ok(
                  views.html.itemDetailTemplate(metadata.metadata, itemDetail, itemPictures.retrieveAttachmentNames(itemId))
                )
            }
          }
      }
    }
  }

  def showAsJson(itemId: Long, siteId: Long) = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      val optLogin = loginSessionRepo.fromRequest(request)
      Ok(
        asJson(
          itemDetailRepo.show(
            siteId, itemId, localeInfoRepo.getDefault(request.acceptLanguages.toList),
            itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
          )
        )
      )
    }
  }

  def asJson(detail: Option[models.ItemDetail]): JsObject = Json.obj(
    "name" -> JsString(
      detail.map { itd =>
        if (itd.siteItemNumericMetadata.get(SiteItemNumericMetadataType.HIDE).map(_.metadata).getOrElse(0) == 1) ""
        else itd.name
      }.getOrElse("")
    )
  )
}
