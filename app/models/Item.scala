package models

import javax.inject.Singleton

import anorm._
import anorm.SqlParser

import scala.language.postfixOps
import collection.immutable
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import javax.inject.Inject

import org.joda.time.DateTime

import annotation.tailrec
import helpers.QueryString
import helpers.{CategoryCodeSearchCondition, CategoryIdSearchCondition}
import play.api.Play
import play.api.db.Database

case class ItemId(id: Long) extends AnyVal

case class Item(id: Option[ItemId] = None, categoryId: Long)

case class ItemName(localeId: Long, itemId: ItemId, name: String)

case class ItemDescription(localeId: Long, itemId: ItemId, siteId: Long, description: String)

case class ItemPrice(id: Option[Long] = None, siteId: Long, itemId: ItemId)

case class ItemPriceHistory(
  id: Option[Long] = None,
  itemPriceId: Long, 
  taxId: Long, 
  currency: CurrencyInfo,
  unitPrice: BigDecimal,
  listPrice: Option[BigDecimal],
  costPrice: BigDecimal,
  validUntil: LocalDateTime
)

case class ItemNumericMetadata(
  id: Option[Long] = None, itemId: ItemId, metadataType: ItemNumericMetadataType, metadata: Long
)

case class ItemTextMetadata(
  id: Option[Long] = None, itemId: ItemId, metadataType: ItemTextMetadataType, metadata: String
)

case class SiteItem(itemId: ItemId, siteId: Long, created: Long)

case class SiteItemNumericMetadata(
  id: Option[Long] = None, itemId: ItemId, siteId: Long, metadataType: SiteItemNumericMetadataType, metadata: Long,
  validUntil: LocalDateTime
)

case class SiteItemTextMetadata(
  id: Option[Long] = None, itemId: ItemId, siteId: Long, metadataType: SiteItemTextMetadataType, metadata: String
)

