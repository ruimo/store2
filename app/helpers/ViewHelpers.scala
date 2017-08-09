package helpers

import java.sql.Connection
import java.util.Locale

import play.api.i18n.Lang
import models.{Employee, LoginSession}
import controllers.I18n

object ViewHelpers extends I18n {
  def toAmount(amount: BigDecimal)(implicit lang: Lang): String = lang match {
    case japanese => String.format("%,.0f円", amount.bigDecimal)
    case japan => String.format("%,.0f円", amount.bigDecimal)
    case _ => String.format("%.2f", amount.bigDecimal)
  }

  def employee(implicit optLogin: Option[LoginSession], conn: Connection): Option[Employee] =
    optLogin.flatMap { login => Employee.getBelonging(login.storeUser.id.get) }
}
