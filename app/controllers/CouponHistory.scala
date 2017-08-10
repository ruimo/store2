package controllers

import javax.inject.{Inject, Singleton}

import java.time.Instant
import models._
import play.api.mvc._
import play.api.db.Database

@Singleton
class CouponHistory @Inject() (
  cc: MessagesControllerComponents,
  authenticated: NeedLogin.Authenticated,
  implicit val db: Database,
  implicit val transactionLogCouponRepo: TransactionLogCouponRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def showPurchasedCouponList(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

   db.withConnection { implicit conn =>
      val list: PagedRecords[CouponDetail] = transactionLogCouponRepo.list(
        locale = localeInfoRepo.getDefault(request.acceptLanguages.toList),
        userId = login.userId, 
        page = page,
        pageSize = pageSize
      )

      Ok(views.html.showCouponHistory(list))
    }
  }

  def showPurchasedCoupon(
    tranCouponId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val c = transactionLogCouponRepo.at(
        localeInfoRepo.getDefault(request.acceptLanguages.toList), login.userId, TransactionLogCouponId(tranCouponId)
      )
      showCoupon(
        c.siteItemNumericMetadata,
        c.couponDetail.itemId,
        c.couponDetail.time,
        Some(c.couponDetail.tranHeaderId)
      )
    }
  }

  def showInstantCoupon(siteId: Long, itemId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    val metaData: Map[SiteItemNumericMetadataType, SiteItemNumericMetadata] = 
      db.withConnection { implicit conn => siteItemNumericMetadataRepo.all(siteId, ItemId(itemId)) }

    if (metaData.get(SiteItemNumericMetadataType.INSTANT_COUPON).getOrElse(0) != 0) {
      showCoupon(metaData, ItemId(itemId), Instant.now(), None)
    }
    else {
      Redirect(routes.Application.index)
    }
  }

  def showCoupon(
    siteItemNumericMetadata: Map[SiteItemNumericMetadataType, SiteItemNumericMetadata],
    itemId: ItemId,
    time: Instant,
    tranId: Option[Long]
  )(
    implicit request: MessagesRequest[AnyContent],
    login: LoginSession
  ): Result = {
    siteItemNumericMetadata.get(SiteItemNumericMetadataType.COUPON_TEMPLATE) match {
      case None => Ok(
        views.html.showCoupon(itemId, time, tranId)
      )
      case Some(metadata) =>
        if (metadata.metadata == 0) Ok(
          views.html.showCoupon(itemId, time, tranId)
        )
        else Ok(
          views.html.showCouponTemplate(metadata.metadata, itemId, time, tranId)
        )
    }
  }
}
