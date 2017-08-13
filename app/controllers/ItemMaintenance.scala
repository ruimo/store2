package controllers

import play.api.mvc.MessagesRequestHeader
import helpers.Forms._
import javax.inject.{Inject, Singleton}

import helpers.Forms._
import play.Logger
import controllers.NeedLogin.Authenticated
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models._
import play.api.i18n.{Lang, Messages, MessagesProvider}
import models.CreateItem
import helpers.{Cache, QueryString}
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

class ChangeItem(
  val id: Long,
  val siteMap: Map[Long, Site],
  val langTable: Seq[(String, String)],
  val itemNameTableForm: Form[ChangeItemNameTable],
  val newItemNameForm: Form[ChangeItemName],
  val siteNameTable: Seq[(String, String)],
  val siteItemTable: Seq[(Site, SiteItem)],
  val newSiteItemForm: Form[ChangeSiteItem],
  val updateCategoryForm: Form[ChangeItemCategory],
  val categoryTable: Seq[(String, String)],
  val itemDescriptionTableForm: Form[ChangeItemDescriptionTable],
  val newItemDescriptionForm: Form[ChangeItemDescription],
  val itemPriceTableForm: Form[ChangeItemPriceTable],
  val newItemPriceForm: Form[ChangeItemPrice],
  val taxTable: Seq[(String, String)],
  val currencyTable: Seq[(String, String)],
  val itemInSiteTable: Seq[(String, String)],
  val itemMetadataTableForm: Form[ChangeItemMetadataTable],
  val newItemMetadataForm: Form[ChangeItemMetadata],
  val siteItemMetadataTableForm: Form[ChangeSiteItemMetadataTable],
  val newSiteItemMetadataForm: Form[CreateSiteItemMetadata],
  val siteItemTextMetadataTableForm: Form[ChangeSiteItemTextMetadataTable],
  val newSiteItemTextMetadataForm: Form[ChangeSiteItemTextMetadata],
  val itemTextMetadataTableForm: Form[ChangeItemTextMetadataTable],
  val newItemTextMetadataForm: Form[ChangeItemTextMetadata],
  val attachmentNames: Map[Int, String],
  val couponForm: Form[ChangeCoupon],
  val newSupplementalCategoryForm: Form[ChangeSupplementalCategory],
  val supplementalCategories: Seq[(Long, String)]
)

