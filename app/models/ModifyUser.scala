package models

import helpers.{PasswordHash, TokenGenerator}
import java.security.MessageDigest
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import scala.collection.immutable

case class ModifyUser(
  userId: Long, userName: String, firstName: String, middleName: Option[String], lastName: String,
  email: String, supplementalEmails: immutable.Seq[String], password: String, companyName: String, 
  sendNoticeMail: Boolean,
  altFirstName: Option[String], altMiddleName: Option[String], altLastName: Option[String],
)(
  implicit storeUserRepo: StoreUserRepo,
  orderNotificationRepo: OrderNotificationRepo
) extends CreateUserBase {
  def update(implicit tokenGenerator: TokenGenerator, conn: Connection) {
    val salt = tokenGenerator.next
    val hash = PasswordHash.generate(password, salt, storeUserRepo.PasswordHashStretchCount())
    storeUserRepo.update(
      userId, userName, firstName, middleName, lastName, email, hash, salt, Some(companyName),
      altFirstName, altMiddleName, altLastName
    )

    SupplementalUserEmail.save(supplementalEmails.toSet, userId)

    orderNotificationRepo.delete(userId)
    if (sendNoticeMail)
      orderNotificationRepo.createNew(userId)

    UserMetadata.getByStoreUserId(userId) match {
      case None =>
        UserMetadata.createNew(
          userId,
          firstNameKana = altFirstName,
          middleNameKana = altMiddleName,
          lastNameKana = altLastName
        )
      case Some(um) =>
        UserMetadata.update(
          userId,
          firstNameKana = altFirstName,
          middleNameKana = altMiddleName,
          lastNameKana = altLastName
        )
    }
  }
}

object ModifyUser {
  def apply(
    user: ListUserEntry, supplementalUserEmails: Seq[SupplementalUserEmail]
  )(
    implicit storeUserRepo: StoreUserRepo,
    orderNotificationRepo: OrderNotificationRepo,
    conn: Connection
  ): ModifyUser = {
    val optMd: Option[UserMetadata] = UserMetadata.getByStoreUserId(user.user.id.get)

    ModifyUser(
      user.user.id.get,
      user.user.userName,
      user.user.firstName,
      user.user.middleName,
      user.user.lastName,
      user.user.email,
      supplementalUserEmails.map(_.email).sorted.toList,
      "",
      user.user.companyName.getOrElse(""),
      user.sendNoticeMail,
      optMd.flatMap(_.firstNameKana),
      optMd.flatMap(_.middleNameKana),
      optMd.flatMap(_.lastNameKana)
    )
  }

  def fromForm(
    userId: Long, userName: String, firstName: String, middleName: Option[String], lastName: String,
    email: String, supplementalEmails: Seq[Option[String]], passwords: (String, String), companyName: String,
    sendNoticeMail: Boolean,
    altFirstName: Option[String], altMiddleName: Option[String], altLastName: Option[String]
  )(
    implicit storeUserRepo: StoreUserRepo,
    orderNotificationRepo: OrderNotificationRepo
  ): ModifyUser =
    ModifyUser(
      userId, userName, firstName, middleName, lastName, email, 
      supplementalEmails.filter(_.isDefined).map(_.get).toList,
      passwords._1, companyName, sendNoticeMail,
      altFirstName, altMiddleName, altLastName
    )

  def toForm(m: ModifyUser) = Some(
    m.userId, m.userName, m.firstName, m.middleName, m.lastName, m.email, 
    m.supplementalEmails.map {e => Some(e)},
    (m.password, m.password), m.companyName, m.sendNoticeMail,
    m.altFirstName, m.altMiddleName, m.altLastName
  )
}
