package views.csv

import helpers.CsvWriter
import models.{LoginSession, TransactionDetail, TransactionLogShipping, TransactionSummaryEntry}
import play.api.i18n.{Lang, Messages, MessagesProvider}

class TransactionDetailBodyCsv(csvWriter: CsvWriter) {
  def print(
    tranId: Long, tranSummary: TransactionSummaryEntry, detail: TransactionDetail
  ) (
    implicit mp: MessagesProvider,
    loginSession: LoginSession
  ) {
    csvWriter.print(
      tranId.toString,
      Messages("csv.tran.detail.date.format").format(new java.util.Date(tranSummary.transactionTime.toEpochMilli)),
      tranSummary.shippingDate.map { shippingDate =>
        Messages("csv.tran.detail.shippingDate.format").format(new java.util.Date(shippingDate.toEpochMilli))
      }.getOrElse(""),
      Messages("csv.tran.detail.type.item"),
      detail.itemName,
      detail.quantity.toString,
      detail.unitPrice.toString,
      detail.costUnitPrice.toString
    )
  }

  def printShipping(
    tranId: Long, tranSummary: TransactionSummaryEntry, detail: TransactionLogShipping
  ) (
    implicit mp: MessagesProvider,
    loginSession: LoginSession
  ) {
    csvWriter.print(
      tranId.toString,
      Messages("csv.tran.detail.date.format").format(new java.util.Date(tranSummary.transactionTime.toEpochMilli)),
      tranSummary.shippingDate.map { shippingDate =>
        Messages("csv.tran.detail.shippingDate.format").format(new java.util.Date(shippingDate.toEpochMilli))
      }.getOrElse(""),
      Messages("csv.tran.detail.type.shipping"),
      detail.boxName,
      detail.boxCount.toString,
      (detail.amount / detail.boxCount).toString,
      "0"
    )
  }
}
