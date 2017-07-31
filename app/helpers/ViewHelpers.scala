package helpers

import java.sql.Connection
import java.util.Locale

import play.api.i18n.Lang
import models.{Employee, LoginSession}

object ViewHelpers {
  def toAmount(amount: BigDecimal)(implicit lang: Lang): String = lang.toLocale match {
    case Locale.JAPANESE => String.format("%,.0f円", amount.bigDecimal)
    case Locale.JAPAN => String.format("%,.0f円", amount.bigDecimal)
    case _ => String.format("%.2f", amount.bigDecimal)
  }

  def employee(implicit optLogin: Option[LoginSession], conn: Connection): Option[Employee] =
    optLogin.flatMap { login => Employee.getBelonging(login.storeUser.id.get) }
}
