package models

import anorm._
import java.sql.Connection
import javax.inject.{Inject, Singleton}

import play.api.db.Database
import play.api.mvc.RequestHeader
import play.api.Configuration

case class LoginSession(storeUser: StoreUser, siteUser: Option[SiteUser], expireTime: Long)(
  implicit storeUserRepo: StoreUserRepo
) {
  lazy val user = User(storeUser, siteUser)
  lazy val userId = storeUser.id.get
  def withExpireTime(newExpireTime: Long) = LoginSession(storeUser, siteUser, newExpireTime)
  def toSessionString = storeUser.id.get + ";" + expireTime
  lazy val role: UserType = user.userType
  lazy val isBuyer = (role == Buyer || role == AnonymousBuyer || role == EntryUserBuyer)
  lazy val isSuperUser = role == SuperUser
  lazy val isAdmin = !isBuyer
  lazy val isAnonymousBuyer = role == AnonymousBuyer
  lazy val isEntryUserBuyer = role == EntryUserBuyer
  lazy val isSiteOwner = isAdmin && (! isSuperUser)
  def quantityInShoppingCart(implicit shoppingCartItemRepo: ShoppingCartItemRepo, db: Database): Long = db.withConnection { implicit conn =>
    shoppingCartItemRepo.quantityForUser(storeUser.id.get)
  }
  def update(addr: CreateAddress)(implicit conn: Connection) {
    storeUserRepo.update(
      storeUser.id.get,
      storeUser.userName, 
      addr.firstName,
      if (addr.middleName.isEmpty) None else Some(addr.middleName),
      addr.lastName,
      addr.email,
      storeUser.passwordHash,
      storeUser.salt,
      storeUser.companyName
    )
  }
}

@Singleton
class LoginSessionRepo @Inject() (
  implicit storeUserRepo: StoreUserRepo,
  loginSessionRepo: LoginSessionRepo,
  conf: Configuration
) {
  val loginUserKey = "loginUser"
  val sessionTimeout = conf.getOptional[Int]("login.timeout.minute").getOrElse(5) * 60 * 1000

  def apply(sessionString: String)(implicit conn: Connection): LoginSession = {
    val args = sessionString.split(';').map(_.toLong)
    val storeSiteUser = storeUserRepo.withSite(args(0))
    LoginSession(storeSiteUser.user, storeSiteUser.siteUser, args(1))
  }

  def serialize(storeUserId: Long, expirationTime: Long): String = storeUserId + ";" + expirationTime

  def fromRequest(
    request: RequestHeader, now: Long = System.currentTimeMillis
  )(implicit conn: Connection): Option[LoginSession] = {
    request.session.get(loginUserKey).flatMap {
      sessionString =>
        val s = loginSessionRepo(sessionString)
        if (s.expireTime < now) None else Some(s)
    }
  }
}
