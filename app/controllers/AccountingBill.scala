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
class AccountingBill @Inject() (
  cc: MessagesControllerComponents,
  cache: Cache,
  authenticated: NeedLogin.Authenticated,
  db: Database,
  implicit val siteRepo: SiteRepo,
  transactionSummary: TransactionSummary,
  transactionLogShippingRepo: TransactionLogShippingRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val UseShippingDateForAccountingBill: () => Boolean = cache.config(
    _.getOptional[Boolean]("useShippingDateForAccountingBill").getOrElse(false)
  )

  val accountingBillForm = Form(
    mapping(
      "year" -> number(min = YearMonth.MinYear, max = YearMonth.MaxYear),
      "month" -> number(min = 1, max = 12),
      "user" -> longNumber,
      "command" -> text
    )(YearMonthUser.apply)(YearMonthUser.unapply)
  )

  val accountingBillForStoreForm = Form(
    mapping(
      "year" -> number(min = YearMonth.MinYear, max = YearMonth.MaxYear),
      "month" -> number(min = 1, max = 12),
      "site" -> longNumber,
      "command" -> text
    )(YearMonthSite.apply)(YearMonthSite.unapply)
  )

  def AllUser(implicit mp: MessagesProvider) = ("0", Messages("all"))

  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] => {
    implicit val login: LoginSession = request.login

    db.withConnection { implicit conn =>
      Ok(
        views.html.accountingBill(
          accountingBillForm, accountingBillForStoreForm, List(), LongMap(), LongMap(), LongMap(),
          false, siteRepo.tableForDropDown, Map(), List(AllUser)
        )
      )
    }
  }}

  def show() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    accountingBillForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in AccountingBill.showForStore() " + formWithErrors)
        db.withConnection { implicit conn =>
          BadRequest(
            views.html.accountingBill(
              formWithErrors, accountingBillForStoreForm, List(), LongMap(), LongMap(), LongMap(),
              false, siteRepo.tableForDropDown, Map(), List(AllUser)
            )
          )
        }
      },
      yearMonth => {
        db.withConnection { implicit conn =>
          val table: AccountingBillTable = transactionSummary.accountingBillForUser(
            login.siteUser.map(_.siteId),
            yearMonth,
            yearMonth.userIdOpt,
            request.acceptLanguages.toList,
            UseShippingDateForAccountingBill()
          )

          if (yearMonth.command == "csv") {
            implicit val cs = play.api.mvc.Codec.javaSupported("Windows-31j")
            val fileName = "accountingBillByUser.csv"

            Ok(
              createCsv(table.summaries, table.siteTranByTranId, false)
            ).as(
              "text/csv charset=Shift_JIS"
            ).withHeaders(
              CONTENT_DISPOSITION -> ("""attachment; filename="%s"""".format(fileName))
            )
          }
          else {
            Ok(views.html.accountingBill(
              accountingBillForm.fill(yearMonth),
              accountingBillForStoreForm,
              table.summaries,
              transactionSummary.getDetailByTranSiteId(table.summaries),
              getBoxBySiteAndItemSize(table.summaries),
              table.siteTranByTranId,
              false,
              siteRepo.tableForDropDown,
              getAddressTable(table.siteTranByTranId),
              getUserDropDown(table.summariesForAllUser)
            ))
          }
        }
      }
    )
  }

  def showForStore() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    accountingBillForStoreForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in AccountingBill.showForStore() " + formWithErrors)
        db.withConnection { implicit conn =>
          BadRequest(
            views.html.accountingBill(
              accountingBillForm, formWithErrors, List(), LongMap(), LongMap(), LongMap(), 
              true, siteRepo.tableForDropDown, Map(), List()
            )
          )
        }
      },
      yearMonthSite => {
        db.withConnection { implicit conn =>
          val summaries = transactionSummary.listByPeriod(
            siteId = Some(yearMonthSite.siteId), yearMonth = yearMonthSite,
            onlyShipped = true, useShippedDate = UseShippingDateForAccountingBill()
          )
          val siteTranByTranId = transactionSummary.getSiteTranByTranId(summaries, request.acceptLanguages.toList)
          val useCostPrice = true

          if (yearMonthSite.command == "csv") {
            implicit val cs = play.api.mvc.Codec.javaSupported("Windows-31j")
            val fileName = "accountingBillByStore.csv"

            Ok(
              createCsv(summaries, siteTranByTranId, useCostPrice)
            ).as(
              "text/csv charset=Shift_JIS"
            ).withHeaders(
              CONTENT_DISPOSITION -> ("""attachment; filename="%s"""".format(fileName))
            )
          }
          else {
            Ok(views.html.accountingBill(
              accountingBillForm,
              accountingBillForStoreForm.fill(yearMonthSite),
              summaries,
              transactionSummary.getDetailByTranSiteId(summaries),
              getBoxBySiteAndItemSize(summaries),
              siteTranByTranId,
              useCostPrice,
              siteRepo.tableForDropDown,
              getAddressTable(siteTranByTranId),
              getUserDropDown(summaries)
            ))
          }
        }
      }
    )
  }

  def getAddressTable(
    tran: LongMap[PersistedTransaction]
  )(
    implicit conn: Connection
  ): Map[Long, Address] = {
    val addresses = scala.collection.mutable.Map[Long, Address]()
    tran.values.foreach {
      pt => pt.shippingTable.values.foreach {
        seq => seq.foreach {
          tranShipping =>
          val id = tranShipping.addressId
          if (! addresses.isDefinedAt(id)) {
            addresses.put(id, Address.byId(id))
          }
        }
      }
    }
    addresses.toMap
  }

  def getBoxBySiteAndItemSize(
    summaries: Seq[TransactionSummaryEntry]
  )(
    implicit conn: Connection
  ): LongMap[LongMap[TransactionLogShipping]] = {
    summaries.foldLeft(
      LongMap[LongMap[TransactionLogShipping]]()
    ) {
      (sum, e) =>
      sum ++ transactionLogShippingRepo.listBySite(e.transactionSiteId).foldLeft(
        LongMap[LongMap[TransactionLogShipping]]().withDefaultValue(LongMap[TransactionLogShipping]())
      ) {
        (names, e2) =>
        names.updated(
          e.transactionSiteId,
          names(e.transactionSiteId).updated(e2.itemClass, e2)
        )
      }
    }
  }

  def getUserDropDown(
    summaries: Seq[TransactionSummaryEntry]
  )(
    implicit mp: MessagesProvider
  ): Seq[(String, String)] = AllUser +: summaries.foldLeft(Set[StoreUser]()) {
    (set, e) => set + e.buyer
  }.map {
    u => (u.id.get.toString, u.userName)
  }.toSeq

  def createCsv(
    summaries: Seq[TransactionSummaryEntry],
    siteTranByTranId: immutable.LongMap[PersistedTransaction],
    useCostPrice: Boolean
  )(
    implicit mp: MessagesProvider
  ): String = {
    class Rec {
      var userName: String = _
      var companyName: String = _
      var itemSubtotal: BigDecimal = BigDecimal(0)
      var tax: BigDecimal = BigDecimal(0)
      var fee: BigDecimal = BigDecimal(0)
      def total = itemSubtotal + tax + fee
    }

    val rows = summaries.foldLeft(immutable.LongMap[Rec]().withDefault(idx => new Rec)) { (sum, e) =>
      val rec = sum(e.buyer.id.get)

      rec.userName = e.buyer.fullName
      rec.companyName = e.buyer.companyName.getOrElse("")
      rec.itemSubtotal += (
        if (useCostPrice) siteTranByTranId(e.transactionId).costPriceTotal
        else siteTranByTranId(e.transactionId).itemTotal
      )(e.siteId)
      rec.tax += (
        if (useCostPrice) siteTranByTranId(e.transactionId).outerTaxWhenCostPrice
        else siteTranByTranId(e.transactionId).outerTaxTotal
      )(e.siteId)
      rec.fee += siteTranByTranId(e.transactionId).boxTotal(e.siteId)

      sum.updated(e.buyer.id.get, rec)
    }.foldLeft(new StringBuilder) { (buf, e) =>
      buf.append(e._1)
        .append(',').append('"').append(e._2.userName).append('"')
        .append(',').append('"').append(e._2.companyName).append('"')
        .append(',').append(e._2.itemSubtotal)
        .append(',').append(e._2.tax)
        .append(',').append(e._2.fee)
        .append(',').append(e._2.total)
        .append("\r\n")
    }.toString

    Messages("accountingBillCsvHeaderUserId") + "," +
    Messages("accountingBillCsvHeaderUserName") + "," +
    Messages("accountingBillCsvHeaderCompanyName") + "," +
    Messages("accountingBillCsvHeaderItemTotal") + "," +
    Messages("accountingBillCsvHeaderOuterTax") + "," +
    Messages("accountingBillCsvHeaderFee") + "," +
    Messages("accountingBillCsvHeaderGrandTotal") + "\r\n" + rows
  }
}
