package models

import org.specs2.mutable._

import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.Database
import java.time.Instant

class TransporterSpec extends Specification with InjectorSupport {
  "Transporter" should {
    "Can create new transporter." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]


      inject[Database].withConnection { implicit conn =>
        val trans0 = inject[TransporterRepo].createNew
        val trans1 = inject[TransporterRepo].createNew

        val list = inject[TransporterRepo].list
        list.size === 2
        list(0) === trans0
        list(1) === trans1
      }
    }

    "Can create new transporter with name." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      val currencyInfo = inject[CurrencyRegistry]

      inject[Database].withConnection { implicit conn =>
        val trans0 = inject[TransporterRepo].createNew
        val transName00 = inject[TransporterNameRepo].createNew(
          trans0.id.get, localeInfo.Ja, "トマト運輸"
        )
        val transName01 = inject[TransporterNameRepo].createNew(
          trans0.id.get, localeInfo.En, "Tomato"
        )
        val trans1 = inject[TransporterRepo].createNew
        val transName10 = inject[TransporterNameRepo].createNew(
          trans1.id.get, localeInfo.En, "Hedex"
        )

        val list = inject[TransporterRepo].tableForDropDown(localeInfo.Ja)
        list.size === 1
        list(0) === (trans0.id.get.toString, "トマト運輸")

        val listEn = inject[TransporterRepo].tableForDropDown(localeInfo.En)
        listEn.size === 2
        listEn(0) === (trans1.id.get.toString, "Hedex")
        listEn(1) === (trans0.id.get.toString, "Tomato")
      }
    }
  }
}
