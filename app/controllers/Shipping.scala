package controllers

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import helpers.Forms._
import play.Logger

import scala.util.{Failure, Success, Try}
import java.net.URLDecoder

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import helpers._
import models.CreateAddress
import models._

import collection.immutable.LongMap
import java.util.Locale
import java.util.regex.Pattern

import play.api.data.Forms._
import play.api.data.validation.Constraints._
import java.sql.Connection
import java.time.Instant
import java.time.format.DateTimeFormatter

import scala.collection.immutable
import java.util.regex.Pattern
import javax.inject.{Inject, Singleton}

import play.api.data.Form
import play.api.data.validation.Constraints._
import play.api.i18n.{Messages, MessagesProvider}
import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import play.api.db.Database
import play.api.mvc._

@Singleton
class Shipping @Inject() (
  cc: MessagesControllerComponents,
  cache: Cache,
  fc: FormConstraints,
  authenticated: Authenticated,
  notificationMail: NotificationMail,
  recommendEngine: RecommendEngine,
  admin: Admin,
  ws: WSClient,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val currencyRegistry: CurrencyRegistry,
  implicit val shippingFeeHistoryRepo: ShippingFeeHistoryRepo,
  implicit val transactionPersister: TransactionPersister,
  implicit val siteItemNumericMetadataRepo: SiteItemNumericMetadataRepo,
  implicit val taxRepo: TaxRepo,
  implicit val fakePaypalResponseRepo: FakePaypalResponseRepo,
  implicit val acceptableTender: AcceptableTender
) extends MessagesAbstractController(cc) with I18n {
  val NameValuePattern = Pattern.compile("=")

  val tenderTypeForm = Form(
    single(
      "tenderType" -> text
    )
  )

  val UrlBase: () => String = cache.config(
    _.get[String]("urlBase")
  )

  val PaypalApiUrl: () => String = cache.config(
    _.get[String]("paypal.apiUrl")
  )

  val PaypalApiVersion: () => String = cache.config(
    _.get[String]("paypal.apiVersion")
  )

  val PaypalUser: () => String = cache.config(
    _.get[String]("paypal.user")
  )

  val PaypalPassword: () => String = cache.config(
    _.get[String]("paypal.password")
  )

  val PaypalSignature: () => String = cache.config(
    _.get[String]("paypal.signature")
  )

  val PaypalRedirectUrl: () => String = cache.config(
    _.get[String]("paypal.redirectUrl")
  )

  val PaypalLocaleCode: () => String = cache.config(
    _.get[String]("paypal.localeCode")
  )

  val IsFakePaypalResponseEnabled: () => Boolean = cache.config(
    _.getOptional[Boolean]("fakePaypalRespons.enabled").getOrElse(false)
  )

  val PaypalWebPaymentPlusUrl: () => String = cache.config(
    _.get[String]("paypalWebPaymentPlus.requestUrl")
  )

  val PaypalId: () => String = cache.config(
    _.get[String]("paypalWebPaymentPlus.paypalId")
  )

  val PaypalWebPaymentPlusDebug: () => Boolean = cache.config(
    _.getOptional[Boolean]("paypalWebPaymentPlus.debug").getOrElse(false)
  )

  val firstNameKanaConstraint = List(nonEmpty, maxLength(64))
  val lastNameKanaConstraint = List(nonEmpty, maxLength(64))

  val Zip1Pattern = Pattern.compile("\\d{3}")
  val Zip2Pattern = Pattern.compile("\\d{4}")
  val TelPattern = Pattern.compile("\\d+{1,32}")
  val TelOptionPattern = Pattern.compile("\\d{0,32}")
  def shippingDateFormat(implicit mp: MessagesProvider) = DateTimeFormatter.ofPattern(Messages("shipping.date.format"))

  def jaForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "firstNameKana" -> text.verifying(firstNameKanaConstraint: _*),
      "lastNameKana" -> text.verifying(lastNameKanaConstraint: _*),
      "zip1" -> text.verifying(z => Zip1Pattern.matcher(z).matches),
      "zip2" -> text.verifying(z => Zip2Pattern.matcher(z).matches),
      "prefecture" -> number,
      "address1" -> text.verifying(nonEmpty, maxLength(256)),
      "address2" -> text.verifying(nonEmpty, maxLength(256)),
      "address3" -> text.verifying(maxLength(256)),
      "address4" -> text.verifying(maxLength(256)),
      "address5" -> text.verifying(maxLength(256)),
      "tel1" -> text.verifying(Messages("error.number"), z => TelPattern.matcher(z).matches),
      "tel2" -> text.verifying(Messages("error.number"), z => TelOptionPattern.matcher(z).matches),
      "tel3" -> text.verifying(Messages("error.number"), z => TelOptionPattern.matcher(z).matches),
      "shippingDate" -> instant(Messages("shipping.date.format")),
      "comment" -> text.verifying(maxLength(2048)),
      "email" -> text.verifying(fc.emailConstraint: _*)
    )(CreateAddress.apply4Japan)(CreateAddress.unapply4Japan)
  )

  def startEnterShippingAddress = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    db.withConnection { implicit conn =>
      if (shoppingCartItemRepo.isAllCoupon(login.userId)) {
        Redirect(routes.Shipping.confirmShippingAddressJa())
      }
      else {
        val addr: Option[Address] = ShippingAddressHistory.list(login.userId).headOption.map {
          h => Address.byId(h.addressId)
        }.orElse {
          UserAddress.getByUserId(login.userId).map { ua =>
            Address.byId(ua.addressId)
          }
        }
        val shippingDate = ShoppingCartShipping.find(login.userId).getOrElse(Instant.now().plus(5, ChronoUnit.DAYS))
        val form = addr match {
          case Some(a) =>
            jaForm.fill(CreateAddress.fromAddress(a.fillEmailIfEmpty(login.storeUser.email), shippingDate))
          case None =>
            jaForm.bind(
              Map(
                "shippingDate" -> shippingDateFormat.format(shippingDate.atZone(ZoneId.systemDefault())),
                "email" -> login.storeUser.email
              )
            ).discardingErrors
        }

        request.acceptLanguages.head match {
          case `japanese` =>
            Ok(views.html.shippingAddressJa(form, Address.JapanPrefectures))
          case `japan` =>
            Ok(views.html.shippingAddressJa(form, Address.JapanPrefectures))
        
          case _ =>
            Ok(views.html.shippingAddressJa(form, Address.JapanPrefectures))
        }
      }
    }
  }

  def enterShippingAddressJa = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    jaForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in Shipping.enterShippingAddress. " + formWithErrors)
        db.withConnection { implicit conn =>
          BadRequest(views.html.shippingAddressJa(formWithErrors, Address.JapanPrefectures))
        }
      },
      newShippingAddress => {
        db.withTransaction { implicit conn =>
          newShippingAddress.save(login.userId)
          if (login.isAnonymousBuyer) {
            login.update(newShippingAddress)
          }
          Redirect(routes.Shipping.confirmShippingAddressJa())
        }
      }
    )
  }

  def confirmShippingAddressJa = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    db.withConnection { implicit conn =>
      val (cart: ShoppingCartTotal, errors: Seq[ItemExpiredException]) =
        shoppingCartItemRepo.listItemsForUser(localeInfoRepo.getDefault(request.acceptLanguages.toList), login)

      if (! errors.isEmpty) {
        Ok(views.html.itemExpired(errors))
      }
      else {
        if (shoppingCartItemRepo.isAllCoupon(login.userId)) {
          Ok(
            views.html.confirmShippingAddressJa(
              Transaction(
                login.userId, currencyRegistry.Jpy, cart, None, ShippingTotal(), ShippingDate()
              )
            )
          )
        }
        else {
          val shipping = cart.sites.foldLeft(LongMap[ShippingDateEntry]()) { (sum, e) =>
            sum.updated(e.id.get, ShippingDateEntry(e.id.get, ShoppingCartShipping.find(login.userId, e.id.get)))
          }
          val his = ShippingAddressHistory.list(login.userId).head
          val addr = Address.byId(his.addressId)
          try {
            Ok(
              views.html.confirmShippingAddressJa(
                Transaction(
                  login.userId, currencyRegistry.Jpy, cart, Some(addr), shippingFee(addr, cart), ShippingDate(shipping)
                )
              )
            )
          }
          catch {
            case e: CannotShippingException => {
              Ok(views.html.cannotShipJa(cannotShip(cart, e, addr), addr, e.itemClass))
            }
          }
        }
      }
    }
  }


  def cannotShip(
    cart: ShoppingCartTotal, e: CannotShippingException, addr: Address
  ): Seq[ShoppingCartTotalEntry] = {
    cart.table.filter { item =>
      item.siteItemNumericMetadata.get(SiteItemNumericMetadataType.SHIPPING_SIZE) match {
        case None => true
        case Some(itemClass) =>
          e.isCannotShip(
            item.site,
            addr.prefecture.code,
            itemClass.metadata
          )
      }
    }
  }

  def shippingFee(
    addr: Address, cart: ShoppingCartTotal
  )(implicit conn: Connection): ShippingTotal = {
    shippingFeeHistoryRepo.feeBySiteAndItemClass(
      CountryCode.JPN, addr.prefecture.code,
      cart.table.foldLeft(ShippingFeeEntries()) {
        (sum, e) => sum.add(
          e.site,
          e.siteItemNumericMetadata.get(SiteItemNumericMetadataType.SHIPPING_SIZE).map(_.metadata).getOrElse(1L),
          e.shoppingCartItem.quantity
        )
      }
    )
  }

  def finalizeTransactionJa = authenticated.async { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    finalizeTransaction(currencyRegistry.Jpy)
  }

  def finalizeTransaction(
    currency: CurrencyInfo
  )(
    implicit request: MessagesRequest[AnyContent], login: LoginSession
  ): Future[Result] = tenderTypeForm.bindFromRequest.fold(
    formWithErrors => {
      Logger.error("Unknown error. Tender type is not specified? form: " + formWithErrors)
      Future.successful(Redirect(routes.Shipping.confirmShippingAddressJa()))
    },
    transactionType => db.withTransaction { implicit conn =>
      val (cart: ShoppingCartTotal, expErrors: Seq[ItemExpiredException]) =
        shoppingCartItemRepo.listItemsForUser(localeInfoRepo.getDefault(request.acceptLanguages.toList), login)

      if (! expErrors.isEmpty) {
        Future.successful(Ok(views.html.itemExpired(expErrors)))
      }
      else if (cart.isEmpty) {
        Logger.error("Shipping.finalizeTransaction(): shopping cart is empty.")
        throw new Error("Shipping.finalizeTransaction(): shopping cart is empty.")
      }
      else {
        val exceedStock: immutable.Map[(ItemId, Long), (String, String, Int, Long)] =
          shoppingCartItemRepo.itemsExceedStock(login.userId, localeInfoRepo.getDefault(request.acceptLanguages.toList))

        if (! exceedStock.isEmpty) {
          Logger.error("Item exceed stock. " + exceedStock)
          Future.successful(Ok(views.html.itemStockExhausted(exceedStock)))
        }
        else if (shoppingCartItemRepo.isAllCoupon(login.userId)) {
          val (tranId: Long, taxesBySite: immutable.Map[Site, immutable.Seq[TransactionLogTax]]) =
            transactionPersister.persist(
              Transaction(login.userId, currency, cart, None, ShippingTotal(), ShippingDate())
            )
          shoppingCartItemRepo.removeForUser(login.userId)
          ShoppingCartShipping.removeForUser(login.userId)
          val tran = transactionPersister.load(tranId, localeInfoRepo.getDefault(request.acceptLanguages.toList))
          notificationMail.orderCompleted(login, tran, None)
          recommendEngine.onSales(login, tran, None)
          Future.successful(
            Ok(
              views.html.showTransactionJa(
                tran, None, textMetadata(tran), siteItemMetadata(tran),
                admin.anonymousCanPurchase() && login.isAnonymousBuyer
              )
            )
          )
        }
        else {
          transactionType match {
            case "payByAccountingBill" => Future.successful(payByAccountingBill(currency, cart))
            case "payByPaypal" => payByPaypal(currency, cart)
            case "payByPaypalWebPaymentPlus" => payByPaypalWebPeymentPlus(currency, cart)
          }
        }
      }
    }
  )

  def payByAccountingBill(
    currency:CurrencyInfo, cart: ShoppingCartTotal
  )(
    implicit request: MessagesRequest[AnyContent], login: LoginSession, conn: Connection
  ): Result = {
    val his = ShippingAddressHistory.list(login.userId).head
    val addr = Address.byId(his.addressId)
    val shippingDateBySite = cart.sites.foldLeft(LongMap[ShippingDateEntry]()) { (sum, e) =>
      sum.updated(e.id.get, ShippingDateEntry(e.id.get, ShoppingCartShipping.find(login.userId, e.id.get)))
    }
    try {
      val (tranId: Long, taxesBySite: immutable.Map[Site, immutable.Seq[TransactionLogTax]])
        = transactionPersister.persist(
          Transaction(
            login.userId, currency, cart, Some(addr), shippingFee(addr, cart), ShippingDate(shippingDateBySite)
          )
        )
      shoppingCartItemRepo.removeForUser(login.userId)
      ShoppingCartShipping.removeForUser(login.userId)
      val tran = transactionPersister.load(tranId, localeInfoRepo.getDefault(request.acceptLanguages.toList))
      val address = Address.byId(tran.shippingTable.head._2.head.addressId)
      notificationMail.orderCompleted(login, tran, Some(address))
      recommendEngine.onSales(login, tran, Some(address))
      Ok(
        views.html.showTransactionJa(
          tran, Some(address), textMetadata(tran), siteItemMetadata(tran),
          admin.anonymousCanPurchase() && login.isAnonymousBuyer
        )
      )
    }
    catch {
      case e: CannotShippingException => {
        Ok(views.html.cannotShipJa(cannotShip(cart, e, addr), addr, e.itemClass))
      }
    }
  }

  def payByPaypal(
    currency:CurrencyInfo, cart: ShoppingCartTotal
  )(
    implicit request: MessagesRequest[AnyContent], login: LoginSession
  ): Future[Result] = db.withConnection { implicit conn =>
    val his = ShippingAddressHistory.list(login.userId).head
    val addr = Address.byId(his.addressId)
    val shippingDateBySite = cart.sites.foldLeft(LongMap[ShippingDateEntry]()) { (sum, e) =>
      sum.updated(e.id.get, ShippingDateEntry(e.id.get, ShoppingCartShipping.find(login.userId, e.id.get)))
    }

    try {
      val shippingTotal: ShippingTotal = shippingFee(addr, cart)

      val (tranId: Long, taxesBySite: immutable.Map[Site, immutable.Seq[TransactionLogTax]], token: Long) = {
        transactionPersister.persistPaypal(
          Transaction(
            login.userId, currency, cart, Some(addr), shippingTotal, ShippingDate(shippingDateBySite)
          )
        )
      }

      val successUrl = UrlBase() + routes.Paypal.onSuccess(tranId, token).url
      val cancelUrl = UrlBase() + routes.Paypal.onCancel(tranId, token).url
      val resp: Future[WSResponse] = if (IsFakePaypalResponseEnabled()) {
        Future.successful(fakePaypalResponseRepo())
      } else {
        ws.url(PaypalApiUrl()).post(
          Map(
            "USER" -> Seq(PaypalUser()),
            "PWD" -> Seq(PaypalPassword()),
            "SIGNATURE" -> Seq(PaypalSignature()),
            "VERSION" -> Seq(PaypalApiVersion()),
            "PAYMENTREQUEST_0_PAYMENTACTION" -> Seq("Sale"),
            "PAYMENTREQUEST_0_AMT" -> Seq((cart.total + cart.outerTaxTotal + shippingTotal.boxTotal).toString),
            "PAYMENTREQUEST_0_CURRENCYCODE" -> Seq(currency.currencyCode),
            "PAYMENTREQUEST_0_INVNUM" -> Seq(tranId.toString),
            "RETURNURL" -> Seq(successUrl),
            "CANCELURL" -> Seq(cancelUrl),
            "METHOD" -> Seq("SetExpressCheckout"),
            "SOLUTIONTYPE" -> Seq("Sole"),
            "LANDINGPAGE" -> Seq("Billing"),
            "LOCALECODE" -> Seq(PaypalLocaleCode())
          )
        )
      }

      resp.map { resp =>
        val body = URLDecoder.decode(resp.body, "UTF-8")
        Logger.info("Paypal response: '" + body + "'")
        val values: immutable.Map[String, String] = immutable.Map[String, String]() ++ body.split("&").map { s =>
          val a = NameValuePattern.split(s, 2)
          a(0) -> a(1)
        }
        Logger.info("Paypal response decoded: " + values)
        values("ACK") match {
          case "Success" =>
            db.withConnection { implicit conn =>
              TransactionLogPaypalStatus.update(tranId, PaypalStatus.PREPARED)
            }
            Redirect(
              PaypalRedirectUrl(),
              Map(
                "cmd" -> Seq("_express-checkout"),
                "token" -> Seq(values("TOKEN"))
              )
            )
          case _ =>
            db.withConnection { implicit conn =>
              TransactionLogPaypalStatus.update(tranId, PaypalStatus.ERROR)
            }
            Logger.error("Cannot start paypal checkout: '" + body + "'")
            Ok(views.html.paypalError())
        }
      }
    }
    catch {
      case e: CannotShippingException => {
        Future.successful(
          Ok(views.html.cannotShipJa(cannotShip(cart, e, addr), addr, e.itemClass))
        )
      }
    }
  }

  def payByPaypalWebPeymentPlus(
    currency:CurrencyInfo, cart: ShoppingCartTotal
  )(
    implicit request: MessagesRequest[AnyContent], login: LoginSession
  ): Future[Result] = db.withConnection { implicit conn =>
    val his = ShippingAddressHistory.list(login.userId).head
    val addr = Address.byId(his.addressId)
    val shippingDateBySite = cart.sites.foldLeft(LongMap[ShippingDateEntry]()) { (sum, e) =>
      sum.updated(e.id.get, ShippingDateEntry(e.id.get, ShoppingCartShipping.find(login.userId, e.id.get)))
    }

    try {
      val shippingTotal: ShippingTotal = shippingFee(addr, cart)

      val (tranId: Long, taxesBySite: immutable.Map[Site, immutable.Seq[TransactionLogTax]], token: Long) = {
        transactionPersister.persistPaypalWebPaymentPlus(
          Transaction(
            login.userId, currency, cart, Some(addr), shippingTotal, ShippingDate(shippingDateBySite)
          )
        )
      }

      val subTotal = cart.total + cart.outerTaxTotal + shippingTotal.boxTotal
      val paypalId = PaypalId()
      val successUrl = UrlBase() + routes.Paypal.onWebPaymentPlusSuccess(tranId, token).url
      val cancelUrl = UrlBase() + routes.Paypal.onWebPaymentPlusCancel(tranId, token).url
      Future.successful(
        Ok(
          views.html.paypalWebPaymentPlusStart(
            addr, currency, subTotal, paypalId, PaypalWebPaymentPlusUrl(), successUrl, cancelUrl, PaypalWebPaymentPlusDebug()
          )
        )
      )
    }
    catch {
      case e: CannotShippingException => {
        Future.successful(
          Ok(views.html.cannotShipJa(cannotShip(cart, e, addr), addr, e.itemClass))
        )
      }
    }
  }

  def textMetadata(
    tran: PersistedTransaction
  )(
    implicit conn: Connection
  ): Map[Long, Map[ItemTextMetadataType, ItemTextMetadata]] = {
    val buf = scala.collection.mutable.HashMap[Long, Map[ItemTextMetadataType, ItemTextMetadata]]()
    tran.itemTable.foreach { e =>
      val items = e._2
      items.foreach { it =>
        val tranItem = it._2
        val itemId = ItemId(tranItem.itemId)
        buf.update(tranItem.itemId, ItemTextMetadata.allById(itemId))
      }
    }

    buf.toMap
  }

  def siteItemMetadata(
    tran: PersistedTransaction
  )(
    implicit conn: Connection
  ): Map[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]] = {
    val buf = scala.collection.mutable.HashMap[(Long, Long), Map[SiteItemNumericMetadataType, SiteItemNumericMetadata]]()
    tran.itemTable.foreach { e =>
      val siteId = e._1
      val items = e._2
      items.foreach { it =>
        val tranItem = it._2
        val itemId = tranItem.itemId
        buf.update(siteId -> itemId, siteItemNumericMetadataRepo.all(siteId, ItemId(tranItem.itemId)))
      }
    }

    buf.toMap
  }
}
