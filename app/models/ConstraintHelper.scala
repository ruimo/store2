package models

import javax.inject.Inject

import play.api.db.Database

import scala.collection.concurrent.TrieMap

class ConstraintHelper @Inject() (
  db: Database
) {
  case class ColumnSize(schema: Option[String], table: String, column: String) {
    require(schema != null, "schema should not be null.")
    require(table != null, "table should not be null.")
    require(column != null, "column should not be null.")

    val columnSize = db.withConnection { implicit conn =>
      val md = conn.getMetaData()
      val cols = md.getColumns(null, schema.orNull, table, column)
      if(cols.next())
        cols.getInt("COLUMN_SIZE")
      else 
        throw new Error("Database metadata does not have 'COLUMN_SIZE'!")
    }
  }
  
  val columnSizes = TrieMap[ColumnSize, ColumnSize]()

  def getColumnSize(schema: Option[String], table: String, column: String) : Int = {
    val col = ColumnSize(schema, table, column)
    columnSizes.putIfAbsent(col, col).getOrElse(col).columnSize
  }

  def refreshColumnSizes() = {
    columnSizes.clear()
  }
}