@Singleton
class ItemRepo @Inject() (
  localeInfoRepo: LocaleInfoRepo,
  taxRepo: TaxRepo,
  siteItemRepo: SiteItemRepo,
  siteRepo: SiteRepo,
  itemNameRepo: ItemNameRepo,
  itemPriceRepo: ItemPriceRepo,
  itemPriceHistoryRepo: ItemPriceHistoryRepo,
  itemDescriptionRepo: ItemDescriptionRepo,
  siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo,
  db: Database,
  currencyRegistry: CurrencyRegistry
) {
  val ItemListDefaultOrderBy = OrderBy("item_name.item_name", Asc)
  val ItemListQueryColumnsToAdd = Play.current.configuration.getString("item.list.query.columns.add").get

  val simple = {
    SqlParser.get[Option[Long]]("item.item_id") ~
    SqlParser.get[Long]("item.category_id") map {
      case id~categoryId => Item(id.map {ItemId.apply}, categoryId)
    }
  }

  val itemParser = simple~itemNameRepo.simple~itemDescriptionRepo.simple~itemPriceRepo.simple~siteRepo.simple map {
    case item~itemName~itemDescription~itemPrice~site => (
      item, itemName, itemDescription, itemPrice, site
    )
  }

  val itemListParser = simple~itemNameRepo.simple~itemDescriptionRepo.simple~itemPriceHistoryRepo.simple~siteRepo.simple map {
    case item~itemName~itemDescription~itemPrice~site => (
      item, itemName, itemDescription, itemPrice, site
    )
  }

  val itemListForMaintenanceParser = 
    simple~(itemNameRepo.simple ?)~(itemDescriptionRepo.simple ?)~(itemPriceHistoryRepo.simple ?)~(siteRepo.simple ?) map {
      case item~itemName~itemDescription~itemPrice~site => (
        item, itemName, itemDescription, itemPrice, site
      )
    }

  def apply(id: Long): Item = db.withConnection { implicit conn =>
    SQL(
      "select * from item where item_id = {id}"
    ).on(
      'id -> id
    ).as(simple.single)
  }

  def itemInfo(
    id: Long, locale: LocaleInfo,
    now: Long = System.currentTimeMillis
  ): (Item, ItemName, ItemDescription, Site, ItemPriceHistory, Map[ItemNumericMetadataType, ItemNumericMetadata]) = {
    db.withConnection { implicit conn =>
      val item = SQL(
        """
        select * from item
        inner join item_name on item.item_id = item_name.item_id
        inner join item_description on item.item_id = item_description.item_id
        inner join item_price on item.item_id = item_price.item_id
        inner join site_item on item.item_id = site_item.item_id
        inner join site on site_item.site_id = site.site_id
        where item.item_id= {id}
        and item_name.locale_id = {localeId}
        and item_description.locale_id = {localeId}
        order by item_name.item_name
        """
      ).on(
        'id -> id,
        'localeId -> locale.id
      ).as(
        itemParser.single
      )

      val itemId = item._1.id.get
      val itemPriceId = item._4.id.get
      val priceHistory = itemPriceHistoryRepo.at(itemPriceId, now)
      val metadata = ItemNumericMetadata.allById(itemId)

      (item._1, item._2, item._3, item._5, priceHistory, metadata)
    }
  }

  def createNew(category: Category): Item = db.withConnection { implicit conn =>
    createNew(category.id.get)
  }

  def createNew(categoryId: Long): Item = db.withConnection { implicit conn =>
    SQL(
      """
      insert into item values (
        (select nextval('item_seq')), {categoryId}
      )
      """
    ).on(
      'categoryId -> categoryId
    ).executeUpdate()

    val itemId = SQL("select currval('item_seq')").as(SqlParser.scalar[Long].single)

    Item(Some(ItemId(itemId)), categoryId)
  }

  def listForMaintenance(
    siteUser: Option[SiteUser] = None, locale: LocaleInfo, queryString: QueryString,
    page: Int = 0, pageSize: Int = 10,
    now: Long = System.currentTimeMillis,
    orderBy: OrderBy = ItemListDefaultOrderBy
  ): PagedRecords[(
    Item, Option[ItemName], Option[ItemDescription], Option[Site], Option[ItemPriceHistory],
    Map[ItemNumericMetadataType, ItemNumericMetadata],
    Option[Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]],
    Map[ItemTextMetadataType, ItemTextMetadata]
  )] = db.withConnection { implicit conn =>
    val sqlBody = """
      left join item_name on item.item_id = item_name.item_id and item_name.locale_id = {localeId}
      left join item_description on item.item_id = item_description.item_id and item_description.locale_id = {localeId}
      left join item_price on item.item_id = item_price.item_id 
      left join item_price_history on item_price.item_price_id = item_price_history.item_price_id
      left join site_item on item.item_id = site_item.item_id and item_price.site_id = site_item.site_id
      left join site on site_item.site_id = site.site_id
      where 1 = 1
    """ + 
    siteUser.map { "  and site.site_id = " + _.siteId }.getOrElse("") +
    """
      and (
        (item_price_history.item_price_history_id is null)
        or item_price_history.item_price_history_id = (
          select iph.item_price_history_id
          from item_price_history iph
          where
            iph.item_price_id = item_price.item_price_id and
            iph.valid_until > {now}
            order by iph.valid_until
            limit 1
        )
      )
    """ +
    createQueryConditionSql(
      queryString, CategoryIdSearchCondition.Null, CategoryCodeSearchCondition.Null, None
    )

    val columns = if (ItemListQueryColumnsToAdd.isEmpty) "*" else "*, " + ItemListQueryColumnsToAdd

    val sql = SQL(
      s"select $columns from item $sqlBody order by $orderBy limit {pageSize} offset {offset}"
    )

    val list = applyQueryString(queryString, sql)
      .on(
        'localeId -> locale.id,
        'pageSize -> pageSize,
        'offset -> page * pageSize,
        'now -> new Timestamp(now)
      ).as(
        itemListForMaintenanceParser *
      ).map {e => {
        val itemId = e._1.id.get
        val metadata = ItemNumericMetadata.allById(itemId)
        val textMetadata = ItemTextMetadata.allById(itemId)
        val siteMetadata = e._5.map {md => siteItemNumericMetadataRepo.all(md.id.get, itemId)}

        (e._1, e._2, e._3, e._5, e._4, metadata, siteMetadata, textMetadata)
      }}

    val countSql = SQL(
      "select count(*) from item" + sqlBody
    )

    val count = applyQueryString(queryString, countSql)
      .on(
        'localeId -> locale.id,
        'now -> new Timestamp(now)
      ).as(
        SqlParser.scalar[Long].single
      )

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, list)
  }

  // Do not pass user input directly into orderBy argument. That will
  // cause SQL injection vulnerability.
  def list(
    siteUser: Option[SiteUser] = None,
    locale: LocaleInfo, queryString: QueryString,
    category: CategoryIdSearchCondition = CategoryIdSearchCondition.Null,
    categoryCodes: CategoryCodeSearchCondition = CategoryCodeSearchCondition.Null,
    siteId: Option[Long] = None,
    page: Int = 0, pageSize: Int = 10,
    now: Long = System.currentTimeMillis,
    orderBy: OrderBy = ItemListDefaultOrderBy
  ): PagedRecords[(
    Item, ItemName, ItemDescription, Site, ItemPriceHistory,
    Map[ItemNumericMetadataType, ItemNumericMetadata],
    Map[SiteItemNumericMetadataType, SiteItemNumericMetadata],
    Map[ItemTextMetadataType, ItemTextMetadata],
    Map[SiteItemTextMetadataType, SiteItemTextMetadata]
  )] = db.withConnection { implicit conn =>
    val sqlBody = """
      inner join item_name on item.item_id = item_name.item_id
      inner join item_description on item.item_id = item_description.item_id
      inner join item_price on item.item_id = item_price.item_id 
      inner join item_price_history on item_price.item_price_id = item_price_history.item_price_id
      inner join site_item on item.item_id = site_item.item_id and item_price.site_id = site_item.site_id
      inner join site on site_item.site_id = site.site_id
      where item_name.locale_id = {localeId}
    """ + 
    siteUser.map { "  and site.site_id = " + _.siteId }.getOrElse("") +
    """
      and 0 = (case
        (
          select metadata
          from site_item_numeric_metadata as sinm
          where item.item_id = sinm.item_id
          and site.site_id = sinm.site_id
          and sinm.metadata_type = 
          """ + SiteItemNumericMetadataType.HIDE.ordinal +
          """
          and sinm.valid_until > {now}
          order by sinm.valid_until
          limit 1
        ) when 1 then 1 when 0 then 0 else 0 end
      )
      and item_description.locale_id = {localeId}
      and item_price_history.item_price_history_id = (
        select iph.item_price_history_id
        from item_price_history iph
        where
          iph.item_price_id = item_price.item_price_id and
          iph.valid_until > {now}
          order by iph.valid_until
          limit 1
      )
    """ +
    createQueryConditionSql(queryString, category, categoryCodes, siteId)

    val columns = if (ItemListQueryColumnsToAdd.isEmpty) "*" else "*, " + ItemListQueryColumnsToAdd

    val sql = SQL(
      s"select $columns from item $sqlBody order by $orderBy limit {pageSize} offset {offset}"
    )

    val list = applyQueryString(queryString, sql)
      .on(
        'localeId -> locale.id,
        'pageSize -> pageSize,
        'offset -> page * pageSize,
        'now -> new Timestamp(now)
      ).as(
        itemListParser *
      ).map {e => {
        val itemId = e._1.id.get
        val metadata = ItemNumericMetadata.allById(itemId)
        val textMetadata = ItemTextMetadata.allById(itemId)
        val siteMetadata = siteItemNumericMetadataRepo.all(e._5.id.get, itemId)
        val siteItemTextMetadata = SiteItemTextMetadata.all(e._5.id.get, itemId)

        (e._1, e._2, e._3, e._5, e._4, metadata, siteMetadata, textMetadata, siteItemTextMetadata)
      }}

    val countSql = SQL(
      "select count(*) from item" + sqlBody
    )

    val count = applyQueryString(queryString, countSql)
      .on(
        'localeId -> locale.id,
        'now -> new Timestamp(now)
      ).as(
        SqlParser.scalar[Long].single
      )

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, list)
  }

  def createQueryConditionSql(
    q: QueryString, category: CategoryIdSearchCondition, 
    categoryCodes: CategoryCodeSearchCondition, siteId: Option[Long]
  ): String = {
    val buf = new StringBuilder

    @tailrec def createQueryByQueryString(idx: Int): String =
      if (idx < q.size) {
        buf.append(
          f"and (item_name.item_name like {query$idx%d} or item_description.description like {query$idx%d}) "
        )
        createQueryByQueryString(idx + 1)
      }
      else buf.toString

    createQueryByQueryString(0) +
    category.condition.map {
      cids => {
        val cid = cids.mkString(",")
        f"""
          and (
            item.category_id in (
              select descendant from category_path where ancestor in ($cid)
            )
            or exists (
              select descendant from category_path
              where ancestor in (
                select category_id from supplemental_category where item_id = item.item_id
              )
              and descendant in ($cid)
            )
          )
        """
      }
    }.mkString("") +
    categoryCodes.condition.map {
      ccs => {
        val cc = ccs.map {"'" + _ + "'"}.mkString(",")
        f"""
          and (
            item.category_id in (
              select descendant from category_path where ancestor in (
                select category_id from category where category_code in ($cc)
              )
            )
            or exists (
              select descendant from category_path
              where ancestor in (
                select category_id from supplemental_category where item_id = item.item_id
              )
              and descendant in (
                select category_id from category where category_code in ($cc)
              )
            )
          )
        """
      }
    }.mkString("") +
    siteId.map {
      sid => f"and site.site_id = $sid "
    }.getOrElse("")
  }

  def applyQueryString(queryString: QueryString, sql: SimpleSql[Row]): SimpleSql[Row] = {
    @tailrec
    def applyQueryString(idx: Int, queryString: QueryString, sql: SimpleSql[Row]): SimpleSql[Row] = 
      queryString.toList match {
        case List() => sql
        case head::tail => applyQueryString(
          idx + 1, QueryString(tail), sql.on(Symbol("query" + idx) -> ("%" + head + "%"))
        )
      }

    applyQueryString(0, queryString, sql)
  }

  def listBySite(
    site: Site, locale: LocaleInfo, queryString: String, page: Int = 0, pageSize: Int = 10,
    now: Long = System.currentTimeMillis
  ): Seq[
    (Item, ItemName, 
     ItemDescription,
     ItemPrice,
     ItemPriceHistory,
     Map[ItemNumericMetadataType, ItemNumericMetadata])
  ] = db.withConnection { implicit conn =>
    listBySiteId(site.id.get, locale, queryString, page, pageSize, now)
  }

  def listBySiteId(
    siteId: Long, locale: LocaleInfo, queryString: String, page: Int = 0, pageSize: Int = 10,
    now: Long = System.currentTimeMillis
  ): Seq[
    (Item, ItemName, ItemDescription, ItemPrice, ItemPriceHistory, Map[ItemNumericMetadataType, ItemNumericMetadata])
  ]  = db.withConnection { implicit conn =>
    SQL(
      """
      select * from item
      inner join item_name on item.item_id = item_name.item_id
      inner join item_description on item.item_id = item_description.item_id
      inner join item_price on item.item_id = item_price.item_id
      inner join site_item on item.item_id = site_item.item_id and item_price.site_id = site_item.site_id
      inner join site on site.site_id = site_item.site_id
      where item_name.locale_id = {localeId}
      and item_description.locale_id = {localeId}
      and item_description.site_id = {siteId}
      and item_price.site_id = {siteId}
      and site_item.site_id = {siteId}
      and (item_name.item_name like {query} or item_description.description like {query})
      order by item_name.item_name
      limit {pageSize} offset {offset}
      """
    ).on(
      'localeId -> locale.id,
      'siteId -> siteId,
      'query -> ("%" + queryString + "%"),
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      itemParser *
    ).map { e => {
      val itemId = e._1.id.get
      val itemPriceId = e._4.id.get
      val priceHistory = itemPriceHistoryRepo.at(itemPriceId, now)
      val metadata = ItemNumericMetadata.allById(itemId)
      (e._1, e._2, e._3, e._4, priceHistory, metadata)
    }}
  }

  def createItem(prototype: CreateItem, hide: Boolean) {
    db.withConnection { implicit conn =>
      val item = createNew(prototype.categoryId)
      val name = itemNameRepo.createNew(item, Map(localeInfoRepo(prototype.localeId) -> prototype.itemName))
      val site = siteRepo(prototype.siteId)
      val desc = itemDescriptionRepo.createNew(item, site, prototype.description)
      val price = itemPriceRepo.createNew(item, site)
      val tax = taxRepo(prototype.taxId)
      val priceHistory = itemPriceHistoryRepo.createNew(price, tax, currencyRegistry(prototype.currencyId), prototype.price, prototype.listPrice, prototype.costPrice, Until.EverLocalDateTime)
      val siteItem = siteItemRepo.createNew(site, item)
      if (prototype.isCoupon)
        Coupon.updateAsCoupon(item.id.get)
      if (hide) {
        siteItemNumericMetadataRepo.createNew(site.id.get, item.id.get, SiteItemNumericMetadataType.HIDE, 1)
      }
    }
  }

  def changeCategory(itemId: ItemId, categoryId: Long) {
    db.withConnection { implicit conn =>
      SQL(
        "update item set category_id = {categoryId} where item_id = {itemId}"
      ).on(
        'itemId -> itemId.id,
        'categoryId -> categoryId
      ).executeUpdate()
    }
  }
}

