package models

trait CreateUserBase {
  val userName: String
  val firstName: String
  val middleName: Option[String]
  val lastName: String
  val email: String
  val supplementalEmails: Seq[String]
  val password: String
  val companyName: String
  val altFirstName: Option[String]
  val altMiddleName: Option[String]
  val altLastName: Option[String]
}
