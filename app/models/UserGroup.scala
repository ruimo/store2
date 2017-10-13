package models

import helpers.Cache
import javax.inject.Singleton

import scala.concurrent.duration._
import play.api.data.validation.Invalid
import constraints.FormConstraints
import play.api.data.validation.ValidationError
import play.api.Logger
import anorm._

import scala.language.postfixOps
import java.sql.Connection

import scala.util.{Failure, Success, Try}
import com.ruimo.csv.CsvRecord
import helpers.{PasswordHash, RandomTokenGenerator, TokenGenerator}
import java.sql.SQLException
import javax.inject.Inject

import com.google.inject
import play.api.db.Database

case class UserGroupId(value: Long) extends AnyVal

case class UserGroup(
  id: Option[UserGroupId], name: String
)

case class UserGroupMember(
  userGroupId: UserGroupId,
  storeUserId: Long
)

@Singleton
class UserGroupRepo @Inject() (
) {
  val simple = {
    SqlParser.get[Option[Long]]("user_group.user_group_id") ~
    SqlParser.get[String]("user_group.name") map {
      case id~name => UserGroup(id.map(UserGroupId.apply), name)
    }
  }

  def create(name: String)(implicit conn: Connection): UserGroup = {
    SQL(
      """
      insert into user_group (user_group_id, name) values (
        (select nextval('user_group_seq')), {name}
      )
      """
    ).on(
      'name -> name
    ).executeUpdate()

    val id = SQL("select currval('user_group_seq')").as(SqlParser.scalar[Long].single)
    
    UserGroup(Some(UserGroupId(id)), name)
  }

  def list(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy = OrderBy("user_group.name")
  )(
    implicit conn: Connection
  ): PagedRecords[UserGroup] = {
    val list = SQL(
      s"""
      select * from user_group order by $orderBy limit {pageSize} offset {offset}
    
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      simple *
    )

    val count = SQL("select count(*) from user_group").as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, list)
  }

  def remove(id: UserGroupId)(implicit conn: Connection): Int = SQL(
    "delete grom user_group where user_group_id = {id}"
  ).on(
    'id -> id.value
  ).executeUpdate()
}

@Singleton
class UserGroupMemberRepo @Inject() (
  storeUserRepo: StoreUserRepo,
  userGroupRepo: UserGroupRepo
) {
  val simple = {
    SqlParser.get[Long]("user_group_member.user_group_id") ~
    SqlParser.get[Long]("user_group_member.store_user_id") map {
      case userGroupId~storeUserId => UserGroupMember(UserGroupId(userGroupId), storeUserId)
    }
  }

  def create(
    userGroupId: UserGroupId, storeUserId: Long
  )(
    implicit conn: Connection
  ): UserGroupMember = {
    SQL(
      "insert into user_group_member (user_group_id, store_user_id) values ({userGroupId}, {storeUserId})"
    ).on(
      'userGroupId -> userGroupId.value,
      'storeUserId -> storeUserId
    ).executeUpdate()

    UserGroupMember(userGroupId, storeUserId)
  }

  def remove(
    userGroupId: UserGroupId, storeUserId: Long
  )(
    implicit conn: Connection
  ): Int = SQL(
    "delete from user_group_member where user_group_id = {userGroupId} and store_user_id = {storeUserId}"
  ).on(
    'userGroupId -> userGroupId.value,
    'storeUserId -> storeUserId
  ).executeUpdate()

  val userGroupAndStoreUser = userGroupRepo.simple ~ storeUserRepo.simple map {
    case userGroup~storeUser => (userGroup, storeUser)
  }

  def listByUserGroupId(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy = OrderBy("u.user_name"), userGroupId: UserGroupId
  )(
    implicit conn: Connection
  ): PagedRecords[(UserGroup, StoreUser)] = {
    val list = SQL(
      """
      select * from user_group_member m
      inner join user_group g on m.user_group_id = g.user_group_id
      inner join store_user u on m.store_user_id = u.store_user_id
      where m.user_group_id = {userGroupId}
      """
    ).on(
      'userGroupId -> userGroupId.value
    ).as(
      userGroupAndStoreUser *
    )

    val count = SQL(
      "select count(*) from user_group_member where user_group_id = {userGroupId}"
    ).on(
      'userGroupId -> userGroupId.value
    ).as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, list)
  }
}
