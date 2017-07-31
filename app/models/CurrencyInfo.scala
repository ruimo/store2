package models

import anorm._
import anorm.SqlParser._
import java.util.{Currency, Locale}

import scala.language.postfixOps
import java.sql.Connection
import javax.inject.Inject

import play.api.db.Database

import javax.inject.Singleton

case class CurrencyInfo(id: Long, currencyCode: String) {
  def toCurrency: Currency = Currency.getInstance(currencyCode)
}

@Singleton
class CurrencyRegistry @Inject() (db: Database) {
  lazy val Jpy = apply(1L)
  lazy val Usd = apply(2L)

  val simple = {
    SqlParser.get[Long]("currency.currency_id") ~
    SqlParser.get[String]("currency.currency_code") map {
      case id~code => CurrencyInfo(id, code)
    }
  }

  lazy val registry: Map[Long, CurrencyInfo] = db.withConnection { implicit conn =>
    SQL("select * from currency")
      .as(simple *).map(r => r.id -> r).toMap
  }

  def apply(id: Long): CurrencyInfo = get(id).get

  def get(id: Long): Option[CurrencyInfo] = registry.get(id)
  
  def tableForDropDown(implicit conn: Connection): Seq[(String, String)] =
    SQL(
      "select * from currency order by currency_code"
    ).as(
      simple *
    ).map {
      e => e.id.toString -> e.currencyCode
    }
}
