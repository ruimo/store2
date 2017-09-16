package helpers

import models.{SiteUser, StoreUser, UserRole}
import play.api.test.TestBrowser
import java.io.{BufferedReader, ByteArrayOutputStream, InputStream, InputStreamReader}
import java.net.{HttpURLConnection, URL}

import org.openqa.selenium.chrome.ChromeDriver

import collection.mutable.ListBuffer
import annotation.tailrec

object Helper extends HelperBase {
  val CHROME = classOf[ChromeDriver]
}