@Singleton
class ItemNameRepo @Inject() (
  localeInfoRepo: LocaleInfoRepo
) {
  val simple = {
    SqlParser.get[Long]("item_name.locale_id") ~
    SqlParser.get[Long]("item_name.item_id") ~
    SqlParser.get[String]("item_name.item_name") map {
      case localeId~itemId~name => ItemName(localeId, ItemId(itemId), name)
    }
  }

  def createNew(
    item: Item, names: Map[LocaleInfo, String]
  )(implicit conn: Connection): Map[LocaleInfo, ItemName] = {
    names.transform { (k, v) => {
      SQL(
        """
        insert into item_name (item_name_id, locale_id, item_id, item_name)
        values (
          (select nextval('item_name_seq')),
          {localeId}, {itemId}, {itemName}
        )
        """
      ).on(
        'localeId -> k.id,
        'itemId -> item.id.get.id,
        'itemName -> v
      ).executeUpdate()
      
      ItemName(k.id, item.id.get, v)
    }}
  }

  def list(item: Item)(implicit conn: Connection): Map[LocaleInfo, ItemName] = list(item.id.get)

  def list(itemId: ItemId)(implicit conn: Connection): Map[LocaleInfo, ItemName] = {
    SQL(
      "select * from item_name where item_id = {itemId}"
    ).on(
      'itemId -> itemId.id
    ).as(simple *).map { e =>
      localeInfoRepo(e.localeId) -> e
    }.toMap
  }

  def add(itemId: ItemId, localeId: Long, itemName: String)(implicit conn: Connection) {
    SQL(
      """
      insert into item_name (item_name_id, item_id, locale_id, item_name)
      values (
        (select nextval('item_name_seq')),
        {itemId}, {localeId}, {itemName}
      )
      """
    ).on(
      'itemId -> itemId.id,
      'localeId -> localeId,
      'itemName -> itemName
    ).executeUpdate()
  }

  def remove(id: Long)(implicit conn: Connection) {
    SQL(
      "delete from item_name where item_name_id = {id}"
    )
    .on(
      'id -> id
    ).executeUpdate()
  }

  def remove(itemId: ItemId, localeId: Long)(implicit conn: Connection): Long = SQL(
    """
    delete from item_name
    where item_id = {itemId}
    and locale_id = {localeId}
    and (select count(*) from item_name where item_id = {itemId}) > 1
    """
  )
  .on(
    'itemId -> itemId.id,
    'localeId -> localeId
  ).executeUpdate()

  def update(itemId: ItemId, localeId: Long, itemName: String)(implicit conn: Connection) {
    SQL(
      """
      update item_name set item_name = {itemName}
      where item_id = {itemId} and locale_id = {localeId}
      """
    ).on(
      'itemName -> itemName,
      'itemId -> itemId.id,
      'localeId -> localeId
    ).executeUpdate()
  }
}

