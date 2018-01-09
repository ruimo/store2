package functional

import play.api.{Application => PlayApp}
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import views.Titles
import helpers.Formatter
import helpers.UrlHelper
import helpers.UrlHelper._
import helpers.PasswordHash
import constraints.FormConstraints
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection
import java.util.concurrent.TimeUnit

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import play.api.test.TestServer
import org.openqa.selenium.By
import models._
import com.ruimo.scoins.Scoping._
import SeleniumHelpers.htmlUnit
import SeleniumHelpers.FirefoxJa

class EmployeeUserMaintenanceSpec extends Specification with InjectorSupport {
  val disableEmployeeMaintenance = Map("siteOwnerCanEditEmployee" -> false)
  val enableEmployeeMaintenance = Map("siteOwnerCanEditEmployee" -> true)

  def createNormalUser(userName: String = "administrator")(implicit conn: Connection, app: PlayApp): StoreUser =
    inject[StoreUserRepo].create(
      userName, "Admin", None, "Manager", "admin@abc.com",
      4151208325021896473L, -1106301469931443100L, UserRole.NORMAL, Some("Company1")
    )

  def login(browser: TestBrowser, userName: String = "administrator") {
    browser.goTo(controllers.routes.Admin.index.url)
    browser.find("#userName").fill().`with`(userName)
    browser.find("#password").fill().`with`("password")
    browser.find("#doLoginButton").click()
  }

  "Employee user" should {
    "Employee editing is disabled." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ disableEmployeeMaintenance)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = createNormalUser()
        val site = inject[SiteRepo].createNew(Ja, "店舗1")
        val siteUser = inject[SiteUserRepo].createNew(user.id.get, site.id.get)
        login(browser)

