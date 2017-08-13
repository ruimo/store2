package controllers

import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import org.specs2.mock._
import play.api.mvc.Session
import models.{UserRole, StoreUser, TestHelper, LoginSession, StoreUserRepo, LoginSessionRepo}
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class NeedLoginSpec extends Specification with InjectorSupport {
  "NeedLogin" should {
    "Can get login session." in {
      implicit val app: PlayApp = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "name1", "first1", None, "last1", "email1", 123L, 234L, UserRole.NORMAL, Some("companyName")
        )
          
        val now = 234L
        val loginSession = inject[LoginSessionRepo]
        val req = FakeRequest().withSession((loginSession.loginUserKey, user1.id.get + ";234"))
        val login = loginSession.fromRequest(req, now).get
        login.storeUser.id.get === user1.id.get
        login.expireTime === 234L
      }
    }

    "Login session expired." in {
      implicit val app: PlayApp = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val user1 = inject[StoreUserRepo].create(
          "name1", "first1", None, "last1", "email1", 123L, 234L, UserRole.NORMAL, Some("companyName")
        )
        val loginSession = inject[LoginSessionRepo]

        val now = 234L
        val req = FakeRequest().withSession((loginSession.loginUserKey, user1.id.get + ";233"))
        inject[LoginSessionRepo].fromRequest(req, now) === None
      }
    }
  }
}