@Singleton
class ItemDescriptionRepo @Inject() (
  localeInfoRepo: LocaleInfoRepo
) {
  val simple = {
    SqlParser.get[Long]("item_description.locale_id") ~
    SqlParser.get[Long]("item_description.item_id") ~
    SqlParser.get[Long]("item_description.site_id") ~
    SqlParser.get[String]("item_description.description") map {
      case localeId~itemId~siteId~description => ItemDescription(localeId, ItemId(itemId), siteId, description)
    }
  }

  def createNew(
    item: Item, site: Site, description: String
  )(implicit conn: Connection): ItemDescription = {
    SQL(
      """
      insert into item_description (item_description_id, locale_id, item_id, site_id, description)
      values (
        (select nextval('item_description_seq')),
        {localeId}, {itemId}, {siteId}, {description}
      )
      """
    ).on(
      'localeId -> site.localeId,
      'itemId -> item.id.get.id,
      'siteId -> site.id.get,
      'description -> description
    ).executeUpdate()

    ItemDescription(site.localeId, item.id.get, site.id.get, description)
  }

  def list(item: Item)(implicit conn: Connection): Seq[(Long, LocaleInfo, ItemDescription)] = list(item.id.get)

  def list(itemId: ItemId)(implicit conn: Connection): Seq[(Long, LocaleInfo, ItemDescription)] =
    SQL(
      """
      select * from item_description
      inner join site on site.site_id = item_description.site_id
      where item_id = {itemId}
      order by site.site_name, item_description.locale_id
      """
    ).on(
      'itemId -> itemId.id
    ).as(simple *).map { e =>
      (e.siteId, localeInfoRepo(e.localeId), e)
    }.toSeq

  def add(siteId: Long, itemId: ItemId, localeId: Long, itemDescription: String)(implicit conn: Connection) {
    SQL(
      """
      insert into item_description (item_description_id, site_id, item_id, locale_id, description)
      values (
        (select nextval('item_description_seq')),
        {siteId}, {itemId}, {localeId}, {itemDescription}
      )
      """
    ).on(
      'siteId -> siteId,
      'itemId -> itemId.id,
      'localeId -> localeId,
      'itemDescription -> itemDescription
    ).executeUpdate()
  }

  def remove(siteId: Long, itemId: ItemId, localeId: Long)(implicit conn: Connection) {
    SQL(
      """
      delete from item_description
      where site_id = {siteId}
      and item_id = {itemId}
      and locale_id = {localeId}
      and (select count(*) from item_description where item_id = {itemId}) > 1
      """
    )
    .on(
      'siteId -> siteId,
      'itemId -> itemId.id,
      'localeId -> localeId
    ).executeUpdate()
  }

  def update(siteId: Long, itemId: ItemId, localeId: Long, itemDescription: String)(implicit conn: Connection) {
    SQL(
      """
      update item_description set description = {itemDescription}
      where site_id = {siteId} and item_id = {itemId} and locale_id = {localeId}
      """
    ).on(
      'itemDescription -> itemDescription,
      'siteId -> siteId,
      'itemId -> itemId.id,
      'localeId -> localeId
    ).executeUpdate()
  }
}

@Singleton
class ItemPriceRepo @Inject() (
  itemPriceRepo: ItemPriceRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("item_price.item_price_id") ~
    SqlParser.get[Long]("item_price.site_id") ~
    SqlParser.get[Long]("item_price.item_id") map {
      case id~siteId~itemId => ItemPrice(id, siteId, ItemId(itemId))
    }
  }

  def createNew(item: Item, site: Site)(implicit conn: Connection): ItemPrice =
    add(item.id.get, site.id.get)

  def add(itemId: ItemId, siteId: Long)(implicit conn: Connection): ItemPrice = {
    SQL(
      """
      insert into item_price (item_price_id, site_id, item_id)
      values ((select nextval('item_price_seq')), {siteId}, {itemId})
      """
    ).on(
      'siteId -> siteId,
      'itemId -> itemId.id
    ).executeUpdate()

    val itemPriceId = SQL("select currval('item_price_seq')").as(SqlParser.scalar[Long].single)

    ItemPrice(Some(itemPriceId), siteId, itemId)
  }

  def get(site: Site, item: Item)(implicit conn: Connection): Option[ItemPrice] = {
    SQL(
      "select * from item_price where item_id = {itemId} and site_id = {siteId}"
    ).on(
      'itemId -> item.id.get.id,
      'siteId -> site.id.get
    ).as(
      itemPriceRepo.simple.singleOpt
    )
  }

  def remove(itemId: ItemId, siteId: Long)(implicit conn: Connection) {
    SQL(
      "delete from item_price where item_id = {itemId} and site_id = {siteId}"
    ).on(
      'itemId -> itemId.id,
      'siteId -> siteId
    ).executeUpdate()
  }
}

