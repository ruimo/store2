package models

import anorm._
import anorm.SqlParser

import scala.language.postfixOps
import collection.immutable.{HashMap, IntMap, LongMap, TreeMap}
import java.sql.Connection
import java.time.Instant
import javax.inject.{Inject, Singleton}

import collection.mutable
import collection.mutable.ListBuffer

case class ShippingTotalEntry (
  site: Site,
  itemClass: Long,
  shippingBox: ShippingBox,
  shippingFee: ShippingFee,
  itemQuantity: Int,
  boxQuantity: Int,
  boxUnitPrice: BigDecimal,
  boxUnitCostPrice: Option[BigDecimal],
  boxTaxInfo: TaxHistory
)(
  implicit taxRepo: TaxRepo
) {
  lazy val boxTotal = boxUnitPrice * boxQuantity
  lazy val boxCostTotal = boxUnitCostPrice.map(_ * boxQuantity)
  lazy val boxTotalCostPrice: Option[BigDecimal] = boxUnitCostPrice.map(_ * boxQuantity)
  lazy val outerTax = boxTaxInfo.outerTax(boxTotal)
}

case class ShippingTotal(
  table: Seq[ShippingTotalEntry] = List()
)(
  implicit taxRepo: TaxRepo
) {
  lazy val size = table.size
  lazy val boxQuantity = table.foldLeft(0)(_ + _.boxQuantity)
  lazy val boxTotal = table.foldLeft(BigDecimal(0))(_ + _.boxTotal)
  lazy val sumByTaxId: Map[Long, BigDecimal] = {
    var sumById = LongMap[BigDecimal]().withDefaultValue(BigDecimal(0))
    table.foreach { e =>
      val taxId = e.boxTaxInfo.taxId
      sumById += taxId -> (sumById(taxId) + e.boxTotal)
    }
    sumById
  }
  lazy val taxByType: Map[TaxType, BigDecimal] = {
    var hisById = LongMap[TaxHistory]()
    table.foreach { e =>
      val taxId = e.boxTaxInfo.taxId
      if (! hisById.contains(taxId)) {
        hisById += taxId -> e.boxTaxInfo
      }
    }

    sumByTaxId.foldLeft(Map[TaxType, BigDecimal]().withDefaultValue(BigDecimal(0))) {
      (sum, e) => {
        val his = hisById(e._1)
        sum.updated(his.taxType, sum(his.taxType) + his.taxAmount(e._2))
      }
    }
  }
  lazy val taxAmount = taxByType(TaxType.INNER_TAX) + taxByType(TaxType.OUTER_TAX)
  lazy val taxHistoryById: LongMap[TaxHistory] = table.foldLeft(LongMap[TaxHistory]()) {
    (sum, e) =>
      val taxHistory = e.boxTaxInfo
      sum.updated(taxHistory.taxId, taxHistory)
  }
  lazy val bySite: Map[Site, ShippingTotal] =
    table.foldLeft(
      TreeMap[Site, Vector[ShippingTotalEntry]]()
        .withDefaultValue(Vector[ShippingTotalEntry]())
    ) { (map, e) =>
      map.updated(e.site, map(e.site).+:(e))
    }.mapValues(
      e => ShippingTotal(e.toSeq)
    ).withDefaultValue(
      ShippingTotal()
    )
  lazy val byItemClass: Map[Long, ShippingTotal] =
    table.foldLeft(
      TreeMap[Long, Vector[ShippingTotalEntry]]()
        .withDefaultValue(Vector[ShippingTotalEntry]())
    ) { (map, e) =>
      map.updated(e.itemClass, map(e.itemClass).+:(e))
    }.mapValues(e => ShippingTotal(e.toSeq))
}

case class ShippingBox(
  id: Option[Long] = None, siteId: Long, itemClass: Long, boxSize: Int, boxName: String
)

case class ShippingFee(
  id: Option[Long] = None, shippingBoxId: Long, countryCode: CountryCode, locationCode: Int
)

case class ShippingFeeHistory(
  id: Option[Long] = None, shippingFeeId: Long, taxId: Long, fee: BigDecimal, costFee: Option[BigDecimal], validUntil: Instant
)

