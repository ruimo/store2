package controllers

import javax.inject.{Inject, Singleton}

import constraints.FormConstraints
import controllers.NeedLogin.Authenticated
import helpers.ItemInquiryMail
import models.{LocaleInfoRepo, SiteItemRepo}
import play.api.db.Database
import play.api.mvc.MessagesControllerComponents

@Singleton
class ItemInquiryReserve @Inject() (
  cc: MessagesControllerComponents,
  fc: FormConstraints,
  authenticated: Authenticated,
  db: Database,
  itemInquiryMail: ItemInquiryMail,
  localeInfoRepo: LocaleInfoRepo,
  siteItemRepo: SiteItemRepo,
  shoppingCartItemRepo: ShoppingCartItemRepo
) extends ItemInquiryReserveBase(cc, fc, authenticated, db, itemInquiryMail, localeInfoRepo, siteItemRepo, shoppingCartItemRepo)
