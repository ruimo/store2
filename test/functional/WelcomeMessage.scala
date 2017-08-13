package functional

import play.api.i18n.{Lang, Messages, MessagesProvider}

object WelcomeMessage {
  def welcomeMessage(implicit mp: MessagesProvider): String = Messages("login.welcome").format(Messages("guest"), "", "").replaceAll("  *", " ")
}