case class ShippingFeeEntries(
  // site, itemClass, quantity
  bySiteAndItemClass: Map[Site, Map[Long, Int]] = new HashMap[Site, Map[Long, Int]]
) {
  def add(site: Site, itemClass: Long, quantity: Int): ShippingFeeEntries = ShippingFeeEntries(
    bySiteAndItemClass.get(site) match {
      case None =>
        bySiteAndItemClass + (site -> (LongMap(itemClass -> quantity).withDefaultValue(0)))
      case Some(longMap) =>
        bySiteAndItemClass + (site -> longMap.updated(itemClass, longMap(itemClass) + quantity))
    }
  )
}

object ShippingBoxRepo {
  val simple = {
    SqlParser.get[Option[Long]]("shipping_box.shipping_box_id") ~
    SqlParser.get[Long]("shipping_box.site_id") ~
    SqlParser.get[Long]("shipping_box.item_class") ~
    SqlParser.get[Int]("shipping_box.box_size") ~
    SqlParser.get[String]("shipping_box.box_name") map {
      case id~siteId~itemClass~boxSize~boxName =>
        ShippingBox(id, siteId, itemClass, boxSize, boxName)
    }
  }

  val withSite = SiteRepo.simple ~ simple map {
    case site~shippingBox => (site, shippingBox)
  }
}

@Singleton
class ShippingBoxRepo @Inject() (
  siteRepo: SiteRepo,
  shippingFeeHistoryRepo: ShippingFeeHistoryRepo
) {
  def createNew(
    siteId: Long, itemClass: Long, boxSize: Int, boxName: String
  )(implicit conn: Connection): ShippingBox = {
    SQL(
      """
      insert into shipping_box
      (shipping_box_id, site_id, item_class, box_size, box_name)
      values
      ((select nextval('shipping_box_seq')),
      {siteId}, {itemClass}, {boxSize}, {boxName})
      """
    ).on(
      'siteId -> siteId,
      'itemClass -> itemClass,
      'boxSize -> boxSize,
      'boxName -> boxName
    ).executeUpdate()
    
    val id = SQL("select currval('shipping_box_seq')").as(SqlParser.scalar[Long].single)
    ShippingBox(Some(id), siteId, itemClass, boxSize, boxName)
  }

  def apply(id: Long)(implicit conn: Connection): ShippingBox =
    SQL(
      """
      select * from shipping_box where shipping_box_id = {id}
      """
    ).on(
      'id -> id
    ).as(
      ShippingBoxRepo.simple.single
    )

  def getWithSite(id: Long)(implicit conn: Connection): Option[(Site, ShippingBox)] =
    SQL(
      """
      select * from shipping_box
      inner join site on site.site_id = shipping_box.site_id
      where shipping_box_id = {id}
      """
    ).on(
      'id -> id
    ).as(
      ShippingBoxRepo.withSite.singleOpt
    )

  def apply(siteId: Long, itemClass: Long)(implicit conn: Connection): ShippingBox =
    SQL(
      """
      select * from shipping_box where siteId = {siteId} and itemClass = {itemClass}
      """
    ).on(
      'siteId -> siteId,
      'itemClass -> itemClass
    ).as(
      ShippingBoxRepo.simple.single
    )

  def list(siteId: Long)(implicit conn: Connection): Seq[ShippingBox] =
    SQL(
      """
      select * from shipping_box where site_id = {siteId} order by item_class
      """
    ).on(
      'siteId -> siteId
    ).as(
      ShippingBoxRepo.simple *
    )

  def list(implicit conn: Connection): Seq[(Site, ShippingBox)] =
    SQL(
      """
      select * from shipping_box
      inner join site on site.site_id = shipping_box.site_id
      order by site.site_name, item_class
      """
    ).as(
      ShippingBoxRepo.withSite *
    )

  def update(
    id: Long,
    siteId: Long,
    itemClass: Long,
    boxSize: Int,
    boxName: String
  ) (
    implicit conn: Connection
  ) {
    SQL(
      """
      update shipping_box
      set site_id = {siteId},
        item_class = {itemClass},
        box_size = {boxSize},
        box_name = {boxName}
      where shipping_box_id = {id}
      """
    ).on(
      'id -> id,
      'siteId -> siteId,
      'itemClass -> itemClass,
      'boxSize -> boxSize,
      'boxName -> boxName
    ).executeUpdate()
  }

  def removeWithChildren(boxId: Long)(implicit conn: Connection) {
    // Histories will be cascade deleted.

    SQL(
      """
      delete from shipping_fee where shipping_box_id = {boxId}
      """
    ).on(
      'boxId -> boxId
    ).executeUpdate()

    SQL(
      """
      delete from shipping_box where shipping_box_id = {boxId}
      """
    ).on(
      'boxId -> boxId
    ).executeUpdate()
  }
}

