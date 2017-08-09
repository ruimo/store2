package controllers

import play.api.i18n.Lang
import play.twirl.api.Html
import models._

import collection.immutable
import play.api.data.Form
import play.api.data.Forms._
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.db.Database
import play.api.mvc._

@Singleton
class OrderHistory @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  implicit val db: Database,
  implicit val transactionSummary: TransactionSummary,
  implicit val accountingBill: AccountingBill,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val orderHistoryForm = Form(
    mapping(
      "year" -> number(min = YearMonth.MinYear, max = YearMonth.MaxYear),
      "month" -> number(min = 1, max = 12),
      "command" -> text
    )(YearMonth.apply)(YearMonth.unapply)
  )

  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      Ok(views.html.orderHistory(orderHistoryForm))
  }

  type ShowOrderView = (
    PagedRecords[TransactionSummaryEntry],
    immutable.LongMap[Seq[TransactionDetail]],
    immutable.LongMap[scala.collection.immutable.LongMap[TransactionLogShipping]],
    immutable.LongMap[PersistedTransaction],
    immutable.Map[Long, Address],
    Option[Long]
  ) => Html

  def showOrderHistoryList(
    page: Int, pageSize: Int, orderBySpec: String, tranId: Option[Long]
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      db.withConnection { implicit conn =>
        showOrderHistoryInternal(
          page: Int, pageSize: Int, orderBySpec: String, tranId,
          views.html.showOrderHistoryList.apply
        )
      }
  }

  def showOrderHistory(
    page: Int, pageSize: Int, orderBySpec: String, tranId: Option[Long]
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      db.withConnection { implicit conn =>
        val pagedRecords: PagedRecords[TransactionSummaryEntry] =
          transactionSummary.list(
            storeUserId = Some(login.storeUser.id.get), tranId = tranId,
            page = page, pageSize = pageSize, orderBy = OrderBy(orderBySpec)
          )
        val siteTranByTranId: immutable.LongMap[PersistedTransaction] =
          transactionSummary.getSiteTranByTranId(pagedRecords.records, request.acceptLanguages.toList)

        showOrderHistoryInternal(
          page, pageSize, orderBySpec, tranId,
          views.html.showOrderHistory.apply
        )
      }
  }

  def showOrderHistoryInternal(
    page: Int, pageSize: Int, orderBySpec: String,
    tranId: Option[Long],
    view: ShowOrderView
  )(
    implicit login: LoginSession,
    request: AuthMessagesRequest[AnyContent],
    conn: Connection
  ): Result = {
    val pagedRecords = transactionSummary.list(
      storeUserId = Some(login.storeUser.id.get), tranId = tranId,
      page = page, pageSize = pageSize, orderBy = OrderBy(orderBySpec)
    )
    val siteTranByTranId: immutable.LongMap[PersistedTransaction] =
      transactionSummary.getSiteTranByTranId(pagedRecords.records, request.acceptLanguages.toList)

    Ok(
      view(
        pagedRecords,
        transactionSummary.getDetailByTranSiteId(pagedRecords.records),
        accountingBill.getBoxBySiteAndItemSize(pagedRecords.records),
        siteTranByTranId, 
        accountingBill.getAddressTable(siteTranByTranId),
        tranId
      )
    )
  }

  def showMonthly() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    if (login.isAnonymousBuyer)
      Redirect(routes.Application.index)
    else
      orderHistoryForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(
            views.html.orderHistory(formWithErrors)
          )
        },
        yearMonth => {
          db.withConnection { implicit conn =>
            val summaries = transactionSummary.listByPeriod(
              storeUserId = Some(login.storeUser.id.get), yearMonth = yearMonth
            )
            val siteTranByTranId = transactionSummary.getSiteTranByTranId(summaries, request.acceptLanguages.toList)
            Ok(views.html.showMonthlyOrderHistory(
              orderHistoryForm.fill(yearMonth),
              summaries,
              transactionSummary.getDetailByTranSiteId(summaries),
              accountingBill.getBoxBySiteAndItemSize(summaries),
              siteTranByTranId,
              accountingBill.getAddressTable(siteTranByTranId)
            ))
          }
        }
      )
  }
}
