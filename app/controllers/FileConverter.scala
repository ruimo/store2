package controllers

import java.nio.file.{Files, StandardCopyOption}
import java.sql.Connection
import scala.sys.process.Process
import models._
import java.time.{ZonedDateTime, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import play.api.libs.json._
import scala.concurrent.Await
import play.api.{Configuration, Logger}
import javax.inject._
import com.ruimo.scoins.LoanPattern.using
import com.ruimo.scoins.PathUtil
import java.util.Enumeration
import java.util.Collections
import java.net.InetAddress
import java.net.NetworkInterface
import scala.collection.JavaConversions._
import play.api.libs.ws._
import play.api.libs.functional.syntax._
import scala.concurrent.duration._
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.db.Database

@Singleton
class FileConverter @Inject() (
  system: ActorSystem,
  fileConversionStatusRepo: FileConversionStatusRepo,
  fileServer: FileServer,
  uploadedFileRepo: UploadedFileRepo,
  implicit val db: Database
) {
  system.scheduler.schedule(
    0.milliseconds,
    scala.concurrent.duration.Duration(10, SECONDS)
  ) {
    Logger.info("File conversion start")
    try {
      doJob()
    }
    catch {
      case t: Throwable =>
        Logger.error("Error in converting file", t)
    }
    Logger.info("File conversion end")
  }

  def doJob() {
    db.withConnection { implicit conn =>
      fileConversionStatusRepo.list().foreach { rec =>
        Logger.info("Convert start " + rec)
        try {
          fileConversionStatusRepo.update(rec.id.get, FileConversionStatusValue.CONVERTING)
          convertFile(rec)
          fileConversionStatusRepo.update(rec.id.get, FileConversionStatusValue.COMPLETED)
        }
        catch {
          case t: Throwable =>
            Logger.error("Convert failed.", t)
            fileConversionStatusRepo.update(rec.id.get, FileConversionStatusValue.ERROR)
        }
        Logger.info("Convert end " + rec)
      }
    }
  }

  def changeExtention(fileName: String, ext: String): String = {
    val idx = fileName.lastIndexOf('.')
    if (idx == -1) fileName
    else fileName.substring(0, idx) + "." + ext
  }

  def convertFile(rec: FileConversionStatus)(implicit conn: Connection) {
    import scala.language.postfixOps

    uploadedFileRepo.get(rec.uploadedFileId) match {
      case Some(uploadedFile) =>
        val path = fileServer.toFilePath(rec.uploadedFileId)
        val pathString = path.toAbsolutePath.toString
        PathUtil.withTempFile(None, Some(".mp4")) { mp4Path =>
          val mp4 = mp4Path.toAbsolutePath.toString
          val cmd = s"ffmpeg -y -i $pathString -movflags faststart -vcodec libx264 -acodec aac -strict experimental $mp4"
          Logger.info("Invoking [" + cmd + "]")
          val rc = ((Process(cmd) run) exitValue())
          if (rc != 0) throw new RuntimeException("File conversion failed(rc = " + rc + ")")

          Files.copy(mp4Path, path, StandardCopyOption.REPLACE_EXISTING)
          uploadedFileRepo.update(
            uploadedFile.copy(
              fileName = changeExtention(uploadedFile.fileName, "mp4"),
              contentType = Some("video/mp4")
            )
          )
        }.get
      case None =>
        Logger.error("Cannot find file to convert. Ignored." + rec)
    }
  }
}