class ItemPriceHistoryRepo @Inject() (
  currencyRegistry: CurrencyRegistry,
  itemPriceRepo: ItemPriceRepo,
  db: Database
) {
  val simple = {
    SqlParser.get[Option[Long]]("item_price_history.item_price_history_id") ~
    SqlParser.get[Long]("item_price_history.item_price_id") ~
    SqlParser.get[Long]("item_price_history.tax_id") ~
    SqlParser.get[Long]("item_price_history.currency_id") ~
    SqlParser.get[java.math.BigDecimal]("item_price_history.unit_price") ~
    SqlParser.get[Option[java.math.BigDecimal]]("item_price_history.list_price") ~
    SqlParser.get[java.math.BigDecimal]("item_price_history.cost_price") ~
    SqlParser.get[java.time.LocalDateTime]("item_price_history.valid_until") map {
      case id~itemPriceId~taxId~currencyId~unitPrice~listPrice~costPrice~validUntil
        => ItemPriceHistory(id, itemPriceId, taxId, currencyRegistry(currencyId), unitPrice, listPrice.map(BigDecimal.apply), costPrice, validUntil)
    }
  }

  def createNew(
    itemPrice: ItemPrice, tax: Tax, currency: CurrencyInfo, unitPrice: BigDecimal, listPrice: Option[BigDecimal] = None, costPrice: BigDecimal, validUntil: LocalDateTime
  ): ItemPriceHistory = db.withConnection { implicit conn =>
    SQL(
      """
      insert into item_price_history(
        item_price_history_id, item_price_id, tax_id, currency_id, unit_price, list_price, cost_price, valid_until
      ) values (
        (select nextval('item_price_history_seq')),
        {itemPriceId}, {taxId}, {currencyId}, {unitPrice}, {listPrice}, {costPrice}, {validUntil}
      )
      """
    ).on(
      'itemPriceId -> itemPrice.id.get,
      'taxId -> tax.id.get,
      'currencyId -> currency.id,
      'unitPrice -> unitPrice.bigDecimal,
      'listPrice -> listPrice.map(_.bigDecimal),
      'costPrice -> costPrice.bigDecimal,
      'validUntil -> validUntil
    ).executeUpdate()

    val id = SQL("select currval('item_price_history_seq')").as(SqlParser.scalar[Long].single)

    ItemPriceHistory(Some(id), itemPrice.id.get, tax.id.get, currency, unitPrice, listPrice, costPrice, validUntil)
  }

  def update(
    id: Long, taxId: Long, currencyId: Long, unitPrice: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal, validUntil: LocalDateTime
  ) {
    db.withConnection { implicit conn =>
      SQL(
        """
        update item_price_history
        set tax_id = {taxId},
        currency_id = {currencyId},
        unit_price = {unitPrice},
        list_price = {listPrice},
        cost_price = {costPrice},
        valid_until = {validUntil}
        where item_price_history_id = {id}
        """
      ).on(
        'taxId -> taxId,
        'currencyId -> currencyId,
        'unitPrice -> unitPrice.bigDecimal,
        'listPrice -> listPrice.map(_.bigDecimal),
        'costPrice -> costPrice.bigDecimal,
        'validUntil -> validUntil,
        'id -> id
      ).executeUpdate()
    }
  }

  def add(
    itemId: ItemId, siteId: Long, taxId: Long, currencyId: Long, 
    unitPrice: BigDecimal, listPrice: Option[BigDecimal], costPrice: BigDecimal, validUntil: LocalDateTime
  ) {
    db.withConnection { implicit conn =>
      val priceId = SQL(
        """
        select item_price_id from item_price
        where site_id = {siteId}
        and item_id = {itemId}
        """
      ).on(
        'siteId -> siteId,
        'itemId -> itemId.id
      ).as(SqlParser.scalar[Long].single)

      SQL(
        """
        insert into item_price_history
        (item_price_history_id, item_price_id, tax_id, currency_id, unit_price, list_price, cost_price, valid_until)
        values (
          (select nextval('item_price_history_seq')),
          {itemPriceId}, {taxId}, {currencyId}, {unitPrice}, {listPrice}, {costPrice}, {validUntil}
        )
        """
      ).on(
        'itemPriceId -> priceId,
        'taxId -> taxId,
        'currencyId -> currencyId,
        'unitPrice -> unitPrice.bigDecimal,
        'listPrice -> listPrice.map(_.bigDecimal),
        'costPrice -> costPrice.bigDecimal,
        'validUntil -> validUntil
      ).executeUpdate()
    }
  }

  def list(itemPrice: ItemPrice)(implicit conn: Connection): Seq[ItemPriceHistory] = db.withConnection { implicit conn =>
    SQL(
      "select * from item_price_history where item_price_id = {itemPriceId}"
    ).on(
      'itemPriceId -> itemPrice.id.get
    ).as(
      simple *
    )
  }

  def at(
    itemPriceId: Long, now: Long = System.currentTimeMillis
  ): ItemPriceHistory = db.withConnection { implicit conn =>
    getAt(itemPriceId, now).get
  }

  def getAt(
    itemPriceId: Long, now: Long = System.currentTimeMillis
  ): Option[ItemPriceHistory] = db.withConnection { implicit conn =>
    SQL(
      """
      select * from item_price_history
      where item_price_id = {itemPriceId}
      and {now} < valid_until
      order by valid_until
      limit 1
      """
    ).on(
      'itemPriceId -> itemPriceId,
      'now -> new java.sql.Timestamp(now)
    ).as(
      simple.singleOpt
    )
  }

  def atBySiteAndItem(
    siteId: Long, itemId: ItemId, now: Long = System.currentTimeMillis
  ): ItemPriceHistory = db.withConnection { implicit conn =>
    SQL(
      """
      select * from item_price_history
      where item_price_id = (
        select item_price_id from item_price
        where item_price.item_id = {itemId} and item_price.site_id = {siteId}
      )
      and {now} < valid_until
      order by valid_until
      limit 1
      """
    ).on(
      'itemId -> itemId.id,
      'siteId -> siteId,
      'now -> new java.sql.Timestamp(now)
    ).as(
      simple.singleOpt
    ).getOrElse(
      throw new RuntimeException(
        "No rows are found in item_price. itemId = " + itemId + ", siteId = " + siteId + ", time = " + new java.sql.Date(now)
      )
    )
  }

  val withItemPrice = itemPriceRepo.simple~simple map {
    case price~priceHistory => (price, priceHistory)
  }

  def listByItemId(itemId: ItemId): Seq[(ItemPrice, ItemPriceHistory)] = db.withConnection { implicit conn =>
    SQL(
      """
      select * from item_price_history
      inner join item_price on item_price_history.item_price_id = item_price.item_price_id
      inner join site on site.site_id = item_price.site_id
      where item_price.item_id = {itemId}
      order by site.site_name, item_price_history.valid_until
      """
    ).on(
      'itemId -> itemId.id
    ).as(
      withItemPrice *
    ).toSeq
  }

  def remove(itemId: ItemId, siteId: Long, id: Long) {
    db.withConnection { implicit conn =>
      SQL(
        """
        delete from item_price_history
        where item_price_history_id = {id}
        and (
          select count(*) from item_price_history
          inner join item_price on item_price_history.item_price_id = item_price.item_price_id
          where item_price.item_id = {itemId}
          and item_price.site_id = {siteId}
        ) > 1
        """
      ).on(
        'id -> id,
        'itemId -> itemId.id,
        'siteId -> siteId
      ).executeUpdate()
    }
  }
}

