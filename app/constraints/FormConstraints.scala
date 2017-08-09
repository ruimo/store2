package constraints

import javax.inject.Inject

import scala.util.matching.Regex
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints._
import play.api.data.validation.{Invalid, Valid, ValidationError}
import helpers.Cache
import javax.inject.Singleton

@Singleton
class FormConstraints @Inject() (
  cache: Cache
) extends FormConstraintsBase(cache)
