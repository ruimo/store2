package helpers

import org.openqa.selenium.{By, Keys}
import java.time.Instant
import play.api.Application
import java.sql.Connection
import java.util.concurrent.TimeUnit
import models.{UserRole, StoreUser, SiteUser, StoreUserRepo, SiteUserRepo}
import play.api.test.TestBrowser
import java.io.{ByteArrayOutputStream, InputStreamReader, BufferedReader, InputStream}
import java.net.{HttpURLConnection, URL}
import collection.mutable.ListBuffer
import annotation.tailrec

class HelperBase {
  val disableMailer = Map("disable.mailer" -> true)

  // password == password
  def createTestUser(implicit storeUserRepo: StoreUserRepo, conn: Connection): StoreUser = storeUserRepo.create(
    "administrator", "Admin", None, "Manager", "admin@abc.com",
    4151208325021896473L, -1106301469931443100L, UserRole.ADMIN, Some("Company1"), stretchCount = 1
  )

  // password == password
  def createStoreOwner(
    name: String,
    firstName: String = "firstName",
    middleName: Option[String] = None,
    lastName: String = "lastName",
    siteId: Long
  )(
    implicit storeUserRepo: StoreUserRepo,
    siteUserRepo: SiteUserRepo,
    conn: Connection
  ): (StoreUser, SiteUser) = {
    val storeUser = storeUserRepo.create(
      name, firstName, middleName, lastName, "admin@abc.com",
      4151208325021896473L, -1106301469931443100L, UserRole.NORMAL, Some("Company1"), stretchCount = 1
    )

    val siteUser = siteUserRepo.createNew(storeUser.id.get, siteId)

    (storeUser, siteUser)
  }

  def loginWithTestUser(browser: TestBrowser)(implicit storeUserRepo: StoreUserRepo, conn: Connection): StoreUser = {
    val user = createTestUser
    browser.goTo(controllers.routes.Admin.index.url)
    browser.find("#userName").fill().`with`("administrator")
    browser.find("#password").fill().`with`("password")
    browser.find("#doLoginButton").click()
    browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    user
  }

  def login(browser: TestBrowser, userName: String, password: String) {
    browser.goTo(controllers.routes.Admin.index.url)
    browser.find("#userName").fill().`with`(userName)
    browser.find("#password").fill().`with`(password)
    browser.find("#doLoginButton").click()
    browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
  }

  def logoff(browser: TestBrowser) {
    browser.goTo(controllers.routes.Admin.logoff("/").url)
    browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
  }

  def createNormalUser(
    browser: TestBrowser,
    userName: String,
    password: String,
    email: String,
    firstName: String,
    lastName: String,
    companyName: String
  ) {
    browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser.url)
    browser.find("#userName").fill().`with`(userName)
    browser.find("#firstName").fill().`with`(firstName)
    browser.find("#lastName").fill().`with`(lastName)
    browser.find("#companyName").fill().`with`(companyName)
    browser.find("#email").fill().`with`(email)
    browser.find("#password_main").fill().`with`(password)
    browser.find("#password_confirm").fill().`with`(password)
    browser.webDriver
      .findElement(By.id("registerNormalUser"))
      .sendKeys(Keys.ENTER)
    browser.waitUntil(
      failFalse(browser.find(".message").first().displayed())
    )
  }

  def takeScreenShot(browser: TestBrowser) {
    val stack = (new Throwable()).getStackTrace()(1)
    val fname = "screenShots/" + stack.getFileName() + "_" + stack.getLineNumber() + ".png"
    browser.takeScreenShot(fname)
  }

  def downloadString(urlString: String): (Int, String) = downloadString(None, urlString)
  def downloadBytes(urlString: String): (Int, Array[Byte]) = downloadBytes(None, urlString)

  def download[T](ifModifiedSince: Option[Long], urlString: String)(f: InputStream => T): (Int, T) = {
    val url = new URL(urlString)
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    try {
      conn.setUseCaches(false)
      if (ifModifiedSince.isDefined) conn.setIfModifiedSince(ifModifiedSince.get)
      (conn.getResponseCode, f(conn.getInputStream))
    }
    finally {
      conn.disconnect()
    }
  }

  def downloadString(ifModifiedSince: Option[Long], urlString: String): (Int, String) =
    download(ifModifiedSince, urlString) { is =>
      val br = new BufferedReader(new InputStreamReader(is, "UTF-8"))
      val buf = new StringBuilder()
      readFully(buf, br)
      buf.toString
    }

  def downloadBytes(ifModifiedSince: Option[Long], urlString: String): (Int, Array[Byte]) =
    download(ifModifiedSince, urlString) { is =>
      def reader(buf: ByteArrayOutputStream): ByteArrayOutputStream = {
        val c = is.read
        if (c == -1) buf
        else {
          buf.write(c)
          reader(buf)
        }
      }

      reader(new ByteArrayOutputStream).toByteArray
    }

  def readFully(buf: StringBuilder, br: BufferedReader) {
    val s = br.readLine()
    if (s == null) return
    buf.append(s)
    readFully(buf, br)
  }

  def readFully(br: BufferedReader): Seq[String] = {
    @tailrec def readFully(buf: ListBuffer[String]): List[String] = {
      val line = br.readLine
      if (line == null) buf.toList
      else {
        buf += line
        readFully(buf)
      }
    }

    readFully(new ListBuffer[String])
  }

  // Trim consecutive more than one space char to single space character.
  def justOneSpace(s: String): String = {
    var buf = new StringBuilder
    var spaceFound = false

    s.foreach { c =>
      if (spaceFound) {
        if (c != ' ') {
          spaceFound = false
          buf.append(c)
        }
      }
      else {
        if (c == ' ') {
          spaceFound = true
        }
        buf.append(c)
      }
    }

    buf.toString
  }

  def date(s: String): Instant = Instant.ofEpochMilli(java.sql.Date.valueOf(s).getTime)
  def toDate(ins: Instant): java.util.Date = new java.util.Date(ins.toEpochMilli)
  def failFalse(f: => Boolean) = try { f } catch { case t: Throwable => false }
}
