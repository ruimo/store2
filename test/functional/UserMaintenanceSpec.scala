package functional

import anorm._
import models._
import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import helpers.UrlHelper._
import helpers.Helper
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import play.api.test.TestServer
import java.sql.Date.{valueOf => date}
import org.openqa.selenium.By
import java.util.concurrent.TimeUnit

class UserMaintenanceSpec extends Specification with InjectorSupport {
  "User maintenance" should {
    // "Show current user's info." in new WithBrowser(
    //   WebDriverFactory(CHROME), appl(inMemoryDatabase() + ("maxCountOfSupplementalEmail" -> 0))
    // ) {
    //   inject[Database].withConnection { implicit conn =>
    //     val currencyInfo = inject[CurrencyRegistry]
    //     val localeInfo = inject[LocaleInfoRepo]
    //     import localeInfo.{En, Ja}
    //     implicit val lang = Lang("ja")
    //     implicit val storeUserRepo = inject[StoreUserRepo]
    //     val Messages = inject[MessagesApi]
    //     implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
    //     val user = loginWithTestUser(browser)

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString
    //     )

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("modifyUserTitle"))
    //     browser.find("#userId").attribute("value") === user.id.get.toString
    //     browser.find("#userName").attribute("value") === user.userName
    //     browser.find("#firstName").attribute("value") === user.firstName
    //     browser.find("#lastName").attribute("value") === user.lastName
    //     browser.find("#companyName").attribute("value") === user.companyName.getOrElse("")
    //     browser.find("#email").attribute("value") === user.email
    //     browser.find("#password_main").attribute("value") === ""
    //     browser.find("#password_confirm").attribute("value") === ""
    //     browser.webDriver.findElement(By.id("sendNoticeMail")).isSelected === false
    //     browser.find("#sendNoticeMail").click()

