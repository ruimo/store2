package functional

import java.util.Arrays
import org.specs2.runner._
import java.text.SimpleDateFormat
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import SeleniumHelpers.FirefoxJa
import play.api.http.Status
import org.openqa.selenium.By
import java.nio.file.{Paths, Files}
import org.openqa.selenium.JavascriptExecutor
import java.util.concurrent.TimeUnit
import helpers.UrlHelper
import helpers.UrlHelper._
import anorm._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class NewsMaintenanceSpec extends Specification with InjectorSupport {
  val testDir = Files.createTempDirectory(null)
  val testAttachmentDir = Files.createTempDirectory(null)
  lazy val withTempDir = Map(
    "news.picture.path" -> testDir.toFile.getAbsolutePath,
    "news.picture.fortest" -> true
  )

  lazy val avoidLogin = Map(
    "need.authentication.entirely" -> false
  )

  "News maintenace" should {
    "Create news" in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店111")
        val site2 = inject[SiteRepo].createNew(Ja, "商店222")

        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url.addParm("lang", lang.code).toString
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#title_field dd.error").text === Messages("error.required")
        browser.find("#newsContents_field dd.error").text === Messages("error.required")

        browser.find("#releaseDateTextBox_field dd.error").text === Messages("error.localDateTime")

        browser.find("#title").fill().`with`("title01")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents01');")
        browser.find("#releaseDateTextBox").fill().`with`("2016年01月02日")
        browser.find("#siteDropDown option[value='1001']").click()
        browser.find(".createNewsButton").click
        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("createNewsTitle"))
        }
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("editNewsTitle"))
        browser.find(".newsTableBody .title").text === "title01"
        browser.find(".newsTableBody .releaseTime").text === "2016年01月02日"
        browser.find(".newsTableBody .site").text === "商店222"
        browser.find(".newsTableBody .id a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("modifyNewsTitle"))
        browser.find("#title").attribute("value") === "title01"
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("return tinyMCE.activeEditor.getContent();") === "<p>Contents01</p>"
        browser.find("#siteDropDown option[selected='selected']").text === "商店222"
        browser.find("#releaseDateTextBox").attribute("value") === "2016年01月02日"

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".deleteButton").click()
        browser.waitUntil(
          failFalse(browser.find(".no-button").first().displayed())
        )
        browser.find(".no-button").click()
        browser.waitUntil(
          failFalse(browser.find(".no-button").size != 0)
        )

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".deleteButton").click()
        browser.waitUntil(
          failFalse(browser.find(".yes-button").first().displayed())
        )
        browser.find(".yes-button").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".deleteButton").size === 0
      }
    }

    "Browse news" in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ withTempDir)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        val site1 = inject[SiteRepo].createNew(Ja, "商店111")
        val site2 = inject[SiteRepo].createNew(Ja, "商店222")
        val site3 = inject[SiteRepo].createNew(Ja, "商店333")
        val site4 = inject[SiteRepo].createNew(Ja, "商店444")
        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url.addParm("lang", lang.code).toString
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // rec01
        browser.find("#title").fill().`with`("title01")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents01');")
        browser.find("#releaseDateTextBox").fill().`with`("2016年01月02日")
        browser.find("#siteDropDown option[value='1000']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        // rec02
        browser.find("#title").fill().`with`("title02")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents02');")
        browser.find("#releaseDateTextBox").fill().`with`("2016年01月04日")
        browser.find("#siteDropDown option[value='1001']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        // rec03
        browser.find("#title").fill().`with`("title03")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents03');")
        browser.find("#releaseDateTextBox").fill().`with`("2016年01月03日")
        browser.find("#siteDropDown option[value='1002']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        // rec04(Future date)
        val futureDate: String = new SimpleDateFormat("yyyy年MM月dd日").format(
          System.currentTimeMillis + 1000 * 60 * 60 * 24 * 2
        )

        browser.find("#title").fill().`with`("title04")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents03');")
        browser.find("#releaseDateTextBox").fill().`with`(futureDate)
        browser.find("#siteDropDown option[value='1003']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        // In admin console, all news should be shown.
        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("editNewsTitle"))
        browser.find(".newsTableBody .title").index(0).text === "title04"
        browser.find(".newsTableBody .site").index(0).text === "商店444"
        browser.find(".newsTableBody .releaseTime").index(0).text === futureDate
        browser.find(".newsTableBody .title").index(1).text === "title02"
        browser.find(".newsTableBody .site").index(1).text === "商店222"
        browser.find(".newsTableBody .releaseTime").index(1).text === "2016年01月04日"
        browser.find(".newsTableBody .title").index(2).text === "title03"
        browser.find(".newsTableBody .site").index(2).text === "商店333"
        browser.find(".newsTableBody .releaseTime").index(2).text === "2016年01月03日"
        browser.find(".newsTableBody .title").index(3).text === "title01"
        browser.find(".newsTableBody .site").index(3).text === "商店111"
        browser.find(".newsTableBody .releaseTime").index(3).text === "2016年01月02日"

        // In normal console, future news should be hidden.
        browser.goTo(
          controllers.routes.NewsQuery.list().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTitle a").index(0).text === "title02"
        browser.find(".newsSite").index(0).text === "商店222"
        browser.find(".newsReleaseDate").index(0).text === "2016年01月04日"
        browser.find(".newsTitle a").index(1).text === "title03"
        browser.find(".newsSite").index(1).text === "商店333"
        browser.find(".newsReleaseDate").index(1).text === "2016年01月03日"
        browser.find(".newsTitle a").index(2).text === "title01"
        browser.find(".newsSite").index(2).text === "商店111"
        browser.find(".newsReleaseDate").index(2).text === "2016年01月02日"

        browser.find(".newsTitle a").index(2).click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        val currentWindow = browser.webDriver.getWindowHandle
        val allWindows = browser.webDriver.getWindowHandles
        allWindows.remove(currentWindow)
        browser.webDriver.switchTo().window(allWindows.iterator.next)

        browser.waitUntil(
          browser.webDriver.getTitle == Messages("commonTitle", Messages("news"))
        )
        browser.waitUntil(
          failFalse(browser.find(".newsTitle").text == "title01")
        )
        browser.find(".newsReleaseDate").text === "2016年01月02日"
        browser.find(".newsSite").text === "商店111"
        browser.find(".newsContents").text === "Contents01"

        // Paging
        browser.goTo(
          controllers.routes.NewsQuery.pagedList().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".newsTable .body").size === 3

        browser.find(".newsTable .body .date").index(0).text === "2016年01月04日"
        browser.find(".newsTable .body .newsCreatedUser").index(0).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(0).text === "title02"
        browser.find(".newsTable .body .site").index(0).text === "商店222"

        browser.find(".newsTable .body .date").index(1).text === "2016年01月03日"
        browser.find(".newsTable .body .newsCreatedUser").index(1).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(1).text === "title03"
        browser.find(".newsTable .body .site").index(1).text === "商店333"

        browser.find(".newsTable .body .date").index(2).text === "2016年01月02日"
        browser.find(".newsTable .body .newsCreatedUser").index(2).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(2).text === "title01"
        browser.find(".newsTable .body .site").index(2).text === "商店111"

        browser.goTo(
          controllers.routes.NewsQuery.pagedList(page = 0, pageSize = 2).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 2

        browser.find(".newsTable .body .date").index(0).text === "2016年01月04日"
        browser.find(".newsTable .body .newsCreatedUser").index(0).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(0).text === "title02"
        browser.find(".newsTable .body .site").index(0).text === "商店222"

        browser.find(".newsTable .body .date").index(1).text === "2016年01月03日"
        browser.find(".newsTable .body .newsCreatedUser").index(1).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(1).text === "title03"
        browser.find(".newsTable .body .site").index(1).text === "商店333"

        browser.find(".nextPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 1

        browser.find(".newsTable .body .date").index(0).text === "2016年01月02日"
        browser.find(".newsTable .body .newsCreatedUser").index(0).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(0).text === "title01"
        browser.find(".newsTable .body .site").index(0).text === "商店111"

        browser.find(".prevPageButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".newsTable .body").size === 2

        browser.find(".newsTable .body .date").index(0).text === "2016年01月04日"
        browser.find(".newsTable .body .newsCreatedUser").index(0).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(0).text === "title02"
        browser.find(".newsTable .body .site").index(0).text === "商店222"

        browser.find(".newsTable .body .date").index(1).text === "2016年01月03日"
        browser.find(".newsTable .body .newsCreatedUser").index(1).text === "Admin Manager"
        browser.find(".newsTable .body .title").index(1).text === "title03"
        browser.find(".newsTable .body .site").index(1).text === "商店333"
      }
    }

    "If normalUserCanCreateNews is false, login needed" in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(
        inMemoryDatabase() ++ withTempDir +
          ("normalUserCanCreateNews" -> false)
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        createNormalUser(
          browser, "user01", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        logoff(browser)
        login(browser, "user01", "password01")

        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url
        )

        // Because logged in with normal user, redirected to top page.
        browser.waitUntil {
          browser.webDriver.getTitle.trim == Messages("commonTitle", Messages("company.name")).trim
        }
      }
    }

    "If normalUserCanCreateNews is true, normal user can start creating news" in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(
        inMemoryDatabase() ++ withTempDir +
          ("normalUserCanCreateNews" -> true)
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        createNormalUser(
          browser, "user01", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        logoff(browser)

        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url
        )
        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("loginTitle"))
        }

        login(browser, "user01", "password01")

        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url
        )

        // Because logged in with normal user, redirected to top page.
        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("createNewsTitle"))
        }
      }
    }

    "Normal user can create news if normalUserCanCreateNews is true" in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(inMemoryDatabase() ++ withTempDir ++ avoidLogin + (
        ("normalUserCanCreateNews" -> true)
      ))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        loginWithTestUser(browser)
        createNormalUser(
          browser, "user01", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        logoff(browser)

        val site1 = inject[SiteRepo].createNew(Ja, "商店111")
        val site2 = inject[SiteRepo].createNew(Ja, "商店222")

        login(browser, "user01", "password01")
        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url
        )

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#title_field dd.error").text === Messages("error.required")
        browser.find("#newsContents_field dd.error").text === Messages("error.required")

        browser.find("#releaseDateTextBox_field dd.error").text === Messages("error.localDateTime")

        browser.find("#title").fill().`with`("title01")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents01');")
        browser.find("#releaseDateTextBox").fill().`with`("2016年01月02日")
        browser.find("#siteDropDown option[value='1001']").click()
        browser.find(".createNewsButton").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("createNewsTitle"))
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.webDriver.getTitle === Messages("commonTitle", Messages("editNewsTitle"))
        browser.find(".newsTableBody .title").text === "title01"
        browser.find(".newsTableBody .releaseTime").text === "2016年01月02日"
        browser.find(".newsTableBody .site").text === "商店222"
        browser.find(".newsTableBody .id a").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("modifyNewsTitle"))
        browser.find("#title").attribute("value") === "title01"
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("return tinyMCE.activeEditor.getContent();") === "<p>Contents01</p>"
        browser.find("#siteDropDown option[selected='selected']").text === "商店222"
        browser.find("#releaseDateTextBox").attribute("value") === "2016年01月02日"

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".deleteButton").click()
        browser.waitUntil(
          failFalse(browser.find(".no-button").first().displayed())
        )
        browser.find(".no-button").click()
        browser.waitUntil(
          failFalse(browser.find(".no-button").size != 0)
        )

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".deleteButton").click()
        browser.waitUntil(
          failFalse(browser.find(".yes-button").first().displayed())
        )
        browser.find(".yes-button").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".deleteButton").size === 0
      }
    }

    "Normal user can modify his/her own news" in new WithBrowser(
      WebDriverFactory(FIREFOX),
      appl(
        inMemoryDatabase() ++ withTempDir ++ avoidLogin +
          ("normalUserCanCreateNews" -> true)
      )
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val adminUser = loginWithTestUser(browser)
        createNormalUser(
          browser, "user00", "password00", "user00@mail.xxx", "firstName00", "lastName00", "company00"
        )
        createNormalUser(
          browser, "user01", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        logoff(browser)
        login(browser, "user00", "password00")

        val site1 = inject[SiteRepo].createNew(Ja, "商店111")
        val site2 = inject[SiteRepo].createNew(Ja, "商店222")

        browser.goTo(
          controllers.routes.NewsMaintenance.startCreateNews().url.addParm("lang", lang.code).toString
        )

        browser.find("#title").fill().`with`("title01")
        browser.webDriver.asInstanceOf[JavascriptExecutor].executeScript("tinyMCE.activeEditor.setContent('Contents01');")
        browser.find("#releaseDateTextBox").fill().`with`("2016年01月02日")
        browser.find("#siteDropDown option[value='1001']").click()
        browser.find(".createNewsButton").click
        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("createNewsTitle"))
        }
        browser.find(".globalErrorMessage").size === 0
        browser.find(".message").text === Messages("newsIsCreated")

        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )

        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("editNewsTitle"))
        }

        browser.find(".newsTableBody .title").text === "title01"
        browser.find(".newsTableBody .releaseTime").text === "2016年01月02日"
        browser.find(".newsTableBody .site").text === "商店222"
        val newsId = browser.find(".newsTableBody .id a").text.toLong
        browser.find(".newsTableBody .id a").click()
        browser.waitUntil {
          browser.webDriver.getTitle === Messages("commonTitle", Messages("modifyNewsTitle"))
        }

        logoff(browser)
        login(browser, "user01", "password01")

        // user01 cannot modify news owned by user00.
        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url.addParm("lang", lang.code).toString
        )
        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("editNewsTitle"))
        }
        browser.find(".newsTableBody").size === 0

        // user01 cannot modify news owned by user00. Should navigate to top.
        browser.goTo(
          controllers.routes.NewsMaintenance.modifyNewsStart(newsId).url
        )
        browser.waitUntil {
          browser.webDriver.getTitle.trim == Messages("commonTitle", Messages("company.name")).trim
        }

        // Login with super user
        logoff(browser)
        login(browser, "administrator", "password")

        // super user can modify news owned by user00.
        browser.goTo(
          controllers.routes.NewsMaintenance.editNews().url
        )
        browser.waitUntil {
          browser.webDriver.getTitle == Messages("commonTitle", Messages("editNewsTitle"))
        }
        browser.find(".newsTableBody").size === 1

        // super user can modify news owned by user00.
        browser.goTo(
          controllers.routes.NewsMaintenance.modifyNewsStart(newsId).url
        )

        browser.waitUntil {
          browser.find("#title").first().displayed()
        }
        browser.find("#title").fill().`with`("title02")
        browser.find(".updateButton").click()

        browser.waitUntil {
          browser.webDriver.getTitle === Messages("commonTitle", Messages("editNewsTitle"))
        }
        browser.find(".message").text === Messages("newsIsUpdated")
        browser.find(".newsTableBody .title").text === "title02"
      }

      // Logoff and list news.
      logoff(browser)
      browser.goTo(
        controllers.routes.NewsQuery.list().url
      )

      browser.waitUntil {
        browser.find(".newsList tr").first().displayed()
      }

      browser.find(".newsList tr .newsTitle a").text === "title02"
      browser.find(".newsCreatedUser").text === "firstName00 lastName00"
    }
  }
}
