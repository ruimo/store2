package views.fieldctors

import views.html.helper.FieldConstructor
import views.html.helper.FieldElements
import play.api.i18n.Lang
import play.api.mvc.{AnyContent, MessagesRequest}

object FieldConstructors {
  implicit def showOnlyRequired(implicit request: MessagesRequest[AnyContent]) = new FieldConstructor {
    def apply(e: FieldElements) = views.html.fieldctors.onlyRequired(e)
  }
}
