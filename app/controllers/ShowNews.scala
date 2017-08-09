package controllers

import javax.inject.{Inject, Singleton}

import controllers.NeedLogin.Authenticated
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class ShowNews @Inject() (
  cc: MessagesControllerComponents,
  authenticated: Authenticated
) extends MessagesAbstractController(cc) {
  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok("")
    }
  }
}
