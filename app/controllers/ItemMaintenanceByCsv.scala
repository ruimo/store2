package controllers

import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc._

import scala.annotation.tailrec
import java.nio.file.Path

import com.ruimo.scoins.Zip
import java.nio.file.Files

import com.ruimo.csv

import scala.io.{Codec, Source}
import com.ruimo.scoins.LoanPattern._

import scala.util.Try
import models._
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import helpers.Cache
import play.api.db.Database
import play.api.libs.Files.TemporaryFile

@Singleton
class ItemMaintenanceByCsv @Inject() (
  cc: MessagesControllerComponents,
  cache: Cache,
  authenticated: Authenticated,
  loginSessionRepo: LoginSessionRepo,
  itemPictures: ItemPictures,
  implicit val db: Database,
  implicit val itemCsvRepo: ItemCsvRepo,
  implicit val storeUserRepo: StoreUserRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  class TooManyItemsInCsvException extends Exception

  val MaxItemCount: () => Int = cache.config(
    _.getOptional[Int]("itemCsvMaxLineCount").getOrElse(100)
  )
  val DefaultCsvCodec = Codec("Windows-31j")
  val CsvFileName = "items.csv"

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.uploadItemCsv())
    }
  }

  def uploadZip() = Action(parse.multipartFormData) { implicit request: MessagesRequest[MultipartFormData[TemporaryFile]] =>
    val login = db.withConnection { implicit conn =>
      loginSessionRepo.fromRequest(request)
    }
    login match {
      case None => NeedLogin.onUnauthorized(request)
      case Some(user) =>
        if (! user.isAdmin) NeedLogin.onUnauthorized(request)
        else {
          request.body.file("zipFile").map { zipFile =>
            val filename = zipFile.filename
            if (! zipFile.contentType.map(isZip).getOrElse(false)) {
              Logger.error("Zip file '" + filename + "' has content type '" + zipFile.contentType.getOrElse("") + "'")
              Redirect(
                routes.ItemMaintenanceByCsv.index
              ).flashing("errorMessage" -> Messages("zip.needed"))
            }
            else {
              try {
                val recordCount = db.withConnection { implicit conn =>
                  processItemCsv(zipFile.ref.path)
                }
                Redirect(
                  routes.ItemMaintenanceByCsv.index
                ).flashing("message" -> Messages("itemCsvSuccess", recordCount))
              }
              catch {
                case e: ItemCsv.InvalidColumnException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.column", e.lineNo, e.colNo, e.value)
                  )
                case e: ItemCsv.NoLangDefException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.nolang", e.lineNo)
                  )
                case e: ItemCsv.InvalidSiteException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.site", e.lineNo)
                  )
                case e: ItemCsv.InvalidCategoryException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.category", e.lineNo)
                  )
                case e: ItemCsv.InvalidLocaleException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.locale", e.lineNo)
                  )
                case e: ItemCsv.InvalidTaxException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.tax", e.lineNo)
                  )
                case e: ItemCsv.InvalidCurrencyException =>
                  Logger.error("Error in item csv.", e)
                  Redirect(routes.ItemMaintenanceByCsv.index).flashing(
                    "errorMessage" -> Messages("itemcsv.invalid.currency", e.lineNo)
                  )
              }
            }
          }.getOrElse {
            Redirect(routes.ItemMaintenanceByCsv.index).flashing(
              "errorMessage" -> Messages("file.not.found")
            )
          }
        }
    }
  }

  def processItemCsv(zip: Path, csvCodec: Codec = DefaultCsvCodec)(implicit conn: Connection): Int = {
    val explodeDir = Files.createTempDirectory(null)
    Zip.explode(zip, explodeDir).map { files =>
      val csvFile: Path = explodeDir.resolve(CsvFileName)
      val processedCount: Try[Int] = using(Source.fromFile(csvFile.toFile)(csvCodec)) { src =>
        val csvLines: Iterator[Try[Seq[String]]] = csv.Parser.parseLines(src.toIterator)
        @tailrec def persist(locale: Option[LocaleInfo], lineNo: Int, itemCount: Int): Int = {
          if (itemCount > MaxItemCount())
            throw new TooManyItemsInCsvException

          if (csvLines.hasNext) {
            val csvLine: Seq[String] = csvLines.next().get
            persist(
              itemCsvRepo.processOneLine(
                lineNo,
                explodeDir, locale, csvLine.toIterator, conn,
                (itemId, no) => itemPictures.toPath(itemId.id, no),
                itemId => itemPictures.toDetailPath(itemId.id)
              ),
              lineNo + 1,
              itemCount + 1
            )
          }
          else lineNo
        }

        persist(None, 1, 0)
      } (_.close())
      processedCount.get - 1
    }.get
  }

  def isZip(contentType: String): Boolean = contentType match {
    case "application/x-zip-compressed" => true
    case "application/x-zip" => true
    case "application/zip" => true
    case _ => false
  }
}