    //     browser.find("#userName").fill().`with`("userName2")
    //     browser.find("#firstName").fill().`with`("firstName2")
    //     browser.find("#lastName").fill().`with`("lastName2")
    //     browser.find("#companyName").fill().`with`("companyName2")
    //     browser.find("#email").fill().`with`("email2@abc.com")
    //     browser.find("#password_main").fill().`with`("12345678")
    //     browser.find("#password_confirm").fill().`with`("12345678")
    //     browser.find("#modifyUser").click()

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("editUserTitle"))
    //     val user2 = storeUserRepo(user.id.get)
    //     user2.userName === "userName2"
    //     user2.firstName === "firstName2"
    //     user2.lastName === "lastName2"
    //     user2.companyName === Some("companyName2")
    //     user2.email === "email2@abc.com"
    //     inject[OrderNotificationRepo].getByUserId(user.id.get).isDefined === true

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString
    //     )

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("modifyUserTitle"))
    //     browser.find("#supplementalEmails_0_field").size === 0
    //     browser.find("#userId").attribute("value") === user2.id.get.toString
    //     browser.find("#userName").attribute("value") === user2.userName
    //     browser.find("#firstName").attribute("value") === user2.firstName
    //     browser.find("#lastName").attribute("value") === user2.lastName
    //     browser.find("#companyName").attribute("value") === user2.companyName.getOrElse("")
    //     browser.find("#email").attribute("value") === user2.email
    //     browser.find("#password_main").attribute("value") === ""
    //     browser.find("#password_confirm").attribute("value") === ""
    //     browser.webDriver.findElement(By.id("sendNoticeMail")).isSelected === true

    //     browser.find("#sendNoticeMail").click()
    //     browser.find("#password_main").fill().`with`("12345678")
    //     browser.find("#password_confirm").fill().`with`("12345678")
    //     browser.find("#modifyUser").click()

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("editUserTitle"))
    //     inject[OrderNotificationRepo].getByUserId(user.id.get).isDefined === false
    //   }
    // }

    // "Super user see all registed employee count." in new WithBrowser(
    //   WebDriverFactory(CHROME), appl(inMemoryDatabase())
    // ) {
    //   inject[Database].withConnection { implicit conn =>
    //     val currencyInfo = inject[CurrencyRegistry]
    //     val localeInfo = inject[LocaleInfoRepo]
    //     import localeInfo.{En, Ja}
    //     implicit val lang = Lang("ja")
    //     implicit val storeUserRepo = inject[StoreUserRepo]
    //     val Messages = inject[MessagesApi]
    //     implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
    //     val user = loginWithTestUser(browser)

    //     val site1 = inject[SiteRepo].createNew(Ja, "Store01")
    //     val site2 = inject[SiteRepo].createNew(Ja, "Store02")

    //     // Employee, not registered
    //     val user3 = storeUserRepo.create(
    //       userName = site1.id.get + "-111111", // Employee (n-mmmm)
    //       firstName = "", // unregistered
    //       middleName = None,
    //       lastName = "lastName",
    //       email = "null@ruimo.com",
    //       passwordHash = 0,
    //       salt = 0,
    //       userRole = UserRole.NORMAL, // Normal user
    //       companyName = None
    //     )

    //     // Employee, registered
    //     val user4 = storeUserRepo.create(
    //       userName = site1.id.get + "-222222", // Employee (n-mmmm)
    //       firstName = "firstName", // registered
    //       middleName = None,
    //       lastName = "lastName",
    //       email = "null@ruimo.com",
    //       passwordHash = 0,
    //       salt = 0,
    //       userRole = UserRole.NORMAL, // Normal user
    //       companyName = None
    //     )

    //     // Employee, registered
    //     val user9 = storeUserRepo.create(
    //       userName = site2.id.get + "-77777777", // In employee format (n-mmmm), but site owner is not employee.
    //       firstName = "firstName", // registered
    //       middleName = None,
    //       lastName = "lastName",
    //       email = "null@ruimo.com",
    //       passwordHash = 0,
    //       salt = 0,
    //       userRole = UserRole.NORMAL,
    //       companyName = None
    //     )

    //     // Employee, unregistered
    //     val user10 = storeUserRepo.create(
    //       userName = site2.id.get + "-99999999", // In employee format (n-mmmm), but site owner is not employee.
    //       firstName = "", // unregistered
    //       middleName = None,
    //       lastName = "lastName",
    //       email = "null@ruimo.com",
    //       passwordHash = 0,
    //       salt = 0,
    //       userRole = UserRole.NORMAL,
    //       companyName = None
    //     )

    //     // Employee, unregistered
    //     val user11 = storeUserRepo.create(
    //       userName = site2.id.get + "-12345678", // In employee format (n-mmmm), but site owner is not employee.
    //       firstName = "", // unregistered
    //       middleName = None,
    //       lastName = "lastName",
    //       email = "null@ruimo.com",
    //       passwordHash = 0,
    //       salt = 0,
    //       userRole = UserRole.NORMAL,
    //       companyName = None
    //     )

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.showRegisteredEmployeeCount().url.addParm("lang", lang.code).toString
    //     )

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("showRegisteredEmployeeCount"))

    //     browser.find(".site.body").size === 2

    //     browser.find(".site.body").index(0).text === site1.name
    //     browser.find(".allCount.body").index(0).text === "2"
    //     browser.find(".registeredCount.body").index(0).text === "1"

    //     browser.find(".site.body").index(1).text === site2.name
    //     browser.find(".allCount.body").index(1).text === "3"
    //     browser.find(".registeredCount.body").index(1).text === "1"

    //     implicit val siteUserRepo = inject[SiteUserRepo]
    //     val (ownerUser, ownerSiteUser) = Helper.createStoreOwner(name = "StoreOwner01", siteId = site1.id.get)
    //     Helper.logoff(browser)
    //     Helper.login(browser, "StoreOwner01", "password")

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.showRegisteredEmployeeCount().url.addParm("lang", lang.code).toString
    //     )

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("showRegisteredEmployeeCount"))
    //     browser.find(".site.body").texts.size === 1
    //     browser.find(".site.body").index(0).text === site1.name
    //     browser.find(".allCount.body").index(0).text === "2"
    //     browser.find(".registeredCount.body").index(0).text === "1"

    //     val (ownerUser2, ownerSiteUser2) = Helper.createStoreOwner(name = "StoreOwner02", siteId = site2.id.get)
    //     Helper.logoff(browser)
    //     Helper.login(browser, "StoreOwner02", "password")

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.showRegisteredEmployeeCount().url.addParm("lang", lang.code).toString
    //     )

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("showRegisteredEmployeeCount"))
    //     browser.find(".site.body").size === 1
    //     browser.find(".site.body").index(0).text === site2.name
    //     browser.find(".allCount.body").index(0).text === "3"
    //     browser.find(".registeredCount.body").index(0).text === "1"
    //   }
    // }

    // "Can edit supplemental emails." in new WithBrowser(
    //   WebDriverFactory(CHROME), appl(inMemoryDatabase() + ("maxCountOfSupplementalEmail" -> 3))
    // ) {
    //   inject[Database].withConnection { implicit conn =>
    //     val currencyInfo = inject[CurrencyRegistry]
    //     val localeInfo = inject[LocaleInfoRepo]
    //     import localeInfo.{En, Ja}
    //     implicit val lang = Lang("ja")
    //     implicit val storeUserRepo = inject[StoreUserRepo]
    //     val Messages = inject[MessagesApi]
    //     implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
    //     val user = loginWithTestUser(browser)

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString
    //     )
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.find("#supplementalEmails_0_field").size === 1
    //     browser.find("#supplementalEmails_1_field").size === 1
    //     browser.find("#supplementalEmails_2_field").size === 1
    //     browser.find("#supplementalEmails_3_field").size === 0
    //     browser.find("#password_main").fill().`with`("12345678")
    //     browser.find("#password_confirm").fill().`with`("12345678")
    //     browser.find("#supplementalEmails_0").fill().`with`("null@ruimo.com")
    //     browser.find("#supplementalEmails_1").fill().`with`("aaa")
        
    //     browser.find("#modifyUser").click()
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.$(".globalErrorMessage").text === Messages("inputError")
    //     browser.find("#supplementalEmails_1_field dd.error").text === Messages("error.email")
    //     browser.find("#password_main").fill().`with`("12345678")
    //     browser.find("#password_confirm").fill().`with`("12345678")
    //     browser.find("#supplementalEmails_0").fill().`with`("null@ruimo.com")
    //     browser.find("#supplementalEmails_1").fill().`with`("foo@ruimo.com")
    //     browser.find("#modifyUser").click()
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString
    //     )
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("modifyUserTitle"))
    //     browser.find("#supplementalEmails_0").attribute("value") === "foo@ruimo.com"
    //     browser.find("#supplementalEmails_1").attribute("value") === "null@ruimo.com"
    //     browser.find("#supplementalEmails_2").attribute("value") === ""

    //     browser.find("#supplementalEmails_1").fill().`with`("")
    //     browser.find("#password_main").fill().`with`("12345678")
    //     browser.find("#password_confirm").fill().`with`("12345678")
    //     browser.find("#modifyUser").click()
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString
    //     )
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("modifyUserTitle"))
    //     browser.find("#supplementalEmails_0").attribute("value") === "foo@ruimo.com"
    //     browser.find("#supplementalEmails_1").attribute("value") === ""

    //     browser.find("#supplementalEmails_0").fill().`with`("")
    //     browser.find("#password_main").fill().`with`("12345678")
    //     browser.find("#password_confirm").fill().`with`("12345678")
    //     browser.find("#modifyUser").click()
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.goTo(
    //       controllers.routes.UserMaintenance.modifyUserStart(user.id.get).url.addParm("lang", lang.code).toString
    //     )
    //     browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

    //     browser.webDriver.getTitle() === Messages("commonTitle", Messages("modifyUserTitle"))
    //     browser.find("#supplementalEmails_0").attribute("value") === ""
    //   }
    // }

    // "Can change password." in new WithBrowser(
    //   WebDriverFactory(CHROME), appl(inMemoryDatabase())
    // ) {
    //   inject[Database].withConnection { implicit conn =>
    //     val currencyInfo = inject[CurrencyRegistry]
    //     val localeInfo = inject[LocaleInfoRepo]
    //     import localeInfo.{En, Ja}
    //     implicit val lang = Lang("ja")
    //     implicit val storeUserRepo = inject[StoreUserRepo]
    //     val Messages = inject[MessagesApi]
    //     implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
    //     val user = loginWithTestUser(browser)
    //     // Stretch count should be updated. So change it in advance to check it is really updated.
    //     SQL(
    //       """
    //       update store_user set
    //       stretch_count = {sc},
    //       password_hash = {ph},
    //       salt = {s}
    //       where user_name='administrator'
    //       """
    //     ).on(
    //       'sc -> 1000,
    //       'ph -> 6291409084797342197L,
    //       's -> 5581624270338170315L
    //     ).executeUpdate()

    //     browser.goTo(controllers.routes.UserEntry.changePasswordStart().url)
    //     browser.waitUntil(30, TimeUnit.SECONDS) {
    //       browser.find("#currentPassword")
    //     }.fill().`with`("1qaz2wsx")
    //     browser.find("#newPassword_main").fill().`with`("2wsx3edc")
    //     browser.find("#newPassword_confirm").fill().`with`("2wsx3edc")
    //     browser.find("#doResetPasswordButton").click()
    //     browser.waitUntil(30, TimeUnit.SECONDS) {
    //       browser.find(".message")
    //     }.text() === Messages("passwordIsUpdated")

    //     Helper.logoff(browser)
    //     Helper.login(browser, "administrator", "2wsx3edc")

    //     browser.waitUntil(30, TimeUnit.SECONDS) {
    //       browser.find(".title")
    //     }.text() === Messages("adminTitle")
    //   }
    // }

    "Can set alternate name." in new WithBrowser(
      WebDriverFactory(CHROME), appl(inMemoryDatabase())
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

        browser.goTo(controllers.routes.UserMaintenance.startCreateNewNormalUser().url.addParm("lang", lang.code).toString)

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("#userName")
        }.fill().`with`("test0001")
        browser.find("#firstName").fill().`with`("firstName01")
        browser.find("#altFirstName").fill().`with`("altFirstName01")
        browser.find("#lastName").fill().`with`("lastName01")
        browser.find("#altLastName").fill().`with`("altLastName01")
        browser.find("#companyName").fill().`with`("company01")
        browser.find("#email").fill().`with`("null@ruimo.com")
        browser.find("#password_main").fill().`with`("1qaz2wsx")
        browser.find("#password_confirm").fill().`with`("1qaz2wsx")
        browser.find("#registerNormalUser").click()
        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".message")
        }.text() === Messages("userIsCreated")

        browser.goTo(controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString)
        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("td.id").index(1).find("a")
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".title").text() === Messages("modifyUserTitle")
        }
        browser.find("#userName").attribute("value") === "test0001"
        browser.find("#firstName").attribute("value") === "firstName01"
        browser.find("#altFirstName").attribute("value") === "altFirstName01"
        browser.find("#lastName").attribute("value") === "lastName01"
        browser.find("#altLastName").attribute("value") === "altLastName01"
        browser.find("#companyName").attribute("value") === "company01"
        browser.find("#email").attribute("value") === "null@ruimo.com"

        browser.goTo(controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString)

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".modifyUserMetadataButton").index(1)
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("#firstNameKana")
        }.attribute("value") === "altFirstName01"
        browser.find("#middleNameKana").attribute("value") === ""
        browser.find("#lastNameKana").attribute("value") === "altLastName01"

        browser.find("#firstNameKana").fill().`with`("altFirstName02")
        browser.find("#middleNameKana").fill().`with`("altMiddleName02")
        browser.find("#lastNameKana").fill().`with`("altLastName02")
        browser.find("#submitUserMetadata").click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("td.id").index(1).find("a")
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".title").text() === Messages("modifyUserTitle")
        }

        browser.find("#userName").attribute("value") === "test0001"
        browser.find("#firstName").attribute("value") === "firstName01"
        browser.find("#altFirstName").attribute("value") === "altFirstName02"
        browser.find("#lastName").attribute("value") === "lastName01"
        browser.find("#altLastName").attribute("value") === "altLastName02"
        browser.find("#companyName").attribute("value") === "company01"
        browser.find("#email").attribute("value") === "null@ruimo.com"

        browser.goTo(controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString)

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".modifyUserMetadataButton").index(1)
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("#firstNameKana")
        }.attribute("value") === "altFirstName02"
        browser.find("#middleNameKana").attribute("value") === "altMiddleName02"
        browser.find("#lastNameKana").attribute("value") === "altLastName02"

        browser.goTo(controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString)
        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("td.id").index(1).find("a")
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".title").text() === Messages("modifyUserTitle")
        }

        browser.find("#altFirstName").fill().`with`("altFirstName03")
        browser.find("#altLastName").fill().`with`("altLastName03")
        browser.find("#password_main").fill().`with`("1qaz2wsx")
        browser.find("#password_confirm").fill().`with`("1qaz2wsx")
        browser.find("#modifyUser").click()

        browser.goTo(controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString)
        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("td.id").index(1).find("a")
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".title").text() === Messages("modifyUserTitle")
        }

        browser.find("#userName").attribute("value") === "test0001"
        browser.find("#firstName").attribute("value") === "firstName01"
        browser.find("#altFirstName").attribute("value") === "altFirstName03"
        browser.find("#lastName").attribute("value") === "lastName01"
        browser.find("#altLastName").attribute("value") === "altLastName03"
        browser.find("#companyName").attribute("value") === "company01"
        browser.find("#email").attribute("value") === "null@ruimo.com"

        browser.goTo(controllers.routes.UserMaintenance.editUser().url.addParm("lang", lang.code).toString)

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find(".modifyUserMetadataButton").index(1)
        }.click()

        browser.waitUntil(30, TimeUnit.SECONDS) {
          browser.find("#firstNameKana")
        }.attribute("value") === "altFirstName03"
        browser.find("#middleNameKana").attribute("value") === "altMiddleName02"
        browser.find("#lastNameKana").attribute("value") === "altLastName03"
      }
    }
  }
}
