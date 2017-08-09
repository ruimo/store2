package controllers

import play.Logger

import scala.collection.immutable
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import anorm._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import models.{DirectSqlExec, LoginSession, QueryResult, ShoppingCartItemRepo}
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.db.Database

@Singleton
class DirectSql @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated,
  db: Database,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val SqlPattern = """(?m);$""".r
  val SqlCommentRemovePattern = """(?m)--.*?$""".r

  val directSqlForm = Form(
    mapping(
      "sql" -> text
    )(DirectSqlExec.apply)(DirectSqlExec.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeSuperUser(login) {
      Ok(
        views.html.admin.directSqlIndex(
          directSqlForm, List(),
          directSqlForm, None
        )
      )
    }
  }

  def execute = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      directSqlForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in DirectSql.execute.")
          BadRequest(
            views.html.admin.directSqlIndex(
              formWithErrors, List(),
              directSqlForm, None
            )
          )
        },
        sql => {
          val results = executeSql(sql.sql)
          if (errorExists(results)) {
            BadRequest(
              views.html.admin.directSqlIndex(
                directSqlForm.fill(sql).withError("sql", "SQL error"), results,
                directSqlForm, None
              )
            )
          }
          else {
            Redirect(routes.DirectSql.index).flashing("message" -> "OK")
          }
        }
      )
    }
  }

  def query = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login

    NeedLogin.assumeSuperUser(login) {
      directSqlForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in DirectSql.query.")
          BadRequest(
            views.html.admin.directSqlIndex(
              formWithErrors, List(),
              directSqlForm, None
            )
          )
        },
        sql => {
          val result = executeQuery(sql.sql)
          result match {
            case Success(r) =>
              Ok(
                views.html.admin.directSqlIndex(
                  directSqlForm, List(),
                  directSqlForm.fill(sql), Some(result)
                )
              )
            case Failure(e) =>
              BadRequest(
                views.html.admin.directSqlIndex(
                  directSqlForm, List(),
                  directSqlForm.fill(sql).withError("sql", "SQL error"), Some(result)
                )
              )
          }
        }
      )
    }
  }

  def executeSql(sql: String): Seq[(String, Try[Int])] = {
    val sqlTable = SqlPattern.split(sql)

    db.withConnection { implicit conn =>
      sqlTable.map { s =>
        (s, Try(SQL(removeComment(s)).executeUpdate()))
      }.toSeq
    }
  }

  def removeComment(sql: String): String =
    SqlCommentRemovePattern.replaceAllIn(sql, "")

  def errorExists(result: Seq[(String, Try[Int])]): Boolean = result.exists(_._2.isFailure)

  def executeQuery(sql: String): Try[QueryResult] = {
    db.withConnection { implicit conn =>
      Try {
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery(sql)
        fetch(rs)
      }
    }
  }

  def fetch(rs: ResultSet): QueryResult = {
    val md = rs.getMetaData
    QueryResult(
      columnNames(md),
      rows(md, rs)
    )
  }

  def columnNames(md: ResultSetMetaData): immutable.Seq[String] = {
    val columnCount = md.getColumnCount
    def fetch(idx: Int, buf: immutable.Vector[String]): immutable.Seq[String] =
      if (idx > columnCount) buf
      else fetch(idx + 1, buf :+ md.getColumnName(idx))

    fetch(1, immutable.Vector())
  }

  def rows(md: ResultSetMetaData, rs: ResultSet): immutable.Seq[immutable.Seq[Any]] = {
    def fetch(idx: Int, buf: immutable.Vector[immutable.Seq[Any]]): immutable.Seq[immutable.Seq[Any]] = {
      if (idx > 200) buf
      else {
        if (rs.next()) {
          fetch(idx + 1, buf :+ fetchRow(md, rs))
        }
        else {
          buf
        }
      }
    }

    fetch(1, immutable.Vector())
  }

  def fetchRow(md: ResultSetMetaData, rs: ResultSet): immutable.Seq[Any] = {
    def fetch(idx: Int, buf: immutable.Vector[Any]): immutable.Seq[Any] = {
      if (idx > md.getColumnCount) buf
      else fetch(idx + 1, buf :+ rs.getObject(idx))
    }

    fetch(1, immutable.Vector())
  }
}
