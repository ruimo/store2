package controllers

import play.api.mvc._
import models._
import helpers.Cache

trait NewsCommon {
  def cache: Cache

  def normalUserCanCreateNews: () => Boolean = cache.config(_.get[Boolean]("normalUserCanCreateNews"))

  def checkLogin(login: LoginSession)(f: => Result): Result =
    if (normalUserCanCreateNews()) NeedLogin.assumeUser(true)(f) else NeedLogin.assumeSuperUser(login)(f)
}