object ShippingFeeRepo {
  val simple = {
    SqlParser.get[Option[Long]]("shipping_fee.shipping_fee_id") ~
    SqlParser.get[Long]("shipping_fee.shipping_box_id") ~
    SqlParser.get[Int]("shipping_fee.country_code") ~
    SqlParser.get[Int]("shipping_fee.location_code") map {
      case id~shippingBoxId~countryCode~locationCode =>
        ShippingFee(id, shippingBoxId, CountryCode.byIndex(countryCode), locationCode)
    }
  }

  val withHistory = simple ~ (ShippingFeeHistoryRepo.simple ?) map {
    case fee~feeHistory => (fee, feeHistory)
  }
}

@Singleton
class ShippingFeeRepo @Inject() (
) {
  def apply(id: Long)(implicit conn: Connection): ShippingFee =
    SQL(
      """
      select * from shipping_fee where shipping_fee_id = {id}
      """
    ).on(
      'id -> id
    ).as(
      ShippingFeeRepo.simple.single
    )

  def createNew(
    shippingBoxId: Long, countryCode: CountryCode, locationCode: Int
  )(implicit conn: Connection): ShippingFee = {
    SQL(
      """
      insert into shipping_fee
      (shipping_fee_id, shipping_box_id, country_code, location_code)
      values (
        (select nextval('shipping_fee_seq')),
        {shippingBoxId}, {countryCode}, {locationCode}
      )
      """
    ).on(
      'shippingBoxId -> shippingBoxId,
      'countryCode -> countryCode.code,
      'locationCode -> locationCode
    ).executeUpdate()

    val id = SQL("select currval('shipping_fee_seq')").as(SqlParser.scalar[Long].single)

    ShippingFee(Some(id), shippingBoxId, countryCode, locationCode)
  }

  def apply(
    shippingBoxId: Long, countryCode: CountryCode, locationCode: Int
  )(implicit conn: Connection): ShippingFee =
    SQL(
      """
      select * from shipping_fee
      where shipping_box_id = {shippingBoxId}
      and country_code = {countryCode}
      and location_code = {locationCode}
      """
    ).on(
      'shippingBoxId -> shippingBoxId,
      'countryCode -> countryCode.code,
      'locationCode -> locationCode
    ).as(
      ShippingFeeRepo.simple.single
    )

  def listWithHistory(
    boxId: Long, now: Instant
  )(implicit conn: Connection): Seq[(ShippingFee, Option[ShippingFeeHistory])] =
    SQL(
      """
      select * from shipping_fee sf
      left join shipping_fee_history sfh on sf.shipping_fee_id = sfh.shipping_fee_id
      where (sfh.valid_until > {now} or sfh.valid_until is null)
      and sf.shipping_box_id = {boxId}
      and not exists (
        select * from shipping_fee_history sfh2
        where sf.shipping_fee_id = sfh2.shipping_fee_id
        and sfh2.valid_until > {now}
        and sfh2.valid_until < sfh.valid_until
      )
      order by sf.shipping_fee_id, sf.country_code, sf.location_code
      """
    ).on(
      'now -> now,
      'boxId -> boxId
    ).as(
      ShippingFeeRepo.withHistory *
    )

  def list(boxId: Long, countryCode: CountryCode)(implicit conn: Connection): Seq[ShippingFee] =
    SQL(
      """
      select * from shipping_fee
      where shipping_box_id = {boxId}
      and country_code = {countryCode}
      """
    ).on(
      'boxId -> boxId,
      'countryCode -> countryCode.code
    ).as(
      ShippingFeeRepo.simple *
    )

  def removeWithHistories(feeId: Long)(implicit conn: Connection) {
    SQL(
      """
      delete from shipping_fee_history where shipping_fee_id = {feeId}
      """
    ).on(
      'feeId -> feeId
    ).executeUpdate()

    SQL(
      """
      delete from shipping_fee where shipping_fee_id = {feeId}
      """
    ).on(
      'feeId -> feeId
    ).executeUpdate()
  }
}