object ItemNumericMetadata {
  val simple = {
    SqlParser.get[Option[Long]]("item_numeric_metadata.item_numeric_metadata_id") ~
    SqlParser.get[Long]("item_numeric_metadata.item_id") ~
    SqlParser.get[Int]("item_numeric_metadata.metadata_type") ~
    SqlParser.get[Long]("item_numeric_metadata.metadata") map {
      case id~itemId~metadata_type~metadata =>
        ItemNumericMetadata(id, ItemId(itemId), ItemNumericMetadataType.byIndex(metadata_type), metadata)
    }
  }

  def createNew(
    item: Item, metadataType: ItemNumericMetadataType, metadata: Long
  )(implicit conn: Connection): ItemNumericMetadata = add(item.id.get, metadataType, metadata)

  def add(
    itemId: ItemId, metadataType: ItemNumericMetadataType, metadata: Long
  )(implicit conn: Connection): ItemNumericMetadata = {
    SQL(
      """
      insert into item_numeric_metadata(item_numeric_metadata_id, item_id, metadata_type, metadata)
      values (
        (select nextval('item_numeric_metadata_seq')),
        {itemId}, {metadataType}, {metadata}
      )
      """
    ).on(
      'itemId -> itemId.id,
      'metadataType -> metadataType.ordinal,
      'metadata -> metadata
    ).executeUpdate()

    val id = SQL("select currval('item_numeric_metadata_seq')").as(SqlParser.scalar[Long].single)

    ItemNumericMetadata(Some(id), itemId, ItemNumericMetadataType.byIndex(metadataType.ordinal), metadata)
  }

  def apply(
    item: Item, metadataType: ItemNumericMetadataType
  )(implicit conn: Connection): ItemNumericMetadata = SQL(
    """
    select * from item_numeric_metadata
    where item_id = {itemId}
    and metadata_type = {metadataType}
    """
  ).on(
    'itemId -> item.id.get.id,
    'metadataType -> metadataType.ordinal
  ).as(
    ItemNumericMetadata.simple.single
  )

  def all(item: Item)(implicit conn: Connection): Map[ItemNumericMetadataType, ItemNumericMetadata] = allById(item.id.get)

  def allById(itemId: ItemId)(implicit conn: Connection): Map[ItemNumericMetadataType, ItemNumericMetadata] = SQL(
    "select * from item_numeric_metadata where item_id = {itemId} "
  ).on(
    'itemId -> itemId.id
  ).as(
    ItemNumericMetadata.simple *
  ).foldLeft(new immutable.HashMap[ItemNumericMetadataType, ItemNumericMetadata]) {
    (map, e) => map.updated(e.metadataType, e)
  }

  def update(itemId: ItemId, metadataType: ItemNumericMetadataType, metadata: Long)(implicit conn: Connection) {
    SQL(
      """
      update item_numeric_metadata set metadata = {metadata}
      where item_id = {itemId} and metadata_type = {metadataType}
      """
    ).on(
      'metadata -> metadata,
      'itemId -> itemId.id,
      'metadataType -> metadataType.ordinal
    ).executeUpdate()
  }

  def remove(itemId: ItemId, metadataType: Int)(implicit conn: Connection) {
    SQL(
      """
      delete from item_numeric_metadata
      where item_id = {itemId}
      and metadata_type = {metadataType}
      """
    ).on(
      'itemId -> itemId.id,
      'metadataType -> metadataType
    ).executeUpdate()
  }
}

object ItemTextMetadata {
  val simple = {
    SqlParser.get[Option[Long]]("item_text_metadata.item_text_metadata_id") ~
    SqlParser.get[Long]("item_text_metadata.item_id") ~
    SqlParser.get[Int]("item_text_metadata.metadata_type") ~
    SqlParser.get[String]("item_text_metadata.metadata") map {
      case id~itemId~metadata_type~metadata =>
        ItemTextMetadata(id, ItemId(itemId), ItemTextMetadataType.byIndex(metadata_type), metadata)
    }
  }

  def createNew(
    item: Item, metadataType: ItemTextMetadataType, metadata: String
  )(implicit conn: Connection): ItemTextMetadata = add(item.id.get, metadataType, metadata)

  def add(
    itemId: ItemId, metadataType: ItemTextMetadataType, metadata: String
  )(implicit conn: Connection): ItemTextMetadata = {
    SQL(
      """
      insert into item_text_metadata(item_text_metadata_id, item_id, metadata_type, metadata)
      values (
        (select nextval('item_text_metadata_seq')),
        {itemId}, {metadataType}, {metadata}
      )
      """
    ).on(
      'itemId -> itemId.id,
      'metadataType -> metadataType.ordinal,
      'metadata -> metadata
    ).executeUpdate()

    val id = SQL("select currval('item_text_metadata_seq')").as(SqlParser.scalar[Long].single)

    ItemTextMetadata(Some(id), itemId, ItemTextMetadataType.byIndex(metadataType.ordinal), metadata)
  }

  def apply(
    item: Item, metadataType: ItemTextMetadataType
  )(implicit conn: Connection): ItemTextMetadata = SQL(
    """
    select * from item_text_metadata
    where item_id = {itemId}
    and metadata_type = {metadataType}
    """
  ).on(
    'itemId -> item.id.get.id,
    'metadataType -> metadataType.ordinal
  ).as(
    ItemTextMetadata.simple.single
  )

  def all(item: Item)(implicit conn: Connection): Map[ItemTextMetadataType, ItemTextMetadata] = allById(item.id.get)

  def allById(itemId: ItemId)(implicit conn: Connection): Map[ItemTextMetadataType, ItemTextMetadata] = SQL(
    "select * from item_text_metadata where item_id = {itemId} "
  ).on(
    'itemId -> itemId.id
  ).as(
    ItemTextMetadata.simple *
  ).foldLeft(new immutable.HashMap[ItemTextMetadataType, ItemTextMetadata]) {
    (map, e) => map.updated(e.metadataType, e)
  }

