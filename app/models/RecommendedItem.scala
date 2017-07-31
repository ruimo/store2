package models

import anorm._
import anorm.SqlParser
import scala.language.postfixOps
import collection.immutable
import java.sql.Connection
import org.joda.time.DateTime

case class RecommendedItem(
  siteId: Long,
  itemId: Long,
  name: String,
  itemNumericMetadata: Map[ItemNumericMetadataType, ItemNumericMetadata],
  itemTextMetadata: Map[ItemTextMetadataType, ItemTextMetadata],
  siteItemNumericMetadata: Map[SiteItemNumericMetadataType, SiteItemNumericMetadata],
  price: BigDecimal,
  siteName: String
)

object RecommendedItem {
}
