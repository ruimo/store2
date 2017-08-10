package controllers

import play.api.Configuration
import helpers.Formatter
import models.InvalidUserNameException
import java.sql.Connection

import helpers.{PasswordHash, RandomTokenGenerator, TokenGenerator}
import constraints.FormConstraints
import java.nio.file.Path

import scala.util.{Failure, Success, Try}
import java.nio.file.Files

import play.Logger
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models._
import play.api.data.Form
import play.api.i18n.{Lang, Messages, MessagesProvider}
import play.api.Play.current
import helpers.{QueryString, RandomTokenGenerator, TokenGenerator}
import com.ruimo.scoins.LoanPattern.iteratorFromReader
import java.nio.charset.Charset
import javax.inject.{Inject, Singleton}

import com.ruimo.csv.CsvParseException
import com.ruimo.csv.CsvRecord
import com.ruimo.csv.Parser._
import controllers.NeedLogin.Authenticated
import play.api.Play
import helpers.Cache
import play.api.db.Database
import play.api.libs.Files.TemporaryFile
import play.api.mvc._

class UserMaintenanceImpl (
  cc: MessagesControllerComponents,
  cache: Cache,
  fc: FormConstraints,
  admin: Admin,
  config: Configuration,
  implicit val storeUserRepo: StoreUserRepo,
  authenticated: Authenticated,
  implicit val orderNotificationRepo: OrderNotificationRepo,
  implicit val siteUserRepo: SiteUserRepo,
  implicit val db: Database,
  implicit val siteRepo: SiteRepo,
  implicit val loginSessionRepo: LoginSessionRepo,
  implicit val shoppingCartItemRepo: ShoppingCartItemRepo
) extends MessagesAbstractController(cc) {

  val EmployeeCsvRegistration: () => Boolean = cache.config(
    _.getOptional[Boolean]("employee.csv.registration").getOrElse(false)
  )
  val SiteOwnerCanEditEmployee: () => Boolean = cache.config(
    _.getOptional[Boolean]("siteOwnerCanEditEmployee").getOrElse(false)
  )
  val MaxCountOfSupplementalEmail: () => Int = cache.config(
    _.getOptional[Int]("maxCountOfSupplementalEmail").getOrElse(0)
  )
  val SiteOwnerCanUploadUserCsv: () => Boolean = cache.config(
    _.getOptional[Boolean]("siteOwnerCanUploadUserCsv").getOrElse(false)
  )

  def createEmployeeForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "userName" -> text.verifying(fc.normalUserNameConstraint(): _*),
      "password" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      )
    )(CreateEmployee.apply)(CreateEmployee.unapply)
  )

  def modifyUserForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "userId" -> longNumber,
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
      "sendNoticeMail" -> boolean
    )(ModifyUser.fromForm)(ModifyUser.toForm)
  )

  def newSiteOwnerForm(implicit mp: MessagesProvider) = Form(
    mapping(
      "siteId" -> longNumber,
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
      "companyName" -> text.verifying(fc.companyNameConstraint: _*)
    )(CreateSiteOwner.fromForm)(CreateSiteOwner.toForm)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      if (SiteOwnerCanUploadUserCsv() || login.isSuperUser) {
        Ok(views.html.admin.userMaintenance(config))
      }
      else {
        Redirect(routes.Admin.index)
      }
    }
  }

  def startCreateNewSuperUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(
        views.html.admin.createNewSuperUser(
          admin.createUserForm(FirstSetup.fromForm, FirstSetup.toForm),
          MaxCountOfSupplementalEmail()
        )
      )
    }
  }

  def startCreateNewSiteOwner = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      db.withConnection { implicit conn =>
        Ok(
          views.html.admin.createNewSiteOwner(
            newSiteOwnerForm, siteRepo.tableForDropDown, MaxCountOfSupplementalEmail()
          )
        )
      }
    }
  }

  def startCreateNewNormalUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      Ok(
        views.html.admin.createNewNormalUser(
          admin.createNormalUserForm(CreateNormalUser.fromForm, CreateNormalUser.toForm),
          MaxCountOfSupplementalEmail()
        )
      )
    }
  }

  def startCreateNewEmployeeUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSiteOwner(login) {
      if (SiteOwnerCanEditEmployee()) {
        val siteId = login.siteUser.map(_.siteId).get
        db.withConnection { implicit conn =>
          Ok(views.html.admin.createNewEmployeeUser(siteRepo(siteId), createEmployeeForm))
        }
      }
      else {
        Redirect(routes.Application.index)
      }
    }
  }

  def createNewEmployeeUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSiteOwner(login) {
      createEmployeeForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.createNewEmployeeUser." + formWithErrors)
          val siteId = login.siteUser.map(_.siteId).get
          db.withConnection { implicit conn =>
            BadRequest(views.html.admin.createNewEmployeeUser(siteRepo(siteId), formWithErrors))
          }
        },
        newUser => {
          if (SiteOwnerCanEditEmployee()) {
            val siteId = login.siteUser.map(_.siteId).get
            val salt = RandomTokenGenerator().next
            db.withConnection { implicit conn =>
              val createdUser = storeUserRepo.create(
                userName = siteId + "-" + newUser.userName,
                firstName = "",
                middleName = None,
                lastName = "",
                email = "",
                passwordHash = PasswordHash.generate(newUser.passwords._1, salt),
                salt = salt,
                userRole = UserRole.NORMAL,
                companyName = Some(siteRepo(siteId).name)
              )

              Employee.createNew(siteId, createdUser.id.get)
            }

            Redirect(
              routes.UserMaintenance.startCreateNewEmployeeUser()
            ).flashing("message" -> Messages("userIsCreated"))
          }
          else {
            Redirect(routes.Application.index)
          }
        }
      )
    }
  }

  def createNewSuperUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      admin.createUserForm(FirstSetup.fromForm, FirstSetup.toForm).bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.createNewSuperUser." + formWithErrors)
          BadRequest(
            views.html.admin.createNewSuperUser(
              formWithErrors, MaxCountOfSupplementalEmail()
            )
          )
        },
        newUser => db.withConnection { implicit conn =>
          newUser.save
          Redirect(
            routes.UserMaintenance.startCreateNewSuperUser
          ).flashing("message" -> Messages("userIsCreated"))
        }
      )
    }
  }

  def createNewSiteOwner = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      newSiteOwnerForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.createNewSiteOwner." + formWithErrors)
          db.withConnection { implicit conn =>
            BadRequest(
              views.html.admin.createNewSiteOwner(
                formWithErrors, siteRepo.tableForDropDown, MaxCountOfSupplementalEmail()
              )
            )
          }
        },
        newUser => db.withTransaction { implicit conn =>
          implicit val tg = RandomTokenGenerator()
          newUser.save
          Redirect(
            routes.UserMaintenance.startCreateNewSiteOwner
          ).flashing("message" -> Messages("userIsCreated"))
        }
      )
    }
  }

  def createNewNormalUser = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeSuperUser(login) {
      admin.createNormalUserForm(CreateNormalUser.fromForm, CreateNormalUser.toForm).bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.createNewNormalUser." + formWithErrors)
          BadRequest(
            views.html.admin.createNewNormalUser(
              formWithErrors, MaxCountOfSupplementalEmail()
            )
          )
        },
        newUser => db.withConnection { implicit conn =>
          newUser.save
          Redirect(
            routes.UserMaintenance.startCreateNewNormalUser
          ).flashing("message" -> Messages("userIsCreated"))
        }
      )
    }
  }

  def editUser(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        if (login.isSuperUser) {
          Ok(
            views.html.admin.editUser(
              storeUserRepo.listUsers(page, pageSize, OrderBy(orderBySpec))
            )
          )
        }
        else { // Store owner
          if (SiteOwnerCanEditEmployee()) {
            val siteId = login.siteUser.map(_.siteId).get
            Ok(
              views.html.admin.editUser(
                storeUserRepo.listUsers(page, pageSize, OrderBy(orderBySpec), employeeSiteId = Some(siteId))
              )
            )
          }
          else {
            Redirect(routes.Application.index)
          }
        }
      }
    }
  }

  def modifyUserStart(userId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        val user: ListUserEntry = storeUserRepo.withSite(userId)
        if (login.isSuperUser) {
          Ok(
            views.html.admin.modifyUser(
              user,
              modifyUserForm.fill(
                ModifyUser(user, SupplementalUserEmail.load(userId).toSeq)
              ),
              MaxCountOfSupplementalEmail()
            )
          )
        }
        else { // Store owner
          if (canEditEmployee(userId, login.siteUser.map(_.siteId).get)) {
            Ok(
              views.html.admin.modifyUser(
                user, modifyUserForm.fill(
                  ModifyUser(user, SupplementalUserEmail.load(userId).toSeq)
                ),
                MaxCountOfSupplementalEmail()
              )
            )
          }
          else {
            Redirect(routes.Application.index)
          }
        }
      }
    }
  }

  def modifyUser(userId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      modifyUserForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.modifyUser." + formWithErrors)
          db.withConnection { implicit conn =>
            val user: ListUserEntry = storeUserRepo.withSite(userId)
            BadRequest(
              views.html.admin.modifyUser(user, formWithErrors, MaxCountOfSupplementalEmail())
            )
          }
        },
        newUser => db.withTransaction { implicit conn =>
          if (login.isSuperUser || canEditEmployee(newUser.userId, login.siteUser.map(_.siteId).get)) {
            implicit val tg = RandomTokenGenerator()
            newUser.update
            Redirect(
              routes.UserMaintenance.editUser()
            ).flashing("message" -> Messages("userIsUpdated"))
          }
          else {
            Redirect(routes.Application.index)
          }
        }
      )
    }
  }

  def deleteUser(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        if (login.isSuperUser || canEditEmployee(id, login.siteUser.map(_.siteId).get)) {
          storeUserRepo.delete(id)
          Redirect(routes.UserMaintenance.editUser())
        }
        else {
          Redirect(routes.Application.index)
        }
      }
    }
  }

  def canEditEmployee(userId: Long, siteId: Long)(implicit conn: Connection): Boolean =
    storeUserRepo(userId).isEmployeeOf(siteId) && SiteOwnerCanEditEmployee()

  def startAddUsersByCsv = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      if (SiteOwnerCanUploadUserCsv() || login.isSuperUser) {
        db.withConnection { implicit conn =>
          Ok(views.html.admin.addUsersByCsv())
        }
      }
      else {
        Redirect(routes.Admin.index)
      }
    }
  }

  def addUsersByCsv = maintainUsersByCsv(
    csvRecordFilter = (_, _) => true,
    deleteSqlSupplemental = _ => None
  )

  def maintainUsersByCsv(
    csvRecordFilter: (Map[String, Seq[String]], CsvRecord) => Boolean,
    deleteSqlSupplemental: Map[String, Seq[String]] => Option[String]
  ) = Action(parse.multipartFormData) { implicit request: MessagesRequest[MultipartFormData[TemporaryFile]] =>
    db.withConnection { implicit conn => loginSessionRepo.fromRequest(request) } match {
      case None => NeedLogin.onUnauthorized(request)
      case Some(user) =>
        if (user.isBuyer) NeedLogin.onUnauthorized(request)
        else {
          request.body.file("attachment").map { csvFile =>
            val filename = csvFile.filename
            val contentType = csvFile.contentType
            implicit val lang = request.acceptLanguages.head
            Logger.info("Users are uploaded. filename='" + filename + "', contentType='" + contentType + "'")
            if (contentType != Some("text/csv") && contentType != Some("application/vnd.ms-excel")) {
              Redirect(
                routes.UserMaintenance.startAddUsersByCsv()
              ).flashing("errorMessage" -> Messages("csv.needed"))
            }
            else {
              createResultFromUserCsvFile(
                csvFile.ref.path,
                csvRecordFilter(request.body.dataParts, _: CsvRecord),
                deleteSqlSupplemental(request.body.dataParts)
              )
            }
          }.getOrElse {
            Logger.error("Users are uploaded. But no attachment found.")
            Redirect(routes.UserMaintenance.startAddUsersByCsv()).flashing(
              "errorMessage" -> Messages("file.not.found")
            )
          }.withSession(
            request.session + 
            (loginSessionRepo.loginUserKey -> user.withExpireTime(System.currentTimeMillis + loginSessionRepo.sessionTimeout).toSessionString)
          )
        }
    }
  }  

  def createResultFromUserCsvFile(
    path: Path,
    csvRecordFilter: CsvRecord => Boolean,
    deleteSqlSupplemental: Option[String]
  )(implicit mp: MessagesProvider): Result = {
    import com.ruimo.csv.Parser.parseLines
    import Files.newBufferedReader
    
    iteratorFromReader(newBufferedReader(path, Charset.forName("Windows-31j"))) { in: Iterator[Char] =>
      val z: Iterator[Try[CsvRecord]] = asHeaderedCsv(parseLines(in))
      storeUserRepo.maintainByCsv(
        z,
        csvRecordFilter,
        deleteSqlSupplemental,
        EmployeeCsvRegistration()
      )
    } match {
      case Success(updatedColumnCount) =>
        Redirect(
          routes.UserMaintenance.startAddUsersByCsv()
        ).flashing("message" -> Messages("usersAreUpdated", updatedColumnCount._1, updatedColumnCount._2))

      case Failure(e) => e match {
        case cpe: CsvParseException =>
          Logger.error("CSV format error", cpe)
          Redirect(
            routes.UserMaintenance.startAddUsersByCsv()
          ).flashing("errorMessage" -> Messages("csv.error", cpe.lineNo))
        case e: InvalidUserNameException =>
          Logger.error("User name '" + e.userName + "' in CSV is invalid." + e.errors)
          Redirect(
            routes.UserMaintenance.startAddUsersByCsv()
          ).flashing(
            "errorMessage" -> (Formatter.validationErrorsToString(e.errors) + s"'${e.userName}'")
          )
        case e: DuplicatedUserNameException =>
          Logger.error("User name '" + e.userName + "' is duplicated in CSV.")
          Redirect(
            routes.UserMaintenance.startAddUsersByCsv()
          ).flashing(
            "errorMessage" -> Messages("userNameDuplicated", e.userName)
          )

        case t: Throwable =>
          Logger.error("CSV general error", t)
          Redirect(
            routes.UserMaintenance.startAddUsersByCsv()
          ).flashing("errorMessage" -> Messages("general.error"))
      }
    }
  }

  def showRegisteredEmployeeCount = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        Ok(
          views.html.admin.showRegisteredEmployeeCount(
            siteRepo.listAsMap,
            storeUserRepo.registeredEmployeeCount
          )
        )
      }
    }
  }
}
