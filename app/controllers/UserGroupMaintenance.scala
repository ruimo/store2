package controllers

import play.Logger
import play.api.data.validation.Constraints._
import helpers.Cache
import play.api.i18n.{Lang, Messages}
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
class UserGroupMaintenance @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  userGroupRepo: UserGroupRepo,
  userGroupMemberRepo: UserGroupMemberRepo,
  storeUserRepo: StoreUserRepo,
  implicit val db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val createForm = Form(
    mapping(
      "groupName" -> text.verifying(nonEmpty, maxLength(256))
    ) (CreateUserGroup.apply)(CreateUserGroup.unapply)
  )

  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.userGroupMaintenance())
    }
  }

  def startCreate() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(views.html.admin.createUserGroup(createForm))
    }
  }

  def remove(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        userGroupRepo.remove(UserGroupId(id))
        Redirect(
          routes.UserGroupMaintenance.edit()
        ).flashing("message" -> Messages("removed", Messages("userGroup")))
      }
    }
  }

  def create() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      createForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserGroupMaintenance.create(). " + formWithErrors)
          BadRequest(views.html.admin.createUserGroup(formWithErrors))
        },
        newName => {
          try {
            ExceptionMapper.mapException {
              db.withConnection { implicit conn =>
                userGroupRepo.create(newName.groupName)
              }
              Redirect(
                routes.UserGroupMaintenance.edit()
              ).flashing("message" -> Messages("userGroupIsCreated"))
            }
          }
          catch {
            case e: UniqueConstraintException =>
              BadRequest(
                views.html.admin.createUserGroup(
                  createForm.withError("groupName", "unique.constraint.violation")
                )
              )
            case t: Throwable => throw t
          }
        }
      )
    }
  }

  def edit(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(
          views.html.admin.editUserGroup(
            userGroupRepo.list(page, pageSize, OrderBy(orderBySpec))
          )
        )
      }
    }
  }

  def editMember(
    userGroupId: Long, page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        val userGroup = userGroupRepo(UserGroupId(userGroupId))
        val orderBy = OrderBy(orderBySpec)

        Ok(
          views.html.admin.editUserGroupMember(
            userGroup,
            userGroupMemberRepo.listByUserGroupId(
              page, pageSize, orderBy, UserGroupId(userGroupId)
            )
          )
        )
      }
    }
  }

  def removeMember(
    userGroupId: Long, userId: Long
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        userGroupMemberRepo.remove(UserGroupId(userGroupId), userId)
      }

      Redirect(
        routes.UserGroupMaintenance.editMember(userGroupId)
      ).flashing("message" -> Messages("removed", Messages("user")))
    }
  }

  def userListForMember(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(
        views.html.admin.userListForMember(
          db.withConnection { implicit conn =>
            storeUserRepo.listUsers(page, pageSize, OrderBy(orderBySpec))
          }
        )
      )
    }
  }

  val addMemberForm = Form(
    single(
      "userId" -> longNumber
    )
  )

  def addMember(userGroupId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      val userId: Long = addMemberForm.bindFromRequest.get
      db.withConnection { implicit conn =>
        userGroupMemberRepo.create(UserGroupId(userGroupId), userId)
      }
      Redirect(
        routes.UserGroupMaintenance.editMember(userGroupId)
      ).flashing("message" -> Messages("added", Messages("user")))
    }
  }
}
