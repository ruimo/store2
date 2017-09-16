package models

import javax.inject.Inject
import javax.inject.Singleton

import play.api.{Configuration, Logger}
import scala.collection.{immutable => imm}

case class FileCategory(
  value: String,
  menuText: String
)

@Singleton
class FileCategories @Inject() (conf: Configuration) {
  val values: imm.Seq[FileCategory] = conf.get[Seq[Configuration]]("fileCategories").map { e =>
    FileCategory(
      e.get[String]("value"), e.get[String]("menuText")
    )
  }.toList
}