        browser.goTo(
          controllers.routes.UserMaintenance.startCreateNewEmployeeUser().url +
          "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        
        // Since employee maintenance is disabled, redirected to top.
        browser.webDriver.getTitle() === Messages("commonTitle", Titles.top).trim
      }
    }

    "Employee editing is enabled." in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase() ++ enableEmployeeMaintenance)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = createNormalUser()
        val site = inject[SiteRepo].createNew(Ja, "店舗1")
        val siteUser = inject[SiteUserRepo].createNew(user.id.get, site.id.get)
        login(browser)

        browser.goTo(
          controllers.routes.UserMaintenance.startCreateNewEmployeeUser().url +
          "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))

        // Check validation error.
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#userName_field .error").text === Formatter.validationErrorString(
          inject[FormConstraints].normalUserNameConstraint(), ""
        )
        browser.find("#password_main_field .error").text === 
          Messages("error.minLength", inject[FormConstraints].passwordMinLength())

        // Confirm password does not match.
        browser.find("#userName").fill().`with`("12345678")
        browser.find("#password_main").fill().`with`("abcdefgh")
        browser.find("#password_confirm").fill().`with`("abcdefgh1")
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#password_confirm_field .error").text === Messages("confirmPasswordDoesNotMatch")

        browser.find("#userName").fill().`with`("12345678")
        browser.find("#password_main").fill().`with`("abcdefgh")
        browser.find("#password_confirm").fill().`with`("abcdefgh")
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))
        browser.find(".message").text === Messages("userIsCreated")

        // store_user table should be updated.
        doWith(inject[StoreUserRepo].findByUserName(site.id.get + "-12345678").get) { user =>
          user.firstName === ""
          user.passwordHash === PasswordHash.generate("abcdefgh", user.salt)
          user.companyName === Some(site.name)

          // employee table should be updated.
          doWith(inject[EmployeeRepo].getBelonging(user.id.get).get) { emp =>
            emp.userId === user.id.get
            emp.siteId === site.id.get
          }
        }
      }
    }

    "User name pattern error." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ enableEmployeeMaintenance + ("normalUserNamePattern" -> "[0-9]{6}"))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = createNormalUser()
        val site = inject[SiteRepo].createNew(Ja, "店舗1")
        val siteUser = inject[SiteUserRepo].createNew(user.id.get, site.id.get)
        login(browser)

        browser.goTo(
          controllers.routes.UserMaintenance.startCreateNewEmployeeUser().url +
          "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))

        browser.find("#userName").fill().`with`("abcdef")
        browser.find("#password_main").fill().`with`("abcdefgh")
        browser.find("#password_confirm").fill().`with`("abcdefgh")
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))
        browser.find("#userName_field dd.error").text === Messages("normalUserNamePatternError")

        browser.find("#userName").fill().`with`("12345")
        browser.find("#password_main").fill().`with`("abcdefgh")
        browser.find("#password_confirm").fill().`with`("abcdefgh")
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))
        browser.find("#userName_field dd.error").text === Messages("normalUserNamePatternError")

        browser.find("#userName").fill().`with`("1234567")
        browser.find("#password_main").fill().`with`("abcdefgh")
        browser.find("#password_confirm").fill().`with`("abcdefgh")
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))
        browser.find("#userName_field dd.error").text === Messages("normalUserNamePatternError")

        browser.find("#userName").fill().`with`("123456")
        browser.find("#password_main").fill().`with`("abcdefgh")
        browser.find("#password_confirm").fill().`with`("abcdefgh")
        browser.find("#registerEmployee").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("createEmployeeTitle"))
        browser.find(".message").text === Messages("userIsCreated")

        // store_user table should be updated.
        doWith(inject[StoreUserRepo].findByUserName(site.id.get + "-123456").get) { user =>
          user.firstName === ""
          user.passwordHash === PasswordHash.generate("abcdefgh", user.salt)
          user.companyName === Some(site.name)

          // employee table should be updated.
          doWith(inject[EmployeeRepo].getBelonging(user.id.get).get) { emp =>
            emp.userId === user.id.get
            emp.siteId === site.id.get
          }
        }
      }
    }

    // Since employee maintenance is disabled, redirected to top
    "Login with super user. Since super user cannot edit employee, page is redirected to top." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ disableEmployeeMaintenance)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)

        browser.goTo(
          controllers.routes.UserMaintenance.startCreateNewEmployeeUser().url +
          "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Titles.top).trim
      }
    }

    // Since employee maintenance is disabled, redirected to top
    "Login with super user. Since super user cannot edit employee, page is redirected to top." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ disableEmployeeMaintenance)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val site01 = inject[SiteRepo].createNew(Ja, "店舗1")
        val superUser = loginWithTestUser(browser)
        val user01 = createNormalUser("user01")
        val employee01 = createNormalUser(site01.id.get + "-employee")
        val employee02 = createNormalUser((site01.id.get + 1) + "-employee")
        val siteOwner = inject[SiteUserRepo].createNew(user01.id.get, site01.id.get)
        logoff(browser)
        login(browser, "user01")

        browser.goTo(
          controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Titles.top).trim

        browser.goTo(
          controllers.routes.UserMaintenance.modifyUserStart(employee01.id.get).url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Titles.top).trim
      }
    }

    "Edit employee will show only employees of the site of currently logined store owner." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ enableEmployeeMaintenance)
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val site01 = inject[SiteRepo].createNew(Ja, "店舗1")
        val superUser = loginWithTestUser(browser)
        val user01 = createNormalUser("user01")
        val employee01 = createNormalUser(site01.id.get + "-employee")
        val employee02 = createNormalUser((site01.id.get + 1) + "-employee")
        val siteOwner = inject[SiteUserRepo].createNew(user01.id.get, site01.id.get)
        logoff(browser)
        login(browser, "user01")

        browser.goTo(
          controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".userTable .userTableBody").size === 1
        browser.find(".userTable .userTableBody .id a").text === employee01.id.get.toString
        browser.find(".userTable .userTableBody .name").text === employee01.userName
        browser.find(".userTable .userTableBody .id a").click()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("modifyUserTitle"))
        browser.find("#userId").attribute("value") === employee01.id.get.toString
        browser.find("#userName").attribute("value") === employee01.userName
        browser.find("#firstName").attribute("value") === employee01.firstName
        browser.find("#lastName").attribute("value") === employee01.lastName
        browser.find("#companyName").attribute("value") === employee01.companyName.get
        browser.find("#email").attribute("value") === employee01.email
        browser.find("#sendNoticeMail_field input[type='checkbox']").size === 0

        browser.find("#userName").fill().`with`("")
        browser.find("#firstName").fill().`with`("")
        browser.find("#lastName").fill().`with`("")
        browser.find("#companyName").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#modifyUser").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#userName_field .error").text === 
          Messages("error.minLength", inject[FormConstraints].userNameMinLength)
        browser.find("#firstName_field .error").text === Messages("error.required")
        browser.find("#lastName_field .error").text === Messages("error.required")
        browser.find("#companyName_field .error").text === Messages("error.required")
        browser.find("#email_field .error").index(0).text === Messages("error.email")
        browser.find("#email_field .error").index(1).text === Messages("error.required")
        browser.find("#password_main_field .error").text ===
          Messages("error.minLength", inject[FormConstraints].passwordMinLength())

        browser.find("#userName").fill().`with`(employee01.userName + "new")
        browser.find("#firstName").fill().`with`("firstName2")
        browser.find("#lastName").fill().`with`("lastName2")
        browser.find("#companyName").fill().`with`("companyName2")
        browser.find("#email").fill().`with`("xxx@xxx.xxx")
        browser.find("#password_main").fill().`with`("password2")
        browser.find("#modifyUser").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#password_confirm_field .error").text === Messages("confirmPasswordDoesNotMatch")
        browser.find("#password_main").fill().`with`("password2")
        browser.find("#password_confirm").fill().`with`("password2")
        browser.find("#modifyUser").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle() === Messages("commonTitle", Messages("editUserTitle"))
        browser.find(".message").text === Messages("userIsUpdated")

        doWith(inject[StoreUserRepo].apply(employee01.id.get)) { newUser =>
          newUser.userName === employee01.userName + "new"
          newUser.firstName === "firstName2"
          newUser.lastName === "lastName2"
          newUser.companyName === Some("companyName2")
          newUser.email === "xxx@xxx.xxx"
          newUser.passwordHash === PasswordHash.generate("password2", newUser.salt, storeUserRepo.PasswordHashStretchCount())
        }

        browser.find("button[data-user-id='" + employee01.id.get + "']").click()
        browser.waitUntil(
          failFalse(browser.find(".ui-dialog-buttonset").first().displayed())
        )
        browser.find(".ui-dialog-buttonset .ui-button").index(1).click() // click No
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("editUserTitle"))

        browser.goTo(
          controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("button[data-user-id='" + employee01.id.get + "']").click()
        browser.waitUntil(
          failFalse(browser.find(".ui-dialog-buttonset").first().displayed())
        )
        browser.find(".ui-dialog-buttonset .ui-button").index(0).click() // click Yes
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle() === Messages("commonTitle", Messages("editUserTitle"))

        browser.find(".userTable .userTableBody").size === 0
      }
    }
  }
}