  def update(itemId: ItemId, metadataType: ItemTextMetadataType, metadata: String)(implicit conn: Connection) {
    SQL(
      """
      update item_text_metadata set metadata = {metadata}
      where item_id = {itemId} and metadata_type = {metadataType}
      """
    ).on(
      'metadata -> metadata,
      'itemId -> itemId.id,
      'metadataType -> metadataType.ordinal
    ).executeUpdate()
  }

  def remove(itemId: ItemId, metadataType: Int)(implicit conn: Connection) {
    SQL(
      """
      delete from item_text_metadata
      where item_id = {itemId}
      and metadata_type = {metadataType}
      """
    ).on(
      'itemId -> itemId.id,
      'metadataType -> metadataType
    ).executeUpdate()
  }
}

@Singleton
class SiteItemNumericMetadataRepo @Inject() (
  implicit val siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("site_item_numeric_metadata.site_item_numeric_metadata_id") ~
    SqlParser.get[Long]("site_item_numeric_metadata.item_id") ~
    SqlParser.get[Long]("site_item_numeric_metadata.site_id") ~
    SqlParser.get[Int]("site_item_numeric_metadata.metadata_type") ~
    SqlParser.get[Long]("site_item_numeric_metadata.metadata") ~
    SqlParser.get[java.time.LocalDateTime]("site_item_numeric_metadata.valid_until") map {
      case id~itemId~siteId~metadataType~metadata~validUntil =>
        SiteItemNumericMetadata(id, ItemId(itemId), siteId, SiteItemNumericMetadataType.byIndex(metadataType), metadata, validUntil)
    }
  }

  def createNew(
    siteId: Long, itemId: ItemId, metadataType: SiteItemNumericMetadataType, metadata: Long,
    validUntil: LocalDateTime = Until.EverLocalDateTime
  )(implicit conn: Connection): SiteItemNumericMetadata = {
    SQL(
      """
      insert into site_item_numeric_metadata(
        site_item_numeric_metadata_id, site_id, item_id, metadata_type, metadata, valid_until
      ) values (
        (select nextval('site_item_numeric_metadata_seq')),
        {siteId}, {itemId}, {metadataType}, {metadata}, {validUntil}
      )
      """
    ).on(
      'siteId -> siteId,
      'itemId -> itemId.id,
      'metadataType -> metadataType.ordinal,
      'metadata -> metadata,
      'validUntil -> validUntil
    ).executeUpdate()

    val id = SQL("select currval('site_item_numeric_metadata_seq')").as(SqlParser.scalar[Long].single)

    SiteItemNumericMetadata(Some(id), itemId, siteId, metadataType, metadata, validUntil)
  }

  def at(
    siteId: Long, itemId: ItemId, metadataType: SiteItemNumericMetadataType, now: Long = System.currentTimeMillis
  )(implicit conn: Connection): SiteItemNumericMetadata = SQL(
    """
    select * from site_item_numeric_metadata
    where site_id = {siteId} and item_id = {itemId}
    and metadata_type = {metadataType}
    and {now} < valid_until
    order by valid_until
    limit 1
    """
  ).on(
    'siteId -> siteId,
    'itemId -> itemId.id,
    'metadataType -> metadataType.ordinal,
    'now -> new java.sql.Timestamp(now)
  ).as(
    siteItemNumericMetadataRepo.simple.single
  )

  def all(
    siteId: Long, itemId: ItemId, now: Long = System.currentTimeMillis
  )(implicit conn: Connection): Map[SiteItemNumericMetadataType, SiteItemNumericMetadata] = SQL(
    """
    select * from site_item_numeric_metadata t1
    where site_id = {siteId} and item_id = {itemId} and
    valid_until = (
      select valid_until from site_item_numeric_metadata t2
      where t2.site_id = {siteId} and t2.item_id = {itemId} and t2.metadata_type = t1.metadata_type
      and {now} < t2.valid_until
      order by t2.valid_until
      limit 1
    )
    """
  ).on(
    'siteId -> siteId,
    'itemId -> itemId.id,
    'now -> new java.sql.Timestamp(now)
  ).as(
    (siteItemNumericMetadataRepo.simple) *
  ).foldLeft(new immutable.HashMap[SiteItemNumericMetadataType, SiteItemNumericMetadata]) {
    (map, e) => map.updated(e.metadataType, e)
  }

  def update(
    id: Long, metadata: Long, validUntil: LocalDateTime = Until.EverLocalDateTime
  )(implicit conn: Connection) {
    SQL(
      """
      update site_item_numeric_metadata set metadata = {metadata}, valid_until = {validUntil}
      where site_item_numeric_metadata_id = {id}
      """
    ).on(
      'id -> id,
      'metadata -> metadata,
      'validUntil -> validUntil
    ).executeUpdate()
  }

  def remove(id: Long)(implicit conn: Connection) {
    SQL(
      """
      delete from site_item_numeric_metadata
      where site_item_numeric_metadata_id = {id}
      """
    ).on(
      'id -> id
    ).executeUpdate()
  }

  def allById(
    itemId: ItemId
  )(implicit conn: Connection): immutable.Seq[SiteItemNumericMetadata] = SQL(
    "select * from site_item_numeric_metadata where item_id = {itemId} order by metadata_type, valid_until"
  ).on(
    'itemId -> itemId.id
  ).as(
    siteItemNumericMetadataRepo.simple *
  )
}

object SiteItemTextMetadata {
  val simple = {
    SqlParser.get[Option[Long]]("site_item_text_metadata.site_item_text_metadata_id") ~
    SqlParser.get[Long]("site_item_text_metadata.item_id") ~
    SqlParser.get[Long]("site_item_text_metadata.site_id") ~
    SqlParser.get[Int]("site_item_text_metadata.metadata_type") ~
    SqlParser.get[String]("site_item_text_metadata.metadata") map {
      case id~itemId~siteId~metadataType~metadata =>
        SiteItemTextMetadata(id, ItemId(itemId), siteId, SiteItemTextMetadataType.byIndex(metadataType), metadata)
    }
  }

  def createNew(
    siteId: Long, itemId: ItemId, metadataType: SiteItemTextMetadataType, metadata: String
  )(implicit conn: Connection): SiteItemTextMetadata = {
    SQL(
      """
      insert into site_item_text_metadata(
        site_item_text_metadata_id, site_id, item_id, metadata_type, metadata
      ) values (
        (select nextval('site_item_text_metadata_seq')),
        {siteId}, {itemId}, {metadataType}, {metadata}
      )
      """
    ).on(
      'siteId -> siteId,
      'itemId -> itemId.id,
      'metadataType -> metadataType.ordinal,
      'metadata -> metadata
    ).executeUpdate()

    val id = SQL("select currval('site_item_text_metadata_seq')").as(SqlParser.scalar[Long].single)

    SiteItemTextMetadata(Some(id), itemId, siteId, metadataType, metadata)
  }

