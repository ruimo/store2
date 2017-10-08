package models

import javax.inject.{Inject, Singleton}
import anorm._
import scala.language.postfixOps
import collection.immutable.{HashMap, IntMap}
import java.sql.{Connection}
import scala.collection.{immutable => imm}

case class EmployeeId(value: Long) extends AnyVal

case class Employee(id: Option[EmployeeId] = None, siteId: Long, userId: Long, index: Int)

@Singleton
class EmployeeRepo @Inject() (
  val siteRepo: SiteRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("employee.employee_id") ~
    SqlParser.get[Long]("employee.site_id") ~
    SqlParser.get[Long]("employee.store_user_id") ~
    SqlParser.get[Int]("employee.index") map {
      case id~siteId~userId~index => Employee(id.map(EmployeeId.apply), siteId, userId, index)
    }
  }

  val withSite = simple ~ SiteRepo.simple map {
    case employee~site => (employee, site)
  }

  def apply(id: EmployeeId)(implicit conn: Connection): Employee =
    SQL(
      "select * from employee where employee_id = {id}"
    ).on(
      'id -> id.value
    ).executeQuery.as(simple.single)

  def createNew(siteId: Long, userId: Long)(implicit conn: Connection): Employee = {
    val index: Int = SQL(
      "select coalesce(max(index), 0) from employee where store_user_id = {userId}"
    ).on(
      'userId -> userId
    ).as(SqlParser.scalar[Int].single) + 1

    SQL(
      """
      insert into employee (employee_id, site_id, store_user_id, index) values (
        (select nextval('employee_seq')), {siteId}, {userId}, {index}
      )
      """
    ).on(
      'siteId -> siteId,
      'userId -> userId,
      'index -> index
    ).executeUpdate()

    val employeeId = SQL("select currval('employee_seq')").as(SqlParser.scalar[Long].single)

    Employee(Some(EmployeeId(employeeId)), siteId, userId, index)
  }

  def list(userId: Long)(implicit conn: Connection): Seq[(Employee, Site)] = SQL(
    """
    select * from employee e
    inner join site s on e.site_id = s.site_id
    where e.store_user_id = {userId}
    order by e.index
    """
  ).on(
    'userId -> userId
  ).as(
    withSite *
  )

  def remove(id: EmployeeId)(implicit conn: Connection) {
    SQL(
      "delete from employee where employee_id = {id}"
    ).on(
      'id -> id.value
    ).executeUpdate()
  }

  def getBelonging(userId: Long)(implicit conn: Connection): Option[Employee] = SQL(
    """
    select * from employee where store_user_id = {userId} order by index limit 1
    """
  ).on(
    'userId -> userId
  ).as(
    simple.singleOpt
  )

  def belongings(userId: Long)(implicit conn: Connection): Seq[Employee] = SQL(
    """
    select * from employee where store_user_id = {userId} order by index
    """
  ).on(
    'userId -> userId
  ).as(
    simple *
  )

  // If you use this method, prepare connection by db.withTransaction.
  // If update count (return value is equal to zero), rollback the transaction.
  def swapIndicies(userId: Long, index0: Int, index1: Int)(implicit conn: Connection): Int = {
    if (
      SQL(
        "update employee set index=-1 where store_user_id={userId} and index={index}"
      ).on(
        'userId -> userId,
        'index -> index0
      ).executeUpdate() == 0
    ) return 0

    if (
      SQL(
        "update employee set index={indexTo} where store_user_id={userId} and index={indexFrom}"
      ).on(
        'userId -> userId,
        'indexTo -> index0,
        'indexFrom -> index1
      ).executeUpdate() == 0
    ) return 0

    SQL(
      "update employee set index={index} where store_user_id={userId} and index=-1"
    ).on(
      'userId -> userId,
      'index -> index1
    ).executeUpdate()
  }

  def siteTable(userId: Long)(implicit login: LoginSession, conn: Connection): Seq[(String, String)] = {
    val table: Seq[(String, String)] = siteRepo.tableForDropDown
    val bel = belongings(userId).map(_.siteId).toSet

    table.filter { t =>
      ! bel.contains(t._1.toLong)
    }
  }
}
