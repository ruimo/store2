package controllers

import javax.inject.{Inject, Singleton}

import helpers.{CategoryCodeSearchCondition, CategoryIdSearchCondition}
import controllers.NeedLogin.OptAuthenticated
import models._
import helpers.QueryString
import play.api.db.Database
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}

@Singleton
class ItemQuery @Inject() (
  cc: MessagesControllerComponents,
  optAuthenticated: OptAuthenticated,
  implicit val db: Database,
  implicit val itemRepo: ItemRepo,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val itemPriceStrategyRepo: ItemPriceStrategyRepo,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val categoryNameRepo: CategoryNameRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  def query(
    qs: List[String], page: Int, pageSize: Int, orderBySpec: String, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    val queryString = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
    implicit val optLogin = loginSessionRepo.fromRequest(request)

    val list = itemRepo.list(
      locale = localeInfoRepo.getDefault(request.acceptLanguages.toList),
      queryString = queryString,
      page = page,
      pageSize = pageSize,
      orderBy = OrderBy(orderBySpec)
    )
    val itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
    Ok(
      if (templateNo == 0)
        views.html.query(
          "", queryString, list,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.query(qs, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
      else
        views.html.queryTemplate(
          "", queryString, list, templateNo,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.query(qs, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
    )
  }}}

  def queryByCategory(
    qs: List[String], c: Option[Long], page: Int, pageSize: Int, orderBySpec: String, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    val queryString = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
    implicit val optLogin = loginSessionRepo.fromRequest(request)

    val list = itemRepo.list(
      locale = localeInfoRepo.getDefault(request.acceptLanguages.toList),
      queryString = queryString,
      category = CategoryIdSearchCondition(c.toSeq: _*),
      page = page,
      pageSize = pageSize,
      orderBy = OrderBy(orderBySpec)
    )
    val itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
    Ok(
      if (templateNo == 0)
        views.html.query(
          "", queryString, list,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.queryByCategory(qs, c, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
      else
        views.html.queryTemplate(
          "", queryString, list, templateNo,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.queryByCategory(qs, c, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
    )
  }}}

  def queryBySite(
    qs: List[String], sid: Option[Long], page: Int, pageSize: Int, orderBySpec: String, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    val queryString = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
    implicit val optLogin = loginSessionRepo.fromRequest(request)

    val list = itemRepo.list(
      locale = localeInfoRepo.getDefault(request.acceptLanguages.toList),
      queryString = queryString,
      siteId = sid,
      page = page,
      pageSize = pageSize,
      orderBy = OrderBy(orderBySpec)
    )
    val itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
    Ok(
      if (templateNo == 0)
        views.html.query(
          "", queryString, list,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.queryBySite(qs, sid, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
      else
        views.html.queryTemplate(
          "", queryString, list, templateNo,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.queryBySite(qs, sid, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
    )
  }}}

  def queryBySiteAndCategory(
    qs: List[String], sid: Option[Long], c: Option[Long], page: Int, pageSize: Int, orderBySpec: String, 
    templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    val queryString = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
    implicit val optLogin = loginSessionRepo.fromRequest(request)

    val list = itemRepo.list(
      locale = localeInfoRepo.getDefault(request.acceptLanguages.toList),
      queryString = queryString,
      category = CategoryIdSearchCondition(c.toSeq: _*),
      siteId = sid,
      page = page,
      pageSize = pageSize,
      orderBy = OrderBy(orderBySpec)
    )
    val itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
    Ok(
      if (templateNo == 0)
        views.html.query(
          "", queryString, list,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.queryBySiteAndCategory(qs, sid, c, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
      else
        views.html.queryTemplate(
          "", queryString, list, templateNo,
          (newPage, newPageSize, newTemplateNo, newOrderBy) =>
            routes.ItemQuery.queryBySiteAndCategory(qs, sid, c, newPage, newPageSize, newOrderBy, newTemplateNo),
          itemPriceStrategy
        )
    )
  }}}

  def queryByCheckBox(
    page: Int, pageSize: Int, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    request.queryString.get("queryText") match {
      case None =>
        Ok(views.html.queryByCheckBox())
      case Some(seq) =>
        Redirect(routes.ItemQuery.query(seq.toList, page, pageSize, templateNo = templateNo))
    }
  }}}

  def queryBySelect(
    page: Int, pageSize: Int, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    request.queryString.get("queryText") match {
      case None =>
        Ok(views.html.queryBySelect())
      case Some(seq) =>
        Redirect(routes.ItemQuery.query(seq.toList, page, pageSize, templateNo = templateNo))
    }
  }}}

  def queryByRadio(
    page: Int, pageSize: Int, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn => {
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    val list = request.queryString.filterKeys {_.startsWith("queryText")}.values.foldLeft(List[String]())(_ ++ _)
    if (list.isEmpty)
      Ok(views.html.queryByRadio())
    else
      Redirect(routes.ItemQuery.query(list, page, pageSize, templateNo = templateNo))
    }
  }}

  def queryAdvanced(
    qs: List[String], cs: String, ccs: String, sid: Option[Long], page: Int, pageSize: Int, orderBySpec: String, 
    templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn =>
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    Ok(
      views.html.queryAdvanced(
        templateNo,
        routes.ItemQuery.queryAdvancedContent(
          qs, cs, ccs, sid, page, pageSize, OrderBy(orderBySpec).toString
        ).url
      )
    )
  }}

  def queryAdvancedContent(
    qs: List[String], cs: String, ccs: String, sid: Option[Long], page: Int, pageSize: Int, orderBySpec: String, templateNo: Int
  ) = optAuthenticated { implicit request: MessagesRequest[AnyContent] => db.withConnection { implicit conn =>
    implicit val optLogin = loginSessionRepo.fromRequest(request)
    val queryString = if (qs.size == 1) QueryString(qs.head) else QueryString(qs.filter {! _.isEmpty})
    val list = itemRepo.list(
      locale = localeInfoRepo.getDefault(request.acceptLanguages.toList),
      queryString = queryString,
      category = CategoryIdSearchCondition(cs),
      categoryCodes = CategoryCodeSearchCondition(ccs),
      siteId = sid,
      page = page,
      pageSize = pageSize,
      orderBy = OrderBy(orderBySpec)
    )

    val itemPriceStrategy = itemPriceStrategyRepo(ItemPriceStrategyContext(optLogin))
    Ok(
      views.html.queryAdvancedContent(
        list,
        (newPage, newPageSize, newTemplateNo, newOrderBy) =>
          routes.ItemQuery.queryAdvanced(qs, cs, ccs, sid, newPage, newPageSize, newOrderBy, templateNo),
        itemPriceStrategy
      )
    )
  }}

  def categoryNameJson = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    request.body.asJson.map { json =>
      val categoryCodes = (json \ "categoryCodes").as[Seq[String]]

      db.withConnection { implicit conn =>
        val categoryNames: Seq[(String, String)] = 
          categoryNameRepo.categoryNameByCode(categoryCodes, localeInfoRepo.getDefault(request.acceptLanguages.toList))
        Ok(
          Json.toJson(
            JsObject(
              Seq(
                "categoryNames" -> JsArray(
                  categoryNames.map { n =>
                    JsObject(
                      Seq(
                        "categoryCode" -> JsString(n._1),
                        "categoryName" -> JsString(n._2)
                      )
                    )
                  }
                )
              )
            )
          )
        )
      }
    }.get
  }
}
