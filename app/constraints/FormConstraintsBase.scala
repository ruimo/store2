package constraints

import java.util.regex.Pattern

import scala.util.matching.Regex
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints._
import play.api.data.validation.{Invalid, Valid, ValidationError}
import helpers.Cache

class FormConstraintsBase(cache: Cache) {
  def passwordMinLength: () => Int = cache.config(_.getOptional[Int]("password.min.length").getOrElse(6))
  val userNameMinLength = 6
  def userNameConstraint: () => Seq[Constraint[String]] =
    () => Seq(minLength(userNameMinLength), maxLength(24))
  def normalUserNameConstraint: () => Seq[Constraint[String]] = cache.config(
    _.getOptional[String]("normalUserNamePattern").map { patStr =>
      Seq(pattern(patStr.r, "normalUserNamePatternRule", "normalUserNamePatternError"))
    }.getOrElse(
      Seq(minLength(userNameMinLength), maxLength(24))
    )
  )

  val passwordConstraint = List(minLength(passwordMinLength()), maxLength(24), passwordCharConstraint)
  val firstNameConstraint = List(nonEmpty, maxLength(64))
  val middleNameConstraint = List(maxLength(64))
  val lastNameConstraint = List(nonEmpty, maxLength(64))
  val firstNameKanaConstraint = List(nonEmpty, maxLength(64))
  val lastNameKanaConstraint = List(nonEmpty, maxLength(64))
  val emailConstraint = List(nonEmpty, maxLength(255))
  val optionalEmailConstraint = maxLength(255)
  val companyNameConstraint = List(nonEmpty, maxLength(32))
  def passwordCharConstraint: Constraint[String] = Constraint[String]("constraint.password.char") { s =>
    if (s.forall(c => (0x21 <= c && c < 0x7e))) Valid else Invalid(ValidationError("error.pasword.char"))
  }

  val zip1Pattern = Pattern.compile("\\d{3}")
  val zip2Pattern = Pattern.compile("\\d{4}")
  val telPattern = Pattern.compile("\\d+{1,32}")
  val telOptionPattern = Pattern.compile("\\d{0,32}")
}
