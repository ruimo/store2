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
import helpers.Helper.disableMailer
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import java.sql.Connection
import com.ruimo.scoins.Scoping._

class PrizeSpec extends Specification with InjectorSupport {
  val conf = inMemoryDatabase() ++ disableMailer

  "Prize" should {
    "Can show information." in new WithBrowser(
      WebDriverFactory(CHROME), appl(conf)
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
        val itemName = "Item01"

        browser.goTo(
          controllers.routes.Prize.entry(itemName) + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("prize"))

        doWith(browser.find(".prizeInfo")) { e =>
          e.find(".itemName .body").text === itemName
          e.find(".companyName .body").text === user.companyName.get
          e.find(".name .body").text === user.firstName + " " + user.lastName
          e.find(".email .body").text === user.email
        }

        // Since no address record exists, address fields will be left blank
        doWith(browser.find("#confirmPrizeForm")) { e =>
          e.find("input[name='zip.zip1']").attribute("value") === ""
          e.find("input[name='zip.zip2']").attribute("value") === ""

          e.find("#address1").attribute("value") === ""
          e.find("#address2").attribute("value") === ""
          e.find("#address3").attribute("value") === ""
          e.find("input[name='address4']").attribute("value") === ""
          e.find("input[name='address5']").attribute("value") === ""

          e.find("#firstName").attribute("value") === user.firstName
          e.find("#lastName").attribute("value") === user.lastName

          e.find("#firstNameKana").attribute("value") === ""
          e.find("#lastNameKana").attribute("value") === ""

          e.find("#tel").attribute("value") === ""
          e.find("input[name='command']").attribute("value") === "confirm"

          browser.find("#firstName").fill().`with`("")
          browser.find("#lastName").fill().`with`("")

          e.find("input.submitButton").click()
        }

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")
        doWith(browser.find(".prizeInfo")) { e =>
          e.find(".itemName .body").text === itemName
          e.find(".companyName .body").text === user.companyName.get
          e.find(".name .body").text === user.firstName + " " + user.lastName
          e.find(".email .body").text === user.email
        }

        doWith(browser.find("#confirmPrizeForm")) { e =>
          e.find(".error span").index(1).text === Messages("zipError")
          e.find("#address1_field .error").text === Messages("error.required")
          e.find("#address2_field .error").text === Messages("error.required")
          e.find("#firstName_field .error").text === Messages("error.required")
          e.find("#lastName_field .error").text === Messages("error.required")
          e.find("#firstNameKana_field .error").text === Messages("error.required")
          e.find("#lastNameKana_field .error").text === Messages("error.required")
          e.find("#tel_field .error").text === Messages("error.number")
        }
        
        browser.find("input[name='zip.zip1']").fill().`with`("AAA")
        browser.find("input[name='zip.zip2']").fill().`with`("0082")
        browser.find("#address1").fill().`with`("ADDRESS01")
        browser.find("#address2").fill().`with`("ADDRESS02")
        browser.find("#firstName").fill().`with`("FIRST_NAME")
        browser.find("#lastName").fill().`with`("LAST_NAME")
        browser.find("#firstNameKana").fill().`with`("FIRST_NAME_KANA")
        browser.find("#lastNameKana").fill().`with`("LAST_NAME_KANA")
        browser.find("#tel").fill().`with`("AAA")
        browser.find("input.submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".globalErrorMessage").text === Messages("inputError")

        doWith(browser.find("#confirmPrizeForm")) { e =>
          e.find(".error span").index(1).text === Messages("zipError")
          e.find("#tel_field .error").text === Messages("error.number")
        }

        browser.find("input[name='zip.zip1']").fill().`with`("146")
        browser.find("#tel").fill().`with`("987654321")
        browser.find("#prefecture option[value='13']").click()
        browser.find("#age option[value='30代']").click()
        browser.find("#sex option[value='1']").click()
        browser.find("input.submitButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find(".prizePersonInfo")) { e =>
          e.find(".itemName .body").text === itemName
          e.find(".companyName .body").text === user.companyName.get
          e.find(".name .body").text === user.firstName + " " + user.lastName
          e.find(".email .body").text === user.email
        }

        doWith(browser.find("#submitPrizeForm")) { e =>
          e.find(".firstName .body").text === "FIRST_NAME"
          e.find("input[name='firstName']").attribute("value") === "FIRST_NAME"
          e.find(".lastName .body").text === "LAST_NAME"
          e.find("input[name='lastName']").attribute("value") === "LAST_NAME"
          e.find(".firstNameKana .body").text === "FIRST_NAME_KANA"
          e.find("input[name='firstNameKana']").attribute("value") === "FIRST_NAME_KANA"
          e.find(".lastNameKana .body").text === "LAST_NAME_KANA"
          e.find("input[name='lastNameKana']").attribute("value") === "LAST_NAME_KANA"
          e.find(".zip .body span").index(0).text === "146"
          e.find(".zip .body span").index(1).text === "0082"
          e.find("input[name='zip.zip1']").attribute("value") === "146"
          e.find("input[name='zip.zip2']").attribute("value") === "0082"
          e.find(".prefecture .body").text === "東京都"
          e.find(".prefecture input[name='prefecture']").attribute("value") === "13"
          e.find(".address1 .body").text === "ADDRESS01"
          e.find(".address2 .body").text === "ADDRESS02"
          e.find(".address3 .body").text === ""
          e.find(".tel .body").text === "987654321"
          e.find(".age .body").text === "30代"
          e.find(".age input[name='age']").attribute("value") === "30代"
          e.find(".sex .body").text === "女性"
          e.find(".sex input[name='sex']").attribute("value") === "1"
          e.find(".prizeComment .body").text === ""
          e.find(".prizeComment input[name='comment']").attribute("value") === ""
        }

        browser.find("button[value='amend']").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("prize"))
        doWith(browser.find(".prizeInfo")) { e =>
          e.find(".itemName .body").text === itemName
          e.find(".companyName .body").text === user.companyName.get
          e.find(".name .body").text === user.firstName + " " + user.lastName
          e.find(".email .body").text === user.email
        }

        doWith(browser.find("#confirmPrizeForm")) { e =>
          e.find("input[name='zip.zip1']").attribute("value") === "146"
          e.find("input[name='zip.zip2']").attribute("value") === "0082"

          e.find("#address1").attribute("value") === "ADDRESS01"
          e.find("#address2").attribute("value") === "ADDRESS02"
          e.find("#address3").attribute("value") === ""
          e.find("input[name='address4']").attribute("value") === ""
          e.find("input[name='address5']").attribute("value") === ""

          e.find("#firstName").attribute("value") === "FIRST_NAME"
          e.find("#lastName").attribute("value") === "LAST_NAME"

          e.find("#firstNameKana").attribute("value") === "FIRST_NAME_KANA"
          e.find("#lastNameKana").attribute("value") === "LAST_NAME_KANA"

          e.find("#tel").attribute("value") === "987654321"
          e.find("input[name='command']").attribute("value") === "confirm"

          browser.find("#address3").fill().`with`("ADDRESS03")
          browser.find("#comment").fill().`with`("COMMENT")

          e.find("input.submitButton").click()
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find(".prizePersonInfo")) { e =>
          e.find(".itemName .body").text === itemName
          e.find(".companyName .body").text === user.companyName.get
          e.find(".name .body").text === user.firstName + " " + user.lastName
          e.find(".email .body").text === user.email
        }

        doWith(browser.find("#submitPrizeForm")) { e =>
          e.find(".firstName .body").text === "FIRST_NAME"
          e.find("input[name='firstName']").attribute("value") === "FIRST_NAME"
          e.find(".lastName .body").text === "LAST_NAME"
          e.find("input[name='lastName']").attribute("value") === "LAST_NAME"
          e.find(".firstNameKana .body").text === "FIRST_NAME_KANA"
          e.find("input[name='firstNameKana']").attribute("value") === "FIRST_NAME_KANA"
          e.find(".lastNameKana .body").text === "LAST_NAME_KANA"
          e.find("input[name='lastNameKana']").attribute("value") === "LAST_NAME_KANA"
          e.find(".zip .body span").index(0).text === "146"
          e.find(".zip .body span").index(1).text === "0082"
          e.find("input[name='zip.zip1']").attribute("value") === "146"
          e.find("input[name='zip.zip2']").attribute("value") === "0082"
          e.find(".prefecture .body").text === "東京都"
          e.find(".prefecture input[name='prefecture']").attribute("value") === "13"
          e.find(".address1 .body").text === "ADDRESS01"
          e.find(".address2 .body").text === "ADDRESS02"
          e.find(".address3 .body").text === "ADDRESS03"
          e.find(".tel .body").text === "987654321"
          e.find(".age .body").text === "30代"
          e.find(".age input[name='age']").attribute("value") === "30代"
          e.find(".sex .body").text === "女性"
          e.find(".sex input[name='sex']").attribute("value") === "1"
          e.find(".prizeComment .body").text === "COMMENT"
          e.find(".prizeComment input[name='comment']").attribute("value") === "COMMENT"
        }
      }
    }
  }
}