object ShippingFeeHistoryRepo {
  val simple = {
    SqlParser.get[Option[Long]]("shipping_fee_history.shipping_fee_history_id") ~
    SqlParser.get[Long]("shipping_fee_history.shipping_fee_id") ~
    SqlParser.get[Long]("shipping_fee_history.tax_id") ~
    SqlParser.get[java.math.BigDecimal]("shipping_fee_history.fee") ~
    SqlParser.get[Option[java.math.BigDecimal]]("shipping_fee_history.cost_fee") ~
    SqlParser.get[java.time.Instant]("shipping_fee_history.valid_until") map {
      case id~feeId~taxId~fee~costFee~validUntil => ShippingFeeHistory(
        id, feeId, taxId, fee, costFee.map {(b: java.math.BigDecimal) => BigDecimal(b)}, validUntil
      )
    }
  }

  val withBoxFee = ShippingBoxRepo.simple ~ ShippingFeeRepo.simple map {
    case box~fee => (box, fee)
  }

  val withFee = ShippingFeeRepo.simple ~ simple map {
    case shippingFee~shippingFeeHistory => (shippingFee, shippingFeeHistory)
  }
}

@Singleton
class ShippingFeeHistoryRepo @Inject() (
  implicit val shippingFeeRepo: ShippingFeeRepo,
  implicit val taxHistoryRepo: TaxHistoryRepo,
  implicit val taxRepo: TaxRepo
) {
  def apply(id: Long)(implicit conn: Connection): ShippingFeeHistory = SQL(
    """
    select * from shipping_fee_history
    where shipping_fee_history_id = {id}
    """
  ).on(
    'id -> id
  ).as(
    ShippingFeeHistoryRepo.simple.single
  )

  def update(
    historyId: Long, taxId: Long, fee: BigDecimal, costFee: Option[BigDecimal], validUntil: Instant
  )(implicit conn: Connection) {
    SQL(
      """
      update shipping_fee_history
      set tax_id = {taxId},
        fee = {fee},
        cost_fee = {costFee},
        valid_until = {validUntil}
      where shipping_fee_history_id = {historyId}
      """
    ).on(
      'historyId -> historyId,
      'taxId -> taxId,
      'fee -> fee.bigDecimal,
      'costFee -> costFee.map(_.bigDecimal),
      'validUntil -> validUntil
    ).executeUpdate()
  }

  def remove(id: Long)(implicit conn: Connection) {
    SQL(
      """
      delete from shipping_fee_history
      where shipping_fee_history_id = {id}
      """
    ).on(
      'id -> id
    ).executeUpdate()
  }

  def createNew(
    feeId: Long, taxId: Long, fee: BigDecimal, costFee: Option[BigDecimal], validUntil: Instant
  )(implicit conn: Connection): ShippingFeeHistory = {
    SQL(
      """
      insert into shipping_fee_history (shipping_fee_history_id, shipping_fee_id, tax_id, fee, cost_fee, valid_until)
      values (
        (select nextval('shipping_fee_history_seq')), {feeId}, {taxId}, {fee}, {costFee}, {validUntil}
      )
      """
    ).on(
      'feeId -> feeId,
      'taxId -> taxId,
      'fee -> fee.bigDecimal,
      'costFee -> costFee.map(_.bigDecimal),
      'validUntil -> validUntil
    ).executeUpdate()

    val id = SQL("select currval('shipping_fee_history_seq')").as(SqlParser.scalar[Long].single)

    ShippingFeeHistory(Some(id), feeId, taxId, fee, costFee, validUntil)
  }

  def list(feeId: Long)(implicit conn: Connection): Seq[ShippingFeeHistory] = SQL(
    "select * from shipping_fee_history where shipping_fee_id = {shippingFeeId} order by valid_until"
  ).on(
    'shippingFeeId -> feeId
  ).as(
    ShippingFeeHistoryRepo.simple *
  )

  def at(
    feeId: Long, now: Instant = Instant.now()
  )(implicit conn: Connection): ShippingFeeHistory = SQL(
    """
    select * from shipping_fee_history
    where shipping_fee_id = {feeId}
    and {now} < valid_until
    order by valid_until
    limit 1
    """
  ).on(
    'feeId -> feeId,
    'now -> now
  ).as(
    ShippingFeeHistoryRepo.simple.single
  )

  def atOpt(
    feeId: Long, now: Instant = Instant.now()
  )(implicit conn: Connection): Option[ShippingFeeHistory] = SQL(
    """
    select * from shipping_fee_history
    where shipping_fee_id = {feeId}
    and {now} < valid_until
    order by valid_until
    limit 1
    """
  ).on(
    'feeId -> feeId,
    'now -> now
  ).as(
    ShippingFeeHistoryRepo.simple.singleOpt
  )

  def listByLocation(
    countryCode: CountryCode, locationCode: Int
  )(implicit conn: Connection): Seq[(ShippingFee, ShippingFeeHistory)] =
    SQL(
      """
      select * from shipping_fee_history
      inner join shipping_fee on shipping_fee_history.shipping_fee_id = shipping_fee.shipping_fee_id
      where country_code = {countryCode} and location_code = {locationCode}
      order by shipping_fee_history.valid_until
      """
    ).on(
      'countryCode -> countryCode.code,
      'locationCode -> locationCode
    ).as(
      ShippingFeeHistoryRepo.withFee *
    ).toSeq

  def feeBySiteAndItemClass(
    countryCode: CountryCode, locationCode: Int, entries: ShippingFeeEntries,
    now: Instant = Instant.now()
  )(
    implicit conn: Connection
  ): ShippingTotal = {
    var result = List[ShippingTotalEntry]()

    entries.bySiteAndItemClass.foreach { t =>
      val site = t._1
      val quantityByItemClass = t._2
      var longMap: Map[Long, (ShippingBox, ShippingFee, Int)] = LongMap()

      val list = SQL(
        """
        select * from shipping_fee
        inner join shipping_box on shipping_box.shipping_box_id = shipping_fee.shipping_box_id
        where country_code = {countryCode}
        and location_code = {locationCode}
        and site_id = {siteId}
        """
      ).on(
        'countryCode -> countryCode.code,
        'locationCode -> locationCode,
        'siteId -> site.id.get
      ).as(
        ShippingFeeHistoryRepo.withBoxFee *
      )
      
      if (list.isEmpty) throw new CannotShippingException(site, locationCode)
      val boxesByItemClass = list.map { e => (e._1.itemClass -> e) }.toMap

      quantityByItemClass.foreach { e =>
        val itemClass = e._1
        val quantity = e._2

        boxesByItemClass.get(itemClass) match {
          case None => throw new CannotShippingException(site, locationCode, itemClass)
          case Some(box) => longMap += (itemClass -> (box._1, box._2, quantity))
        }
      }

      result ++= addPrice(site, longMap, now)
    }

    ShippingTotal(result)
  }

  def addPrice(
    site: Site,
    map: Map[Long, (ShippingBox, ShippingFee, Int)], // The key is itemClass.
    now: Instant
  )(
    implicit conn: Connection
  ): Seq[ShippingTotalEntry] = {
    val ret = ListBuffer[ShippingTotalEntry]()

    map.foreach { e =>
      val quantity = e._2._3
      val boxSize = e._2._1.boxSize
      val boxCount = (quantity + boxSize - 1) / boxSize
      val feeId = e._2._2.id.get

      atOpt(feeId, now) match {
        case None => throw new RuntimeException(
          "shipping_fee_history record not found(shipping_fee_id = " + feeId + ")"
        )
        case Some(boxFeeHistory) => {
          val taxHistory = taxHistoryRepo.at(boxFeeHistory.taxId)
          ret.append(
            ShippingTotalEntry(
              site, e._1, e._2._1, e._2._2, quantity, boxCount, boxFeeHistory.fee, boxFeeHistory.costFee, taxHistory
            )
          )
        }
      }
    }

    ret.toSeq
  }
}
