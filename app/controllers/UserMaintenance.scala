package controllers

import javax.inject.{Inject, Singleton}

import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import helpers.Cache
import models._
import play.api.db.Database
import play.api.mvc.MessagesControllerComponents
import play.api.Configuration

@Singleton
class UserMaintenance @Inject() (
  cc: MessagesControllerComponents,
  config: Configuration,
  cache: Cache,
  fc: FormConstraints,
  admin: Admin,
  storeUserRepo: StoreUserRepo,
  authenticated: Authenticated,
  orderNotificationRepo: OrderNotificationRepo,
  siteUserRepo: SiteUserRepo,
  db: Database,
  siteRepo: SiteRepo,
  loginSessionRepo: LoginSessionRepo,
  shoppingCartItemRepo: ShoppingCartItemRepo
) extends UserMaintenanceImpl(
  cc, cache, fc, admin, config, storeUserRepo, authenticated, orderNotificationRepo, siteUserRepo, db, siteRepo, loginSessionRepo,
  shoppingCartItemRepo
)