@Singleton
class ItemMaintenance @Inject() (
  cc: MessagesControllerComponents,
  implicit val localeInfoRepo: LocaleInfoRepo,
  itemPictures: ItemPictures,
  cache: Cache,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val categoryRepo: CategoryRepo,
  implicit val siteRepo: SiteRepo,
  implicit val taxRepo: TaxRepo,
  implicit val currencyRegistry: CurrencyRegistry,
  implicit val itemRepo: ItemRepo,
  implicit val itemNameRepo: ItemNameRepo,
  implicit val siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo,
  implicit val siteItemRepo: SiteItemRepo,
  implicit val itemPriceRepo: ItemPriceRepo,
  implicit val itemDescriptionRepo: ItemDescriptionRepo,
  implicit val itemPriceHistoryRepo: ItemPriceHistoryRepo,
  implicit val supplementalCategoryRepo: SupplementalCategoryRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def createChangeItem(
    id: Long,
    login: LoginSession,
    lang: Lang,
    req: MessagesRequestHeader
  )(
    siteMap: Map[Long, Site] = siteListAsMap,
    langTable: Seq[(String, String)] = localeInfoRepo.localeTable(req),
    itemNameTableForm: Form[ChangeItemNameTable] = createItemNameTable(id),
    newItemNameForm: Form[ChangeItemName] = addItemNameForm,
    siteNameTable: Seq[(String, String)] = createSiteTable(login),
    siteItemTable: Seq[(Site, SiteItem)] = createSiteItemTable(id),
    newSiteItemForm: Form[ChangeSiteItem] = addSiteItemForm,
    updateCategoryForm: Form[ChangeItemCategory] = createItemCategoryForm(id),
    categoryTable: Seq[(String, String)] = createCategoryTable(lang),
    itemDescriptionTableForm: Form[ChangeItemDescriptionTable] = createItemDescriptionTable(id),
    newItemDescriptionForm: Form[ChangeItemDescription] = addItemDescriptionForm,
    itemPriceTableForm: Form[ChangeItemPriceTable] = createItemPriceTable(id),
    newItemPriceForm: Form[ChangeItemPrice] = addItemPriceForm,
    taxTable: Seq[(String, String)] = createTaxTable(lang),
    currencyTable: Seq[(String, String)] = currencyTable,
    itemInSiteTable: Seq[(String, String)] = createSiteTable(id)(login),
    itemMetadataTableForm: Form[ChangeItemMetadataTable] = createItemMetadataTable(id),
    newItemMetadataForm: Form[ChangeItemMetadata] = addItemMetadataForm,
    siteItemMetadataTableForm: Form[ChangeSiteItemMetadataTable] = createSiteItemMetadataTable(id),
    newSiteItemMetadataForm: Form[CreateSiteItemMetadata] = addSiteItemMetadataForm,
    siteItemTextMetadataTableForm: Form[ChangeSiteItemTextMetadataTable] = createSiteItemTextMetadataTable(id),
    newSiteItemTextMetadataForm: Form[ChangeSiteItemTextMetadata] = addSiteItemTextMetadataForm,
    itemTextMetadataTableForm: Form[ChangeItemTextMetadataTable] = createItemTextMetadataTable(id),
    newItemTextMetadataForm: Form[ChangeItemTextMetadata] = addItemTextMetadataForm,
    attachmentNames: Map[Int, String] = itemPictures.retrieveAttachmentNames(id),
    couponForm: Form[ChangeCoupon] = createCouponForm(ItemId(id)),
    newSupplementalCategoryForm: Form[ChangeSupplementalCategory] = addSupplementalCategoryForm,
    supplementalCategories: Seq[(Long, String)] = createSupplementalCategories(ItemId(id), lang)
  ) = new ChangeItem(
    id,
    siteMap,
    langTable,
    itemNameTableForm,
    newItemNameForm,
    siteNameTable,
    siteItemTable,
    newSiteItemForm,
    updateCategoryForm,
    categoryTable,
    itemDescriptionTableForm,
    newItemDescriptionForm,
    itemPriceTableForm,
    newItemPriceForm,
    taxTable,
    currencyTable,
    itemInSiteTable,
    itemMetadataTableForm,
    newItemMetadataForm,
    siteItemMetadataTableForm,
    newSiteItemMetadataForm,
    siteItemTextMetadataTableForm,
    newSiteItemTextMetadataForm,
    itemTextMetadataTableForm,
    newItemTextMetadataForm,
    attachmentNames,
    couponForm,
    newSupplementalCategoryForm,
    supplementalCategories
  )

  val HideNewlyCreatedItem: () => Boolean = cache.config(
    _.getOptional[Boolean]("hideNewlyCreatedItem").getOrElse(false)
  )

  val ItemDescriptionSize: () => Int = cache.config(
    _.getOptional[Int]("itemDescription.size").getOrElse(2048)
  )

  val StoreOwnerCanModifyAllItemProperties: () => Boolean = cache.config(
    _.getOptional[Boolean]("storeOwnerCanModifyAllItemProperties").getOrElse(false)
  )

  def isPermitted(login: LoginSession): Boolean =
    login.isSuperUser || login.isSiteOwner && StoreOwnerCanModifyAllItemProperties()

  val createItemForm = Form(
    mapping(
      "langId" -> longNumber,
      "siteId" -> longNumber,
      "categoryId" -> longNumber,
      "itemName" -> text.verifying(nonEmpty, maxLength(255)),
      "taxId" -> longNumber,
      "currencyId" -> longNumber,
      "price" -> bigDecimal.verifying(min(BigDecimal(0))),
      "listPrice" -> optional(bigDecimal.verifying(min(BigDecimal(0)))),
      "costPrice" -> bigDecimal.verifying(min(BigDecimal(0))),
      "description" -> text.verifying(maxLength(ItemDescriptionSize())),
      "isCoupon" ->boolean
    ) (CreateItem.apply)(CreateItem.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.itemMaintenance())
    }
  }

  def startCreateNewItem = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        implicit val lang = request.acceptLanguages.head
        Ok(
          views.html.admin.createNewItem(
            createItemForm, localeInfoRepo.localeTable, categoryRepo.tableForDropDown,
            if (login.isSuperUser) siteRepo.tableForDropDown
            else {
              val site = siteRepo(login.siteUser.get.siteId)
              List((site.id.get.toString, site.name))
            },
            taxRepo.tableForDropDown, currencyRegistry.tableForDropDown
          )
        )
      }
    }
  }

  def createNewItem = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      createItemForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.createNewItem." + formWithErrors + ".")
          implicit val lang = request.acceptLanguages.head
          BadRequest(
            db.withConnection { implicit conn =>
              views.html.admin.createNewItem(
                formWithErrors, localeInfoRepo.localeTable, categoryRepo.tableForDropDown,
                siteRepo.tableForDropDown, taxRepo.tableForDropDown, currencyRegistry.tableForDropDown
              )
            }
          )
        },
        newItem => {
          db.withConnection { implicit conn =>
            newItem.save(HideNewlyCreatedItem())
          }
          Redirect(
            routes.ItemMaintenance.startCreateNewItem
          ).flashing("message" -> Messages("itemIsCreated"))
        }
      )
    }
  }

  def editItem(
    qs: List[String], pgStart: Int, pgSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      val queryStr = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
      db.withConnection { implicit conn =>
        login.role match {
          case Buyer => throw new Error("Logic error.")
          case AnonymousBuyer => throw new Error("Logic error.")
          case EntryUserBuyer => throw new Error("Logic error.")

          case SuperUser =>
            implicit val langs = request.acceptLanguages.toList
            val list = itemRepo.listForMaintenance(
              siteUser = None, locale = localeInfoRepo.getDefault, queryString = queryStr, page = pgStart,
              pageSize = pgSize, orderBy = OrderBy(orderBySpec)
            )

            Ok(views.html.admin.editItem(queryStr, list))

          case SiteOwner(siteOwner) =>
            implicit val langs = request.acceptLanguages.toList
            val list = itemRepo.listForMaintenance(
              siteUser = Some(siteOwner), locale = localeInfoRepo.getDefault, queryString = queryStr, page = pgStart,
              pageSize = pgSize, orderBy = OrderBy(orderBySpec)
            )
            Ok(views.html.admin.editItem(queryStr, list))
        }
      }
    }
  }

  def siteListAsMap: Map[Long, Site] = {
    db.withConnection { implicit conn => {
      siteRepo.listAsMap
    }}
  }

  def createTaxTable(implicit lang: Lang): Seq[(String, String)] = db.withConnection { implicit conn =>
    taxRepo.tableForDropDown
  }

  def currencyTable: Seq[(String, String)] = db.withConnection { implicit conn =>
    currencyRegistry.tableForDropDown
  }

  def itemMetadataTable(implicit req: MessagesRequestHeader): Seq[(String, String)] = ItemNumericMetadataType.all.map {
    e => (e.ordinal.toString, Messages("itemNumericMetadata" + e.toString))
  }

  def itemTextMetadataTable(implicit req: MessagesRequestHeader): Seq[(String, String)] = ItemTextMetadataType.all.map {
    e => (e.ordinal.toString, Messages("itemTextMetadata" + e.toString))
  }

  def siteItemMetadataTable(implicit req: MessagesRequestHeader): Seq[(String, String)] = SiteItemNumericMetadataType.all.map {
    e => (e.ordinal.toString, Messages("siteItemMetadata" + e.toString))
  }

  def siteItemTextMetadataTable(implicit req: MessagesRequestHeader): Seq[(String, String)] = SiteItemTextMetadataType.all.map {
    e => (e.ordinal.toString, Messages("siteItemTextMetadata" + e.toString))
  }

  def startChangeItem(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.changeItem(
        createChangeItem(id, login, request.acceptLanguages.head, request)(), this
      ))
    }
  }

  val changeItemNameForm = Form(
    mapping(
      "itemNames" -> seq(
        mapping(
          "localeId" -> longNumber,
          "itemName" -> text.verifying(nonEmpty, maxLength(255))
        ) (ChangeItemName.apply)(ChangeItemName.unapply)
      )
    ) (ChangeItemNameTable.apply)(ChangeItemNameTable.unapply)
  )

  val changeItemMetadataForm = Form(
    mapping(
      "itemMetadatas" -> seq(
        mapping(
          "metadataType" -> number,
          "metadata" -> longNumber
        ) (ChangeItemMetadata.apply)(ChangeItemMetadata.unapply)
      )
    ) (ChangeItemMetadataTable.apply)(ChangeItemMetadataTable.unapply)
  )

  val changeItemTextMetadataForm = Form(
    mapping(
      "itemMetadatas" -> seq(
        mapping(
          "metadataType" -> number,
          "metadata" -> text
        ) (ChangeItemTextMetadata.apply)(ChangeItemTextMetadata.unapply)
      )
    ) (ChangeItemTextMetadataTable.apply)(ChangeItemTextMetadataTable.unapply)
  )

  val changeSiteItemMetadataForm = Form(
    mapping(
      "siteItemMetadatas" -> seq(
        mapping(
          "id" -> longNumber,
          "siteId" -> longNumber,
          "metadataType" -> number,
          "metadata" -> longNumber,
          "validUntil" -> instant("yyyy-MM-dd HH:mm:ss")
        ) (ChangeSiteItemMetadata.apply)(ChangeSiteItemMetadata.unapply)
      )
    ) (ChangeSiteItemMetadataTable.apply)(ChangeSiteItemMetadataTable.unapply)
  )

  val changeSiteItemTextMetadataForm = Form(
    mapping(
      "siteItemTextMetadatas" -> seq(
        mapping(
          "siteId" -> longNumber,
          "metadataType" -> number,
          "metadata" -> text
        ) (ChangeSiteItemTextMetadata.apply)(ChangeSiteItemTextMetadata.unapply)
      )
    ) (ChangeSiteItemTextMetadataTable.apply)(ChangeSiteItemTextMetadataTable.unapply)
  )

  val addItemNameForm = Form(
    mapping(
      "localeId" -> longNumber,
      "itemName" -> text.verifying(nonEmpty, maxLength(255))
    ) (ChangeItemName.apply)(ChangeItemName.unapply)
  )

  val addItemMetadataForm = Form(
    mapping(
      "metadataType" -> number,
      "metadata" -> longNumber
    ) (ChangeItemMetadata.apply)(ChangeItemMetadata.unapply)
  )

  val addItemTextMetadataForm = Form(
    mapping(
      "metadataType" -> number,
      "metadata" -> text
    ) (ChangeItemTextMetadata.apply)(ChangeItemTextMetadata.unapply)
  )

  val addSiteItemMetadataForm = Form(
    mapping(
      "siteId" -> longNumber,
      "metadataType" -> number,
      "metadata" -> longNumber,
      "validUntil" -> instant("yyyy-MM-dd HH:mm:ss")
    ) (CreateSiteItemMetadata.apply)(CreateSiteItemMetadata.unapply)
  ).bind(
    Map(
      "validUntil" -> Until.EverStr
    )
  ).discardingErrors

  val addSiteItemTextMetadataForm = Form(
    mapping(
      "siteId" -> longNumber,
      "metadataType" -> number,
      "metadata" -> text
    ) (ChangeSiteItemTextMetadata.apply)(ChangeSiteItemTextMetadata.unapply)
  )

  val couponForm = Form(
    mapping(
      "isCoupon" ->boolean
    ) (ChangeCoupon.apply)(ChangeCoupon.unapply)
  )

  val addSupplementalCategoryForm = Form(
    mapping(
      "categoryId" -> longNumber
    ) (ChangeSupplementalCategory.apply)(ChangeSupplementalCategory.unapply)
  )

  def createItemNameTable(id: Long): Form[ChangeItemNameTable] = {
    db.withConnection { implicit conn => {
      val itemNames = itemNameRepo.list(ItemId(id)).values.map {
        n => ChangeItemName(n.localeId, n.name)
      }.toSeq

      changeItemNameForm.fill(ChangeItemNameTable(itemNames))
    }}
  }

  def createItemMetadataTable(id: Long): Form[ChangeItemMetadataTable] = {
    db.withConnection { implicit conn => {
      val itemMetadatas = ItemNumericMetadata.allById(ItemId(id)).values.map {
        n => ChangeItemMetadata(n.metadataType.ordinal, n.metadata)
      }.toSeq

      changeItemMetadataForm.fill(ChangeItemMetadataTable(itemMetadatas))
    }}
  }

  def createItemTextMetadataTable(id: Long): Form[ChangeItemTextMetadataTable] = {
    db.withConnection { implicit conn => {
      val itemMetadatas = ItemTextMetadata.allById(ItemId(id)).values.map {
        n => ChangeItemTextMetadata(n.metadataType.ordinal, n.metadata)
      }.toSeq

      changeItemTextMetadataForm.fill(ChangeItemTextMetadataTable(itemMetadatas))
    }}
  }

  def createSiteItemMetadataTable(id: Long): Form[ChangeSiteItemMetadataTable] = {
    db.withConnection { implicit conn => {
      val itemMetadata = siteItemNumericMetadataRepo.allById(ItemId(id)).map {
        n => ChangeSiteItemMetadata(n.id.get, n.siteId, n.metadataType.ordinal, n.metadata, n.validUntil)
      }
      changeSiteItemMetadataForm.fill(ChangeSiteItemMetadataTable(itemMetadata))
    }}
  }

  def createSiteItemTextMetadataTable(id: Long): Form[ChangeSiteItemTextMetadataTable] = {
    db.withConnection { implicit conn => {
      val itemMetadata = SiteItemTextMetadata.allById(ItemId(id)).values.map {
        n => ChangeSiteItemTextMetadata(n.siteId, n.metadataType.ordinal, n.metadata)
      }.toSeq

      changeSiteItemTextMetadataForm.fill(ChangeSiteItemTextMetadataTable(itemMetadata))
    }}
  }

  def changeItemName(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeUser(isPermitted(login)) {
      changeItemNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeItemName." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                itemNameTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newItem => {
          db.withConnection { implicit conn =>
            newItem.update(id)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(id)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def addItemName(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeUser(isPermitted(login)) {
      addItemNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addItemName." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newItemNameForm = formWithErrors
              ),
              this
            )
          )
        },
        newItem => {
          try {
            newItem.add(id)

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newItemNameForm = addItemNameForm.fill(newItem).withError("localeId", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def removeItemName(itemId: Long, localeId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeUser(isPermitted(login)) {
      db.withConnection { implicit conn =>
        itemNameRepo.remove(ItemId(itemId), localeId)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  def removeItemMetadata(itemId: Long, metadataType: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeUser(isPermitted(login)) {
      db.withConnection { implicit conn =>
        ItemNumericMetadata.remove(ItemId(itemId), metadataType)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  def removeItemTextMetadata(itemId: Long, metadataType: Int) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        ItemTextMetadata.remove(ItemId(itemId), metadataType)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  def removeSiteItemMetadata(itemId: Long, id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        siteItemNumericMetadataRepo.remove(id)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  def removeSiteItemTextMetadata(
    itemId: Long, siteId: Long, metadataType: Int
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        SiteItemTextMetadata.remove(ItemId(itemId), siteId, metadataType)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  val addSiteItemForm = Form(
    mapping(
      "siteId" -> longNumber
    ) (ChangeSiteItem.apply)(ChangeSiteItem.unapply)
  )

  def createSiteTable(implicit login: LoginSession): Seq[(String, String)] = {
    db.withConnection { implicit conn => {
      siteRepo.tableForDropDown
    }}
  }

  def createSiteTable(id: Long)(implicit login: LoginSession): Seq[(String, String)] = {
    db.withConnection { implicit conn => {
      siteRepo.tableForDropDown(id)
    }}
  }

  def createSiteItemTable(itemId: Long): Seq[(Site, SiteItem)] = {
    db.withConnection { implicit conn => {
      siteItemRepo.list(ItemId(itemId))
    }}
  }

  def addSiteItem(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      addSiteItemForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addSiteItem." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newSiteItemForm = formWithErrors
              ),
              this
            )
          )
        },
        newSiteItem => {
          try {
            db.withConnection { implicit conn =>
              newSiteItem.add(id)
            }

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newSiteItemForm = addSiteItemForm.fill(newSiteItem).withError("siteId", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def removeSiteItem(itemId: Long, siteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        siteItemRepo.remove(ItemId(itemId), siteId)
        itemPriceRepo.remove(ItemId(itemId), siteId)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  val updateCategoryForm = Form(
    mapping(
      "categoryId" -> longNumber
    ) (ChangeItemCategory.apply)(ChangeItemCategory.unapply)
  )

  def createItemCategoryForm(id: Long): Form[ChangeItemCategory] = {
    db.withConnection { implicit conn => {
      val item = itemRepo(id)
      updateCategoryForm.fill(ChangeItemCategory(item.categoryId))
    }}
  }

  def createCouponForm(id: ItemId): Form[ChangeCoupon] = {
    db.withConnection { implicit conn => {
      couponForm.fill(ChangeCoupon(Coupon.isCoupon(id)))
    }}
  }

  def createCategoryTable(implicit lang: Lang): Seq[(String, String)] = {
    db.withConnection { implicit conn => {
      categoryRepo.tableForDropDown
    }}
  }

  def updateItemAsCoupon(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      couponForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.updateItemAsItem." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                couponForm = formWithErrors
              ),
              this
            )
          )
        },
        newIsCoupon => {
          db.withConnection { implicit conn =>
            newIsCoupon.update(ItemId(id))
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(id)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def updateItemCategory(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      updateCategoryForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.updateItemCategory." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                updateCategoryForm = formWithErrors
              ),
              this
            )
          )
        },
        newItemCategory => {
          db.withConnection { implicit conn =>
            newItemCategory.update(id)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(id)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  val changeItemDescriptionForm = Form(
    mapping(
      "itemDescriptions" -> seq(
        mapping(
          "siteId" -> longNumber,
          "localeId" -> longNumber,
          "itemDescription" -> text.verifying(nonEmpty, maxLength(ItemDescriptionSize()))
        ) (ChangeItemDescription.apply)(ChangeItemDescription.unapply)
      )
    ) (ChangeItemDescriptionTable.apply)(ChangeItemDescriptionTable.unapply)
  )

  val addItemDescriptionForm = Form(
    mapping(
      "siteId" -> longNumber,
      "localeId" -> longNumber,
      "itemDescription" -> text.verifying(nonEmpty, maxLength(ItemDescriptionSize()))
    ) (ChangeItemDescription.apply)(ChangeItemDescription.unapply)
  )

  def createItemDescriptionTable(id: Long): Form[ChangeItemDescriptionTable] = {
    db.withConnection { implicit conn => {
      val itemDescriptions = itemDescriptionRepo.list(ItemId(id)).map {
        n => ChangeItemDescription(n._1, n._2.id, n._3.description)
      }.toSeq

      changeItemDescriptionForm.fill(ChangeItemDescriptionTable(itemDescriptions))
    }}
  }

  def changeItemDescription(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeItemDescriptionForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeItem." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                itemDescriptionTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newItem => {
          db.withTransaction { implicit conn =>
            newItem.update(id)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(id)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def addItemDescription(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      addItemDescriptionForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeItem." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newItemDescriptionForm = formWithErrors
              ),
              this
            )
          )
        },
        newItem => {
          try {
            newItem.add(id)

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newItemDescriptionForm = addItemDescriptionForm
                      .fill(newItem)
                      .withError("localeId", "unique.constraint.violation")
                      .withError("siteId", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def removeItemDescription(
    siteId: Long, itemId: Long, localeId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        itemDescriptionRepo.remove(siteId, ItemId(itemId), localeId)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  val changeItemPriceForm = Form(
    mapping(
      "itemPrices" -> seq(
        mapping(
          "siteId" -> longNumber,
          "itemPriceId" -> longNumber,
          "itemPriceHistoryId" -> longNumber,
          "taxId" -> longNumber,
          "currencyId" -> longNumber,
          "itemPrice" -> bigDecimal.verifying(min(BigDecimal(0))),
          "listPrice" -> optional(bigDecimal.verifying(min(BigDecimal(0)))),
          "costPrice" -> bigDecimal.verifying(min(BigDecimal(0))),
          "validUntil" -> instant("yyyy-MM-dd HH:mm:ss")
        ) (ChangeItemPrice.apply)(ChangeItemPrice.unapply)
      )
    ) (ChangeItemPriceTable.apply)(ChangeItemPriceTable.unapply)
  )

  val addItemPriceForm = Form(
    mapping(
      "siteId" -> longNumber,
      "itemPriceId" -> ignored(0L),
      "itemPriceHistoryId" -> ignored(0L),
      "taxId" -> longNumber,
      "currencyId" -> longNumber,
      "itemPrice" -> bigDecimal.verifying(min(BigDecimal(0))),
      "listPrice" -> optional(bigDecimal.verifying(min(BigDecimal(0)))),
      "costPrice" -> bigDecimal.verifying(min(BigDecimal(0))),
      "validUntil" -> instant("yyyy-MM-dd HH:mm:ss")
    ) (ChangeItemPrice.apply)(ChangeItemPrice.unapply)
  )

  def createItemPriceTable(itemId: Long): Form[ChangeItemPriceTable] = {
    db.withConnection { implicit conn => {
      val histories = itemPriceHistoryRepo.listByItemId(ItemId(itemId)).map {
        e => ChangeItemPrice(
          e._1.siteId, e._2.itemPriceId, e._2.id.get, e._2.taxId,
          e._2.currency.id, e._2.unitPrice, e._2.listPrice, e._2.costPrice, e._2.validUntil
        )
      }.toSeq

      changeItemPriceForm.fill(ChangeItemPriceTable(histories))
    }}
  }

  def changeItemPrice(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeItemPriceForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeItemPrice." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                itemPriceTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newPrice => {
          db.withConnection { implicit conn =>
            newPrice.update
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(id)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def addItemPrice(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      addItemPriceForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addItemPrice " + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newItemPriceForm = formWithErrors
              ),
              this
            )
          )
        },
        newHistory => {
          try {
            db.withConnection { implicit conn =>
              newHistory.add(id)
            }
            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newItemPriceForm = addItemPriceForm
                      .fill(newHistory)
                      .withError("siteId", "unique.constraint.violation")
                      .withError("validUntil", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def removeItemPrice(
    itemId: Long, siteId: Long, itemPriceHistoryId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        itemPriceHistoryRepo.remove(ItemId(itemId), siteId, itemPriceHistoryId)
      }

      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      )
    }
  }

  def changeItemMetadata(itemId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeItemMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeItemMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                itemId, login, request.acceptLanguages.head, request
              )(
                itemMetadataTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          db.withTransaction { implicit conn =>
            newMetadata.update(itemId)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(itemId)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def changeItemTextMetadata(itemId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeItemTextMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeItemTextMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                itemId, login, request.acceptLanguages.head, request
              )(
                itemTextMetadataTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          db.withTransaction { implicit conn =>
            newMetadata.update(itemId)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(itemId)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def changeSiteItemMetadata(itemId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeSiteItemMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeSiteItemMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                itemId, login, request.acceptLanguages.head, request
              )(
                siteItemMetadataTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          db.withConnection { implicit conn =>
            newMetadata.update(itemId)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(itemId)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def changeSiteItemTextMetadata(itemId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      changeSiteItemTextMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.changeSiteItemTextMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                itemId, login, request.acceptLanguages.head, request
              )(
                siteItemTextMetadataTableForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          db.withConnection { implicit conn =>
            newMetadata.update(itemId)
          }
          Redirect(
            routes.ItemMaintenance.startChangeItem(itemId)
          ).flashing("message" -> Messages("itemIsUpdated"))
        }
      )
    }
  }

  def addItemMetadata(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      addItemMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addItemMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newItemMetadataForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          try {
            newMetadata.add(id)

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newItemMetadataForm = addItemMetadataForm
                      .fill(newMetadata)
                      .withError("metadataType", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def addItemTextMetadata(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      addItemTextMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addItemTextMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newItemTextMetadataForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          try {
            newMetadata.add(id)

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newItemTextMetadataForm = addItemTextMetadataForm
                      .fill(newMetadata)
                      .withError("metadataType", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def addSiteItemMetadata(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeAdmin(login) {
      addSiteItemMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addSiteItemMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newSiteItemMetadataForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          try {
            db.withConnection { implicit conn =>
              newMetadata.add(id)
            }

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newSiteItemMetadataForm = addSiteItemMetadataForm
                      .fill(newMetadata)
                      .withError("metadataType", "unique.constraint.violation")
                      .withError("validUntil", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def addSiteItemTextMetadata(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    implicit val lang = request.acceptLanguages.head
    NeedLogin.assumeUser(isPermitted(login)) {
      addSiteItemTextMetadataForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addSiteItemTextMetadata." + formWithErrors + ".")
          BadRequest(
            views.html.admin.changeItem(
              createChangeItem(
                id, login, request.acceptLanguages.head, request
              )(
                newSiteItemTextMetadataForm = formWithErrors
              ),
              this
            )
          )
        },
        newMetadata => {
          try {
            db.withConnection { implicit conn =>
              newMetadata.add(id)
            }

            Redirect(
              routes.ItemMaintenance.startChangeItem(id)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.changeItem(
                  createChangeItem(
                    id, login, request.acceptLanguages.head, request
                  )(
                    newSiteItemTextMetadataForm = addSiteItemTextMetadataForm
                      .fill(newMetadata)
                      .withError("metadataType", "unique.constraint.violation")
                  ),
                  this
                )
              )
            }
          }
        }
      )
    }
  }

  def addSupplementalCategory(itemId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeUser(isPermitted(login)) {
      addSupplementalCategoryForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ItemMaintenance.addSupplementalCategory " + formWithErrors + ".")
          Redirect(
            routes.ItemMaintenance.startChangeItem(itemId)
          ).flashing("errorMessage" -> Messages("unknownError"))
        },
        changeCategory => {
          db.withConnection { implicit conn =>
            try {
              changeCategory.add(itemId)
            }
            catch {
              case e: UniqueConstraintException => {
                Redirect(
                  routes.ItemMaintenance.startChangeItem(itemId)
                ).flashing("errorMessage" -> Messages("unique.constraint.violation"))
              }
              case e: MaxRowCountExceededException => {
                Redirect(
                  routes.ItemMaintenance.startChangeItem(itemId)
                ).flashing("errorMessage" -> Messages("maxSupplementalCategoryCountSucceeded"))
              }
            }

            Redirect(
              routes.ItemMaintenance.startChangeItem(itemId)
            ).flashing("message" -> Messages("itemIsUpdated"))
          }
        }
      )
    }
  }

  def createSupplementalCategories(itemId: ItemId, lang: Lang): Seq[(Long, String)] = {
    db.withConnection { implicit conn =>
      supplementalCategoryRepo.byItemWithName(itemId, lang).map { t =>
        (t._1.categoryId, t._2.name)
      }
    }
  }

  def removeSupplementalCategory(itemId: Long, categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeUser(isPermitted(login)) {
      db.withConnection { implicit conn =>
        supplementalCategoryRepo.remove(ItemId(itemId), categoryId)
      }
      Redirect(
        routes.ItemMaintenance.startChangeItem(itemId)
      ).flashing("message" -> Messages("itemIsUpdated"))
    }
  }
}