  def add(
    itemId: ItemId, siteId: Long, metadataType: SiteItemTextMetadataType, metadata: String
  )(implicit conn: Connection): SiteItemTextMetadata = {
    SQL(
      """
      insert into site_item_text_metadata(
        site_item_text_metadata_id, item_id, site_id, metadata_type, metadata
      ) values (
        (select nextval('site_item_text_metadata_seq')),
        {itemId}, {siteId}, {metadataType}, {metadata}
      )
      """
    ).on(
      'itemId -> itemId.id,
      'siteId -> siteId,
      'metadataType -> metadataType.ordinal,
      'metadata -> metadata
    ).executeUpdate()

    val id = SQL("select currval('site_item_text_metadata_seq')").as(SqlParser.scalar[Long].single)

    SiteItemTextMetadata(Some(id), itemId, siteId, SiteItemTextMetadataType.byIndex(metadataType.ordinal), metadata)
  }

  def apply(
    siteId: Long, itemId: ItemId, metadataType: SiteItemTextMetadataType
  )(implicit conn: Connection): SiteItemTextMetadata = SQL(
    """
    select * from site_item_text_metadata
    where site_id = {siteId} and item_id = {itemId}
    and metadata_type = {metadataType}
    """
  ).on(
    'siteId -> siteId,
    'itemId -> itemId.id,
    'metadataType -> metadataType.ordinal
  ).as(
    SiteItemTextMetadata.simple.single
  )

  def all(
    siteId: Long, itemId: ItemId
  )(implicit conn: Connection): Map[SiteItemTextMetadataType, SiteItemTextMetadata] = SQL(
    "select * from site_item_text_metadata where site_id = {siteId} and item_id = {itemId}"
  ).on(
    'siteId -> siteId,
    'itemId -> itemId.id
  ).as(
    SiteItemTextMetadata.simple *
  ).foldLeft(new immutable.HashMap[SiteItemTextMetadataType, SiteItemTextMetadata]) {
    (map, e) => map.updated(e.metadataType, e)
  }

  def update(
    itemId: ItemId, siteId: Long, metadataType: SiteItemTextMetadataType, metadata: String
  )(implicit conn: Connection) {
    SQL(
      """
      update site_item_text_metadata set metadata = {metadata}
      where item_id = {itemId} and site_id = {siteId} and metadata_type = {metadataType}
      """
    ).on(
      'metadata -> metadata,
      'itemId -> itemId.id,
      'siteId -> siteId,
      'metadataType -> metadataType.ordinal
    ).executeUpdate()
  }

  def remove(itemId: ItemId, siteId: Long, metadataType: Int)(implicit conn: Connection) {
    SQL(
      """
      delete from site_item_text_metadata
      where item_id = {itemId} and site_id = {siteId} and metadata_type = {metadataType}
      """
    ).on(
      'itemId -> itemId.id,
      'siteId -> siteId,
      'metadataType -> metadataType
    ).executeUpdate()
  }

  def allById(
    itemId: ItemId
  )(implicit conn: Connection): Map[(Long, SiteItemTextMetadataType), SiteItemTextMetadata] = SQL(
    "select * from site_item_text_metadata where item_id = {itemId} "
  ).on(
    'itemId -> itemId.id
  ).as(
    SiteItemTextMetadata.simple *
  ).foldLeft(new immutable.HashMap[(Long, SiteItemTextMetadataType), SiteItemTextMetadata]) {
    (map, e) => map.updated((e.siteId, e.metadataType), e)
  }
}

@Singleton
class SiteItemRepo @Inject() (
  itemNameRepo: ItemNameRepo,
  siteRepo: SiteRepo,
  siteItemRepo: SiteItemRepo,
  itemPriceRepo: ItemPriceRepo
) {
  val simple = {
    SqlParser.get[Long]("site_item.item_id") ~
    SqlParser.get[Long]("site_item.site_id") ~
    SqlParser.get[java.util.Date]("site_item.created") map {
      case itemId~siteId~created => SiteItem(ItemId(itemId), siteId, created.getTime)
    }
  }

  val withSiteAndItemName = siteRepo.simple ~ itemNameRepo.simple map {
    case site~itemName => (site, itemName)
  }

  val withSite = siteRepo.simple ~ siteItemRepo.simple map {
    case site~siteItem => (site, siteItem)
  }

  def list(itemId: ItemId)(implicit conn: Connection): Seq[(Site, SiteItem)] =
    SQL(
      """
      select * from site_item
      inner join site on site_item.site_id = site.site_id
      where site_item.item_id = {itemId}
      order by site_item.site_id, site_item.item_id
      """
    ).on(
      'itemId -> itemId.id
    ).as(
      withSite *
    )

  def createNew(site: Site, item: Item)(implicit conn: Connection): SiteItem = add(item.id.get, site.id.get)

  def add(
    itemId: ItemId, siteId: Long, created: Long = System.currentTimeMillis
  )(implicit conn: Connection): SiteItem = {
    SQL(
      "insert into site_item (item_id, site_id, created) values ({itemId}, {siteId}, {created})"
    ).on(
      'itemId -> itemId.id,
      'siteId -> siteId,
      'created -> new java.sql.Timestamp(created)
    ).executeUpdate()

    SiteItem(itemId, siteId, created)
  }

  def remove(itemId: ItemId, siteId: Long)(implicit conn: Connection) {
    SQL(
      "delete from site_item where item_id = {itemId} and site_id = {siteId}"
    ).on(
      'itemId -> itemId.id,
      'siteId -> siteId
    ).executeUpdate()
  }

  def getWithSiteAndItem(
    siteId: Long, itemId: ItemId, locale: LocaleInfo
  )(
    implicit conn: Connection
  ): Option[(Site, ItemName)] = SQL(
    """
    select * from site_item si
    inner join site s on s.site_id = si.site_id and s.deleted = FALSE
    inner join item_name itn on itn.item_id = si.item_id and itn.locale_id = {locale}
    where si.site_id = {siteId} and si.item_id = {itemId}
    """
  ).on(
    'locale -> locale.id,
    'siteId -> siteId,
    'itemId -> itemId.id
  ).as(
    withSiteAndItemName.singleOpt
  )
}

