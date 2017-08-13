package models

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import com.ruimo.scoins.Scoping._

import scala.collection.immutable
import scala.collection.mutable
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.collection.JavaConversions._

class AcceptableTenderSpec extends Specification {
  "AcceptableTender" should {
    "All settings are empty." in {
      val app: Application = GuiceApplicationBuilder().configure(
        inMemoryDatabase()
      ).configure(
        "acceptableTenders.BUYER" -> List(),
        "acceptableTenders.SUPER_USER" -> List(),
        "acceptableTenders.SITE_OWNER" -> List(),
        "acceptableTenders.ANONYMOUS_BUYER" -> List()
      ).build()
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.BUYER).size === 0
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.SUPER_USER).size === 0
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.SITE_OWNER).size === 0
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.ANONYMOUS_BUYER).size === 0

    }

    "Some settings are set." in {
      val app: Application = GuiceApplicationBuilder().configure(
        inMemoryDatabase()
      ).configure(
        "acceptableTenders.BUYER" -> List("ACCOUNTING_BILL", "PAYPAL"),
        "acceptableTenders.SUPER_USER" -> List("ACCOUNTING_BILL"),
        "acceptableTenders.SITE_OWNER" -> List(),
        "acceptableTenders.ANONYMOUS_BUYER" -> List("PAYPAL")
      ).build()

      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.SUPER_USER) === immutable.HashSet(
        TenderType.ACCOUNTING_BILL
      )
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.BUYER) === immutable.HashSet(
        TenderType.PAYPAL, TenderType.ACCOUNTING_BILL
      )
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.SITE_OWNER) === immutable.HashSet()
      app.injector.instanceOf[AcceptableTender].AcceptableTender()(UserTypeCode.ANONYMOUS_BUYER) === immutable.HashSet(
        TenderType.PAYPAL
      )
    }
  }
}

