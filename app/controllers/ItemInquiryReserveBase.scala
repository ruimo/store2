package controllers

import scala.collection.immutable

import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Lang, Messages}
import play.api.data.validation.Constraints._
import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import helpers.ItemInquiryMail
import play.Logger
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

class ItemInquiryReserveBase(
  cc: MessagesControllerComponents,
  fc: FormConstraints,
  authenticated: Authenticated,
  db: Database,
  itemInquiryMail: ItemInquiryMail,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val siteItemRepo: SiteItemRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val idSubmitForm: Form[Long] = Form(
    single(
      "id" -> longNumber
    )
  )

  def itemInquiryForm: Form[CreateItemInquiryReservation] = Form(
    mapping(
      "siteId" -> longNumber,
      "itemId" -> longNumber,
      "name" -> text.verifying(nonEmpty, maxLength(128)),
      "email" -> text.verifying(fc.emailConstraint: _*),
      "inquiryBody" -> text.verifying(nonEmpty, maxLength(8192))
    )(CreateItemInquiry.apply)(CreateItemInquiry.unapply)
  ).asInstanceOf[Form[CreateItemInquiryReservation]]

  def itemReservationForm: Form[CreateItemInquiryReservation] = Form(
    mapping(
      "siteId" -> longNumber,
      "itemId" -> longNumber,
      "name" -> text.verifying(nonEmpty, maxLength(128)),
      "email" -> text.verifying(fc.emailConstraint: _*),
      "comment" -> text.verifying(minLength(0), maxLength(8192))
    )(CreateItemReservation.apply)(CreateItemReservation.unapply)
  ).asInstanceOf[Form[CreateItemInquiryReservation]]

  def startItemInquiry(
    siteId: Long, itemId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    Ok(
      views.html.itemInquiry(itemInfo(siteId, itemId, request.acceptLanguages.toList), inquiryStartForm(siteId, itemId, login.storeUser))
    )
  }

  def startItemReservation(
    siteId: Long, itemId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    Ok(
      views.html.itemReservation(itemInfo(siteId, itemId, request.acceptLanguages.toList), reservationStartForm(siteId, itemId, login.storeUser))
    )
  }

  def inquiryStartForm(
    siteId: Long, itemId: Long, user: StoreUser
  )(
    implicit login: LoginSession
  ): Form[_ <: CreateItemInquiryReservation] = itemInquiryForm.fill(
    CreateItemInquiry(
      siteId, itemId,
      user.fullName,
      user.email, ""
    )
  )

  def reservationStartForm(
    siteId: Long, itemId: Long, user: StoreUser
  )(
    implicit login: LoginSession
  ): Form[_ <: CreateItemInquiryReservation] = itemReservationForm.fill(
    CreateItemReservation(
      siteId, itemId,
      user.fullName,
      user.email, ""
    ).asInstanceOf[CreateItemInquiryReservation]
  )

  def itemInfo(
    siteId: Long, itemId: Long, langs: List[Lang]
  ): (Site, ItemName) = db.withConnection { implicit conn =>
    siteItemRepo.getWithSiteAndItem(siteId, ItemId(itemId), localeInfoRepo.getDefault(langs))
  }.get

  def amendReservationForm(
    rec: ItemInquiry, fields: immutable.Map[Symbol, String]
  ): Form[_ <: CreateItemInquiryReservation] = itemReservationForm.fill(
    CreateItemReservation(
      rec.siteId, rec.itemId.id,
      rec.submitUserName,
      rec.email,
      fields('Message)
    )
  )

  def amendItemReservationStart(inqId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val id = ItemInquiryId(inqId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)

      Ok(
        views.html.amendItemReservation(
          id,
          itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList),
          amendReservationForm(rec, fields)
        )
      )
    }
  }

  def amendItemReservation(inqId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    val id = ItemInquiryId(inqId)
    val (rec: ItemInquiry, fields: immutable.Map[Symbol, String]) = db.withConnection { implicit conn =>
      (ItemInquiry(id), ItemInquiryField(id))
    }

    itemReservationForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in ItemInquiryReserveBase.amendItemReservation." + formWithErrors + ".")
        BadRequest(views.html.amendItemReservation(id, itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), formWithErrors))
      },
      info => db.withTransaction { implicit conn =>
        info.update(id)
        Ok(
          views.html.itemReservationConfirm(
            ItemInquiry(id), ItemInquiryField(id), itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), idSubmitForm.fill(inqId)
          )
        )
      }
    )
  }

  def confirmItemInquiry(
    siteId: Long, itemId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    itemInquiryForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in ItemInquiryReserveBase.submitItemInquiry." + formWithErrors + ".")
        BadRequest(views.html.itemInquiry(itemInfo(siteId, itemId, request.acceptLanguages.toList), formWithErrors))
      },
      info => db.withConnection { implicit conn =>
        val rec: ItemInquiry = info.save(login.storeUser)
        Redirect(routes.ItemInquiryReserve.submitItemInquiryStart(rec.id.get.id))
      }
    )
  }

  def amendInquiryForm(
    rec: ItemInquiry, fields: immutable.Map[Symbol, String]
  ): Form[_ <: CreateItemInquiryReservation] = itemReservationForm.fill(
    CreateItemInquiry(
      rec.siteId, rec.itemId.id,
      rec.submitUserName,
      rec.email,
      fields('Message)
    )
  )

  def amendItemInquiryStart(inqId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val id = ItemInquiryId(inqId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)

      Ok(
        views.html.amendItemInquiry(
          id,
          itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList),
          amendInquiryForm(rec, fields)
        )
      )
    }
  }

  def amendItemInquiry(inqId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    val id = ItemInquiryId(inqId)
    val (rec: ItemInquiry, fields: immutable.Map[Symbol, String]) = db.withConnection { implicit conn =>
      (ItemInquiry(id), ItemInquiryField(id))
    }

    itemInquiryForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in ItemInquiryReserveBase.amendItemInquiry." + formWithErrors + ".")
        BadRequest(
          views.html.amendItemInquiry(
            id,
            itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList),
            formWithErrors
          )
        )
      },
      info => db.withTransaction { implicit conn =>
        info.update(id)
        Ok(
          views.html.itemReservationConfirm(
            rec, fields, itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), idSubmitForm.fill(inqId)
          )
        )
      }
    )
  }

  def confirmItemReservation(
    siteId: Long, itemId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    itemReservationForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in ItemInquiryReserveBase.confirmItemReservation." + formWithErrors + ".")
        BadRequest(views.html.itemReservation(itemInfo(siteId, itemId, request.acceptLanguages.toList), formWithErrors))
      },
      info => db.withConnection { implicit conn =>
        val rec: ItemInquiry = info.save(login.storeUser)
        Redirect(routes.ItemInquiryReserve.submitItemReservationStart(rec.id.get.id))
      }
    )
  }

  def submitItemInquiryStart(inquiryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val id = ItemInquiryId(inquiryId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)
      Ok(
        views.html.itemInquiryConfirm(
          rec, fields, itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), idSubmitForm.fill(inquiryId)
        )
      )
    }
  }

  def submitItemInquiry(inquiryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    idSubmitForm.bindFromRequest.fold(
      formWithErrors => db.withConnection { implicit conn =>
        val id = ItemInquiryId(inquiryId)
        val rec = ItemInquiry(id)
        val fields = ItemInquiryField(id)
        Logger.error("Validation error in ItemInquiryReserveBase.submitItemInquiry." + formWithErrors + ".")
        BadRequest(
          views.html.itemInquiryConfirm(
            rec, fields, itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), idSubmitForm.fill(inquiryId)
          )
        )
      },
      id => db.withConnection { implicit conn =>
        if (ItemInquiry.changeStatus(ItemInquiryId(id), ItemInquiryStatus.SUBMITTED) == 0) {
          throw new Error("Record update fail id = " + id)
        }
        Redirect(routes.Application.index).flashing("message" -> Messages("itemInquirySubmit"))
      }
    )
  }

  def submitItemReservationStart(inquiryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val id = ItemInquiryId(inquiryId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)
      Ok(
        views.html.itemReservationConfirm(
          rec, fields, itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), idSubmitForm.fill(inquiryId)
        )
      )
    }
  }

  def submitItemReservation(inquiryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    idSubmitForm.bindFromRequest.fold(
      formWithErrors => db.withConnection { implicit conn =>
        val id = ItemInquiryId(inquiryId)
        val rec = ItemInquiry(id)
        val fields = ItemInquiryField(id)
        Logger.error("Validation error in ItemInquiryReserveBase.submitItemReservation." + formWithErrors + ".")
        BadRequest(
          views.html.itemReservationConfirm(
            rec, fields, itemInfo(rec.siteId, rec.itemId.id, request.acceptLanguages.toList), idSubmitForm.fill(inquiryId)
          )
        )
      },
      longId => db.withConnection { implicit conn =>
        val id: ItemInquiryId = ItemInquiryId(longId)
        val inquiry: ItemInquiry = ItemInquiry(id)
        val fields: immutable.Map[Symbol, String] = ItemInquiryField(id)

        itemInquiryMail.send(login.storeUser, inquiry, fields, localeInfoRepo.getDefault(request.acceptLanguages.toList))

        if (ItemInquiry.changeStatus(id, ItemInquiryStatus.SUBMITTED) == 0) {
          throw new Error("Record update fail id = " + id)
        }
        Redirect(routes.Application.index).flashing("message" -> Messages("itemReservationSubmit"))
      }
    )
  }
}
