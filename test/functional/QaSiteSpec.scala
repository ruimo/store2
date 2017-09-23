package functional

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import helpers.Helper._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import helpers.Helper.disableMailer
import helpers.UrlHelper.fromString
import models._

class QaSiteSpec extends Specification with InjectorSupport {
  val conf = inMemoryDatabase() ++ disableMailer

  "QA site" should {
    "Show form with some fields are filled" in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "商店111")

        browser.goTo(
          controllers.routes.Qa.qaSiteStart(site.id.get, "/back").url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".qaSiteNameBody").text === site.name
        browser.find("#companyName").attribute("value") === user.companyName.get
        browser.find("#name").attribute("value") === user.fullName
        browser.find("#tel").attribute("value") === ""
        browser.find("#email").attribute("value") === user.email
        browser.find("#inquiryBody").text === ""
        browser.find("a.backLink").attribute("href").endsWith("/back") === true
      }
    }

    "Show form with tel field are filled" in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "商店111")
        val addr = Address.createNew(
          countryCode = CountryCode.JPN,
          firstName = "firstName1",
          lastName = "lastName1",
          zip1 = "zip1",
          zip2 = "zip2",
          prefecture = JapanPrefecture.東京都,
          address1 = "address1-1",
          address2 = "address1-2",
          tel1 = "12345678",
          comment = "comment1"
        )
        UserAddress.createNew(user.id.get, addr.id.get)

        browser.goTo(
          controllers.routes.Qa.qaSiteStart(site.id.get, "/back").url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#companyName").attribute("value") === user.companyName.get
        browser.find("#name").attribute("value") === user.fullName
        browser.find("#tel").attribute("value") === "12345678"
        browser.find("#email").attribute("value") === user.email
        browser.find("#inquiryBody").text === ""
      }
    }

    "Show validation error" in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
    ) {
      inject[Database].withConnection { implicit conn =>
        implicit val currencyInfo = inject[CurrencyRegistry]
        implicit val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "商店111")

        browser.goTo(
          controllers.routes.Qa.qaSiteStart(site.id.get, "/back").url.addParm("lang", lang.code).toString
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#companyName").fill().`with`("")
        browser.find("#name").fill().`with`("")
        browser.find("#tel").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#inquiryBody").fill().`with`("")
        browser.find("#submitQaSite").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#companyName_field.error span.help-inline").text === Messages("error.required")
        browser.find("#name_field.error span.help-inline").text === Messages("error.required")
        browser.find("#tel_field.error span.help-inline").text === Messages("error.required") + ", " + Messages("error.number")
        browser.find("#email_field.error span.help-inline").text === Messages("error.required")
        browser.find("#inquiryBody_field.error span.help-inline").text === Messages("error.required")
        
        browser.find("#tel").fill().`with`("ABC")
        browser.find("#submitQaSite").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#tel_field.error span.help-inline").text === Messages("error.number")

        browser.find("#companyName").fill().`with`("companyName002")
        browser.find("#name").fill().`with`("name002")
        browser.find("#tel").fill().`with`("12345678")
        browser.find("#email").fill().`with`("name@xxx.xxx")
        browser.find("#inquiryBody").fill().`with`("inquiry body")
        browser.find("#submitQaSite").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaConfirmTitle"))
        browser.find(".qaSiteNameBody").text === site.name
        browser.find(".companyName .body .value").text === "companyName002"
        browser.find("input[name='companyName']").attribute("value") === "companyName002"
        browser.find(".name .body .value").text === "name002"
        browser.find("input[name='name']").attribute("value") === "name002"
        browser.find(".tel .body .value").text === "12345678"
        browser.find("input[name='tel']").attribute("value") === "12345678"
        browser.find(".email .body .value").text === "name@xxx.xxx"
        browser.find("input[name='email']").attribute("value") === "name@xxx.xxx"
        browser.find(".inquiryBody .body .value").text === "inquiry body"
        browser.find("input[name='inquiryBody']").attribute("value") === "inquiry body"
        browser.find("button[value='amend']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaTitle"))
        browser.find(".qaSiteNameBody").text === site.name
        browser.find("#companyName").attribute("value") === "companyName002"
        browser.find("#name").attribute("value") === "name002"
        browser.find("#tel").attribute("value") === "12345678"
        browser.find("#email").attribute("value") === "name@xxx.xxx"
        browser.find("#inquiryBody").text === "inquiry body"
        browser.find("a.backLink").attribute("href").endsWith("/back") === true

        browser.find("#companyName").fill().`with`("")
        browser.find("#name").fill().`with`("")
        browser.find("#tel").fill().`with`("")
        browser.find("#email").fill().`with`("")
        browser.find("#inquiryBody").fill().`with`("")
        browser.find("#submitQaSite").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".globalErrorMessage").text === Messages("inputError")
        browser.find("#companyName_field.error span.help-inline").text === Messages("error.required")
        browser.find("#name_field.error span.help-inline").text === Messages("error.required")
        browser.find("#tel_field.error span.help-inline").text === Messages("error.required") + ", " + Messages("error.number")
        browser.find("#email_field.error span.help-inline").text === Messages("error.required")
        browser.find("#inquiryBody_field.error span.help-inline").text === Messages("error.required")

        browser.find("#companyName").fill().`with`("companyName003")
        browser.find("#name").fill().`with`("name003")
        browser.find("#tel").fill().`with`("11111111")
        browser.find("#email").fill().`with`("name003@xxx.xxx")
        browser.find("#inquiryBody").fill().`with`("inquiry body003")
        browser.find("#submitQaSite").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaConfirmTitle"))
        browser.find(".qaSiteNameBody").text === site.name
        browser.find(".companyName .body .value").text === "companyName003"
        browser.find("input[name='companyName']").attribute("value") === "companyName003"
        browser.find(".name .body .value").text === "name003"
        browser.find("input[name='name']").attribute("value") === "name003"
        browser.find(".tel .body .value").text === "11111111"
        browser.find("input[name='tel']").attribute("value") === "11111111"
        browser.find(".email .body .value").text === "name003@xxx.xxx"
        browser.find("input[name='email']").attribute("value") === "name003@xxx.xxx"
        browser.find(".inquiryBody .body .value").text === "inquiry body003"
        browser.find("input[name='inquiryBody']").attribute("value") === "inquiry body003"
        browser.find("button[value='submit']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("qaCompletedTitle"))

        browser.find(".qaSiteNameBody").text === site.name
        browser.find(".companyName .body .value").text === "companyName003"
        browser.find(".name .body .value").text === "name003"
        browser.find(".tel .body .value").text === "11111111"
        browser.find(".email .body .value").text === "name003@xxx.xxx"
        browser.find(".inquiryBody .body .value").text === "inquiry body003"
        browser.find("a.backLink").attribute("href").endsWith("/back") === true
      }
    }
  }
}
