package models

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import play.api.db.Database
import play.api.Play.current
import anorm._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

class ConstraintHelperSpec extends Specification {
  "ConstraintHelper" should {
    "be able to retrieve column size (8 for LOCALE.LANG) " in {
      val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val x = app.injector.instanceOf[ConstraintHelper].getColumnSize(None,"LOCALE","LANG")
      x === 8
    }

    "be able to follow column size change at runtime by purging cached stale values" in {
      val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      app.injector.instanceOf[Database].withConnection { implicit conn =>
        SQL("create table TEST_TABLE (id int primary key, HOGE varchar(8));").executeUpdate()
      }
      val constraintHelper = app.injector.instanceOf[ConstraintHelper]
      constraintHelper.getColumnSize(None,"TEST_TABLE","HOGE") === 8

      app.injector.instanceOf[Database].withConnection { implicit conn =>
        SQL("alter table TEST_TABLE alter column HOGE varchar(16);").executeUpdate()
      }
      constraintHelper.refreshColumnSizes()
      constraintHelper.getColumnSize(None,"TEST_TABLE","HOGE") === 16
    }
  }
}
