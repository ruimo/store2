package models

import helpers.{PasswordHash, TokenGenerator}
import java.security.MessageDigest
import java.sql.Connection
import javax.inject.{Inject, Singleton}

case class CreateSiteOwner(
  siteId: Long, userName: String, firstName: String, middleName: Option[String], lastName: String,
  email: String, supplementalEmails: Seq[String], password: String, companyName: String,
  altFirstName: Option[String], altMiddleName: Option[String], altLastName: Option[String]
)(
  implicit siteUserRepo: SiteUserRepo,
  storeUserRepo: StoreUserRepo
) extends CreateUserBase {
  def save(implicit tokenGenerator: TokenGenerator, conn: Connection): (StoreUser, SiteUser) = {
    val salt = tokenGenerator.next
    val hash = PasswordHash.generate(password, salt)
    val storeUser = storeUserRepo.create(
      userName, firstName, middleName, lastName, email, hash, salt, UserRole.NORMAL, Some(companyName),
      altFirstName, altMiddleName, altLastName
    )
    val siteUser = siteUserRepo.createNew(storeUser.id.get, siteId)

    SupplementalUserEmail.save(supplementalEmails.toSet, storeUser.id.get)
    (storeUser, siteUser)
  }
}

object CreateSiteOwner {
  def fromForm(
    siteId: Long, userName: String, firstName: String, middleName: Option[String], lastName: String,
    email: String, supplementalEmails: Seq[Option[String]], passwords: (String, String), companyName: String,
    altFirstName: Option[String], altMiddleName: Option[String], altLastName: Option[String]
  )(
    implicit siteUserRepo: SiteUserRepo,
    storeUserRepo: StoreUserRepo
  ): CreateSiteOwner =
    CreateSiteOwner(
      siteId, userName, firstName, middleName, lastName, email,
      supplementalEmails.filter(_.isDefined).map(_.get).toList,
      passwords._1, companyName,
      altFirstName, altMiddleName, altLastName
    )

  def toForm(m: CreateSiteOwner) = Some(
    m.siteId, m.userName, m.firstName, m.middleName, m.lastName, m.email,
    m.supplementalEmails.map {e => Some(e)},
    (m.password, m.password), m.companyName,
    m.altFirstName, m.altMiddleName, m.altLastName
  )
}
