package controllers

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.{Authenticated}
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form
import models._
import play.Logger
import play.api.db.Database
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class CategoryMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: NeedLogin.Authenticated,
  implicit val db: Database,
  implicit val localeInfoRepo: LocaleInfoRepo,
  implicit val categoryRepo: CategoryRepo,
  implicit val categoryPathRepo: CategoryPathRepo,
  implicit val categoryNameRepo: CategoryNameRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createCategoryForm = Form(
    mapping(
      "langId" -> longNumber,
      "categoryName" -> text.verifying(nonEmpty, maxLength(32)),
      "parent" -> optional(longNumber)
    )(CreateCategory.apply)(CreateCategory.unapply)
  )

  val updateCategoryCodeForm = Form(
    mapping(
      "categoryCode" -> text.verifying(
        nonEmpty, maxLength(20), pattern("[a-zA-Z0-9_]+".r, "categoryCodePattern", "categoryCodePatternError")
      )
    )(UpdateCategoryCode.apply)(UpdateCategoryCode.unapply)
  )

  val updateCategoryNameForm = Form(
    mapping(
      "categoryNames" -> seq(
        mapping(
          "categoryId" -> longNumber,
          "localeId" -> longNumber,
          "name" -> text.verifying(nonEmpty, maxLength(32))
        )(UpdateCategoryName.apply)(UpdateCategoryName.unapply)
      )
    )(UpdateCategoryNameTable.apply)(UpdateCategoryNameTable.unapply)
  )

  val createCategoryNameForm = Form(
    mapping(
      "categoryId" -> longNumber,
      "localeId" -> longNumber,
      "name" -> text.verifying(nonEmpty, maxLength(32))
    )(UpdateCategoryName.apply)(UpdateCategoryName.unapply)
  )

  val removeCategoryNameForm = Form(
    mapping(
      "categoryId" -> longNumber,
      "localeId" -> longNumber
    )(RemoveCategoryName.apply)(RemoveCategoryName.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.categoryMaintenance())
    }
  }

  def startCreateNewCategory = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.createNewCategory(createCategoryForm, localeInfoRepo.localeTable))
    }
  }

  def createNewCategory = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      createCategoryForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in CategoryMaintenance.createNewCategory." + formWithErrors)
          BadRequest(views.html.admin.createNewCategory(formWithErrors, localeInfoRepo.localeTable))
        },
        newCategory => db.withConnection { implicit conn =>
          newCategory.save
          Redirect(
            routes.CategoryMaintenance.startCreateNewCategory
          ).flashing("message" -> Messages("categoryIsCreated"))
        })
    }
  }

  def editCategory(
    langSpec: Option[Long], start: Int, size: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      val locale: LocaleInfo = langSpec.map(localeInfoRepo.apply).getOrElse(localeInfoRepo.getDefault(request.acceptLanguages.toList))

      db.withConnection { implicit conn =>
        {
          val categories: PagedRecords[(Category, Option[CategoryName])] = categoryRepo.listWithName(
            page = start, pageSize = size, locale = locale, OrderBy(orderBySpec)
          )
          Ok(views.html.admin.editCategory(locale, localeInfoRepo.localeTable, categories))
        }
      }
    }
  }

  /* Seq[CategoryNode] to be folded into recursive JSON structure and vice versa:
        [ {"key":      category_id,
           "title":    category_name,
           "isFolder": true,
           "children": [ 
             ... 
           ] },
             ... ] 
  */
  case class CategoryNode(key: Long, title: String, isFolder: Boolean = true, children: Seq[CategoryNode])

  implicit object CategoryNodeFormat extends Format[CategoryNode] {
    def reads(json: JsValue): JsResult[CategoryNode] = JsSuccess(CategoryNode(
      (json \ "key").as[Long],
      (json \ "title").as[String],
      (json \ "isFolder").as[Boolean],
      (json \ "chidlren").as[Seq[CategoryNode]]))

    def writes(ct: CategoryNode): JsValue = JsObject(List(
      "key" -> JsNumber(ct.key),
      "title" -> JsString(ct.title),
      "isFolder" -> JsBoolean(ct.isFolder),
      "children" -> JsArray(ct.children.map(CategoryNodeFormat.writes))))
  }

  def categoryPathTree = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        val locale = localeInfoRepo.getDefault(request.acceptLanguages.toList)

        // list of parent-name pairs
        val pns: Seq[(Long, CategoryName)] = categoryPathRepo.listNamesWithParent(locale)

        val idToName: Map[Long, CategoryName] = pns.map { t => (t._2.categoryId, t._2) }.toMap

        //map of Category to list of its child CategoryNames
        val pnSubTrees: Map[Long, Seq[Long]] =
          pns.foldLeft(Map[Long, Seq[Long]]()) { (subTree, pn) =>
            val name = pn._2
            val pid = pn._1
            val myid = name.categoryId
            val childrenIds = subTree.get(pid).getOrElse(Seq())

            subTree + (pid -> (if (myid == pid) childrenIds else childrenIds :+ myid))
          }

        def categoryChildren(categoryIds: Seq[Long]): Seq[CategoryNode] =
          categoryIds map { id =>
            CategoryNode(
              key = id,
              title = idToName(id).name,
              children = categoryChildren(pnSubTrees(id)))
          }

        val roots: Seq[Long] = categoryRepo.root(locale) map { _.id.get }
        val pathTree = categoryChildren(roots)

        Ok(Json.toJson(pathTree))
      }
    }
  }

  val moveCategoryForm = Form(tuple(
    "categoryId" -> longNumber,
    "parentCategoryId" -> optional(longNumber)))

  def moveCategory = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      val (categoryId, parentCategoryId) = moveCategoryForm.bindFromRequest.get;
      try {
        db.withConnection { implicit conn =>
          categoryRepo.move(
            categoryRepo.get(categoryId).get,
            parentCategoryId map { categoryRepo.get(_).get }
          )
        }
        Ok
      } catch {
        case e: Throwable => BadRequest
      }
    }
  }

  def createUpdateForms(categoryId: Long)(implicit conn: Connection): Form[UpdateCategoryNameTable] = {
    updateCategoryNameForm.fill(
      UpdateCategoryNameTable(
        categoryNameRepo.all(categoryId).values.toSeq.map { cn =>
          UpdateCategoryName(cn.categoryId, cn.locale.id, cn.name)
        }
      )
    )
  }

  def editCategoryName(categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(
          views.html.admin.editCategoryName(
            categoryId,
            createCategoryNameForm.bind(Map("categoryId" -> categoryId.toString)).discardingErrors,
            createUpdateForms(categoryId),
            removeCategoryNameForm,
            localeInfoRepo.localeTable
          )
        )
      }
    }
  }

  def editCategoryCode(categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    Ok(views.html.admin.editCategoryCode(categoryId, updateCategoryCodeForm))
  }

  def updateCategoryCode(categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    updateCategoryCodeForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error("Validation error in CategoryMaintenance.updateCategoryCode." + formWithErrors)
        BadRequest(
          views.html.admin.editCategoryCode(categoryId, formWithErrors)
        )
      },
      newCategoryCode => db.withConnection { implicit conn =>
        try {
          newCategoryCode.save(categoryId)
          Redirect(
            routes.CategoryMaintenance.editCategory(None)
          ).flashing("message" -> Messages("categoryIsUpdated"))
        }
        catch {
          case e: UniqueConstraintException =>
            BadRequest(
              views.html.admin.editCategoryCode(
                categoryId,
                updateCategoryCodeForm.fill(newCategoryCode).withError("categoryCode", "unique.constraint.violation")
              )
            )
        }
      }
    )
  }

  def updateCategoryName(categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      updateCategoryNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in CategoryMaintenance.updateCategoryName." + formWithErrors)
          BadRequest(
            views.html.admin.editCategoryName(
              categoryId,
              createCategoryNameForm,
              formWithErrors,
              removeCategoryNameForm,
              localeInfoRepo.localeTable
            )
          )
        },
        newCategoryName => db.withConnection { implicit conn =>
          newCategoryName.save()
          Redirect(
            routes.CategoryMaintenance.editCategory(None)
          ).flashing("message" -> Messages("categoryIsUpdated"))
        }
      )
    }
  }

  def createCategoryName(categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      createCategoryNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in CategoryMaintenance.createCategoryName." + formWithErrors)
          db.withConnection { implicit conn =>
            BadRequest(
              views.html.admin.editCategoryName(
                categoryId,
                formWithErrors,
                createUpdateForms(categoryId),
                removeCategoryNameForm,
                localeInfoRepo.localeTable
              )
            )
          }
        },
        newCategoryName => db.withConnection { implicit conn =>
          try {
            newCategoryName.create()
            Redirect(
              routes.CategoryMaintenance.editCategoryName(categoryId)
            ).flashing("message" -> Messages("categoryIsUpdated"))
          }
          catch {
            case e: UniqueConstraintException => {
              BadRequest(
                views.html.admin.editCategoryName(
                  categoryId,
                  createCategoryNameForm.fill(newCategoryName).withError("localeId", "unique.constraint.violation"),
                  createUpdateForms(categoryId),
                  removeCategoryNameForm,
                  localeInfoRepo.localeTable
                )
              )
            }
          }
        }
      )
    }
  }

  def removeCategoryName(categoryId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      removeCategoryNameForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in CategoryMaintenance.createCategoryName." + formWithErrors)
          db.withConnection { implicit conn =>
            BadRequest(
              views.html.admin.editCategoryName(
                categoryId,
                createCategoryNameForm,
                createUpdateForms(categoryId),
                removeCategoryNameForm,
                localeInfoRepo.localeTable
              )
            )
          }
        },
        categoryName => db.withConnection { implicit conn =>
          categoryName.remove()
          Redirect(
            routes.CategoryMaintenance.editCategoryName(categoryId)
          ).flashing("message" -> Messages("categoryIsRemoved"))
        }
      )
    }
  }
}
