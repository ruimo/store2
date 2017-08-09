package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import models._
import play.api.db.Database
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class Invoice @Inject() (
  cc: MessagesControllerComponents,
  transactionSummary: TransactionSummary,
  localeInfoRepo: LocaleInfoRepo,
  db: Database,
  transactionPersister: TransactionPersister,
  authenticated: Authenticated,
  transactionDetailRepo: TransactionDetailRepo
) extends MessagesAbstractController(cc) {
  def show(tranSiteId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    db.withConnection { implicit conn =>
      val entry = transactionSummary.get(login.siteUser.map(_.siteId), tranSiteId).get
      val locale = localeInfoRepo.getDefault(request.acceptLanguages.toList)
      val persistedTran = transactionPersister.load(entry.transactionId, locale)
      Ok(
        views.html.showInvoice(
          entry,
          persistedTran,
          transactionDetailRepo.show(tranSiteId, locale, login.siteUser)
        )
      )
    }
  }
}
