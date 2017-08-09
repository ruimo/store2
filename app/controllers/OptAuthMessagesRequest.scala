package controllers

import models.LoginSession
import play.api.i18n.MessagesApi
import play.api.mvc.{MessagesRequest, Request}

class OptAuthMessagesRequest[A] (
  val login: Option[LoginSession],
  messagesApi: MessagesApi,
  request: Request[A]
) extends MessagesRequest[A](request, messagesApi)
