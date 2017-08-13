package models

import anorm._

import java.sql.Connection

object TestHelper {
  def removePreloadedRecords(implicit conn: Connection) {
    SQL("delete from item_numeric_metadata").executeUpdate()
    SQL("delete from site_item_numeric_metadata").executeUpdate()
    SQL("delete from item_description").executeUpdate()
    SQL("delete from site_item").executeUpdate()
    SQL("delete from item_name").executeUpdate()
    SQL("delete from item").executeUpdate()
    SQL("delete from category_name").executeUpdate()
    SQL("delete from category_path").executeUpdate()
    SQL("delete from site_category").executeUpdate()
    SQL("delete from category").executeUpdate()
    SQL("delete from shipping_fee_history").executeUpdate()
    SQL("delete from shipping_fee").executeUpdate()
    SQL("delete from shipping_box").executeUpdate()
    SQL("delete from site").executeUpdate()
    SQL("delete from tax").executeUpdate()
  }
}
