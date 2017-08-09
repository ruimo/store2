package views

import play.api.i18n.{Lang, Messages, MessagesProvider}

object Titles {
  def top(implicit mp: MessagesProvider): String = Messages("company.name")
}
