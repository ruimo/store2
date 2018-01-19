package controllers

import play.api.Configuration
import javax.inject._

import models._
import play.api.data.Form
import play.api.i18n.{Messages, MessagesProvider}
import play.api.mvc._
import play.api.data.Forms._
import constraints.FormConstraints
import helpers.{Cache, PasswordHash, RandomTokenGenerator}
import play.api.db.Database
import play.api.data.validation.Constraints._
import helpers.Sanitize.{forUrl => sanitize}
import play.api.Logger

@Singleton
class Admin @Inject() (
  cc: MessagesControllerComponents,
  implicit val db: Database,
  cache: Cache,
  fc: FormConstraints,
  authenticated: NeedLogin.Authenticated,
  loginAgentTable: LoginAgentTable,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val storeUserRepo: StoreUserRepo,
  conf: Configuration,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {
  val anonymousCanPurchase: () => Boolean = cache.config(
    _.getOptional[Boolean]("anonymousUserPurchase").getOrElse(false)
  )

  val loginForm = Form(
    mapping(
      "companyId" -> optional(text),
      "userName" -> text.verifying(nonEmpty),
      "password" -> text.verifying(nonEmpty),
      "uri" -> text
    )(LoginUser.apply)(LoginUser.unapply)
  )

  def createUserForm[T <: CreateUser](
    apply: (
      String, String, Option[String], String, String, Seq[Option[String]], (String, String), String,
      Option[String], Option[String], Option[String]
    ) => T,
    unapply: T => Option[
      (
        String, String, Option[String], String, String, Seq[Option[String]], (String, String), String,
        Option[String], Option[String], Option[String]
      )
    ]
  )(implicit mp: MessagesProvider) = Form(
    mapping(
      "userName" -> text.verifying(fc.userNameConstraint(): _*),
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "email" -> email.verifying(fc.emailConstraint: _*),
      "supplementalEmails" -> seq(optional(email.verifying(fc.optionalEmailConstraint))),
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "companyName" -> text.verifying(fc.companyNameConstraint: _*),
      "altFirstName" -> optional(text),
      "altMiddleName" -> optional(text),
      "altLastName" -> optional(text)
    )(apply)(unapply)
  )

  def createNormalUserForm[T <: CreateUser](
    apply: (
      String, String, Option[String], String, String, Seq[Option[String]], (String, String), String,
      Option[String], Option[String], Option[String]
    ) => T,
    unapply: T => Option[
      (
        String, String, Option[String], String, String, Seq[Option[String]], (String, String), String,
        Option[String], Option[String], Option[String]
      )
    ]
  )(implicit mp: MessagesProvider) = Form(
    mapping(
      "userName" -> text.verifying(fc.normalUserNameConstraint(): _*),
      "firstName" -> text.verifying(fc.firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(fc.lastNameConstraint: _*),
      "email" -> email.verifying(fc.emailConstraint: _*),
      "supplementalEmails" -> seq(optional(email.verifying(fc.optionalEmailConstraint))),
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "companyName" -> text.verifying(fc.companyNameConstraint: _*),
      "altFirstName" -> optional(text),
      "altMiddleName" -> optional(text),
      "altLastName" -> optional(text)
    )(apply)(unapply)
  )

  def startFirstSetup = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.admin.firstSetup(createUserForm(FirstSetup.fromForm, FirstSetup.toForm)))
  }

  def firstSetup = Action { implicit request: MessagesRequest[AnyContent] =>
    createUserForm(FirstSetup.fromForm, FirstSetup.toForm).bindFromRequest.fold(
      formWithErrors =>
        BadRequest(views.html.admin.firstSetup(formWithErrors)),
      firstSetup => db.withConnection { implicit conn => {
        val createdUser = firstSetup.save
        val resp = Redirect(routes.Admin.index)
        val msg = Messages("welcome")
        if (! msg.isEmpty) resp.flashing("message" -> msg) else resp
      }}
    )
  }

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.index(conf))
    }
  }

  def startLogin(uriOnLoginSuccess: String) = Action { implicit request: MessagesRequest[AnyContent] =>
    db.withConnection { implicit conn =>
      if (loginSessionRepo.fromRequest(request).isDefined)
        Redirect(uriOnLoginSuccess)
      else
        Ok(
          views.html.admin.login(
            loginForm, anonymousCanPurchase(), sanitize(uriOnLoginSuccess), loginAgentTable.candidates
          )
        )
    }
  }

  def onValidationErrorInLogin(form: Form[LoginUser])(implicit request: MessagesRequest[AnyContent]) = {
    Logger.error("Validation error in NeedLogin.login.")
    BadRequest(
      views.html.admin.login(
        form, anonymousCanPurchase(), form("uri").value.get, loginAgentTable.candidates
      )
    )
  }

  def login = Action { implicit request: MessagesRequest[AnyContent] =>
    val form = loginForm.bindFromRequest
    form.fold(
      onValidationErrorInLogin,
      user => tryLogin(user, form)
    )
  }

  def logoff(uriOnLogoffSuccess: String) = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.Application.index).withSession(request.session - loginSessionRepo.loginUserKey)
  }

  def tryLogin(
    user: LoginUser, form: Form[LoginUser]
  )(implicit request: MessagesRequest[AnyContent]): Result = db.withConnection { implicit conn =>
    storeUserRepo.findByUserName(user.compoundUserName) match {
      case None => 
        Logger.error("Cannot find user '" + user.compoundUserName + "'")
        onLoginUserNotFound(form)
      case Some(rec) =>
        if (rec.passwordMatch(user.password)) {
          Logger.info("Password ok '" + user.compoundUserName + "'")
          if (rec.isRegistrationIncomplete) {
            Logger.info("Need user registration '" + user.compoundUserName + "'")
            Redirect(
              routes.UserEntry.registerUserInformation(rec.id.get)
            )
          }
          else {
            Logger.info("Login success '" + user.compoundUserName + "'")
            val resp = Redirect(user.uri).withSession {
              (loginSessionRepo.loginUserKey,
               loginSessionRepo.serialize(rec.id.get, System.currentTimeMillis + loginSessionRepo.sessionTimeout))
            }
            val msg = Messages("welcome")
            if (! msg.isEmpty) resp.flashing("message" -> msg) else resp
            resp
          }
        }
        else {
          Logger.error("Password doesnot match '" + user.compoundUserName + "'")
          BadRequest(
            views.html.admin.login(
              form.withGlobalError(Messages("cannotLogin")),
              anonymousCanPurchase(),
              form("uri").value.get,
              loginAgentTable.candidates
            )
          )
        }
    }
  }

  def onLoginUserNotFound(form: Form[LoginUser])(implicit request: MessagesRequest[AnyContent]) = {
    Logger.error("User '" + form.data("userName") + "' not found.")
    BadRequest(
      views.html.admin.login(
        form.withGlobalError(Messages("cannotLogin")),
        anonymousCanPurchase(),
        form("uri").value.get,
        loginAgentTable.candidates
      )
    )
  }

  val anonymousLoginForm = Form(
    mapping(
      "uri" -> text
    )(AnonymousLoginUser.apply)(AnonymousLoginUser.unapply)
  )

  def anonymousLogin = Action { implicit request: MessagesRequest[AnyContent] =>
    if (! anonymousCanPurchase()) {
      Redirect(routes.Application.index)
    }
    else {
      anonymousLoginForm.bindFromRequest.fold(
        errorForm => {
          Logger.error("Validation error in NeedLogin.anonymousLogin.")
          BadRequest(
            views.html.admin.login(
              loginForm.fill(
                LoginUser(
                  None, "", "",
                  errorForm("uri").value.get
                )
              ),
              anonymousCanPurchase(),
              errorForm("uri").value.get,
              loginAgentTable.candidates
            )
          )
        },
        user => {
          db.withConnection { implicit conn =>
            val salt = RandomTokenGenerator().next
            val userNameSeed = RandomTokenGenerator().next
            val userName = f"anon$userNameSeed%08x"
            val anonUser = storeUserRepo.create(
              userName = userName,
              firstName = Messages("guest"),
              middleName = None,
              lastName = "",
              email = "",
              passwordHash = PasswordHash.generate(userName, salt),
              salt = salt,
              userRole = UserRole.ANONYMOUS,
              companyName = None,
              altFirstName = None,
              altMiddleName = None,
              altLastName = None
            )

            Logger.info("Anonymous login success '" + user + "'")
            Redirect(user.uri).withSession {
              (loginSessionRepo.loginUserKey, loginSessionRepo.serialize(anonUser.id.get, System.currentTimeMillis + loginSessionRepo.sessionTimeout))
            }
          }
        }
      )
    }
  }
}
