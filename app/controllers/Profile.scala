package controllers

import services.UserService
import services.MetadataInfo.metadataInfo
import play.api.data.Form
import play.api.data.Forms._
import models.Info
import play.api.Logger
import javax.inject.Inject


class Profile @Inject()(users: UserService) extends  SecuredController {

  val bioForm = Form(
    mapping(
      "avatarUrl" -> optional(text),
      "biography" -> optional(text),
      "currentprojects" -> optional(text),
      "institution" -> optional(text),
      "orcidID" -> optional(text),
      "pastprojects" -> optional(text),
      "position" -> optional(text),
      "userMetadataDefUrl" -> optional(text)
    )(Info.apply)(Info.unapply)
  )

  def editProfile() = SecuredAction() {
    implicit request =>
      implicit val user = request.user
    var avatarUrl: Option[String] = None
    var biography: Option[String] = None
    var currentprojects: Option[String] = None
    var institution: Option[String] = None
    var orcidID: Option[String] = None
    var pastprojects: Option[String] = None
    var position: Option[String] = None
    var userMetadataDefUrl: Option[String] = None
    user match {
      case Some(x) => {
        print(x.email.toString())
        implicit val email = x.email
        email match {
          case Some(addr) => {
            implicit val modeluser = users.findByEmail(addr.toString())
            modeluser match {
              case Some(muser) => {
                muser.avatarUrl match {
                  case Some(url) => {
                    val questionMarkIdx :Int = url.indexOf("?")
                    if (questionMarkIdx > -1) {
                      avatarUrl = Option(url.substring(0, questionMarkIdx))
                    } else {
                      avatarUrl = Option(url)
                    }
                  }
                  case None => avatarUrl = None
                }
                biography = muser.biography
                currentprojects = muser.currentprojects
                institution = muser.institution
                orcidID = muser.orcidID
                pastprojects = muser.pastprojects
                position = muser.position
                userMetadataDefUrl = muser.userMetadataDefUrl

                val newbioForm = bioForm.fill(Info(
                  avatarUrl,
                  biography,
                  currentprojects,
                  institution,
                  orcidID,
                  pastprojects,
                  position,
                  userMetadataDefUrl
                ))
                Ok(views.html.editProfile(newbioForm))
              }
              case None => {
                Logger.error("no user model exists for email " + addr.toString())
                InternalServerError
              }
            }
          }
        }
      }
      case None => {
        Redirect(routes.RedirectUtility.authenticationRequired())
      }
    } 
  }


  def addFriend(email: String) = SecuredAction() { request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        implicit val myemail = x.email
        myemail match {
          case Some(addr) => {
            implicit val modeluser = users.findByEmail(addr.toString())
            implicit val otherUser = users.findByEmail(email)
            modeluser match {
              case Some(muser) => {
                muser.friends match {
                  case Some(viewList) =>{
                    users.addUserFriend(addr.toString(),  addr.toString())
                    otherUser match {
                      case Some(other) => {
                        Redirect(routes.Profile.viewProfile(Option(other.email.getOrElse(""))))
                      }
                    }
                  }
                  case None => {
                    val newList: List[String] = List(email)
                    users.createNewListInUser(addr.toString(), "friends", newList)
                    otherUser match {
                      case Some(other) => {
                        Redirect(routes.Profile.viewProfile(Option(other.email.getOrElse(""))))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
   

  def viewProfile(email: Option[String]) = SecuredAction() { request =>
    implicit val user = request.user
    var ownProfile: Option[Boolean] = None
    email match {
      case Some(addr) => {
        implicit val modeluser = users.findByEmail(addr.toString())
        modeluser match {
          case Some(muser) => {
            user match{
              case Some(loggedIn) => {
                loggedIn.email match{
                  case Some(loggedEmail) => {
                    if (loggedEmail.toString == addr.toString())
                      ownProfile = Option(true)
                    else
                      ownProfile = None
                  }
                }
              }
              case None => { ownProfile = None }
            }
            Ok(views.html.profile(muser, ownProfile))
          }
          case None => {
            Logger.error("no user model exists for " + addr.toString())
            InternalServerError
          }
        }
      }
      case None => {
        user match {
          case Some(loggedInUser) => {
            Redirect(routes.Profile.viewProfile(loggedInUser.email))
          }
          case None => {
            Redirect(routes.RedirectUtility.authenticationRequired())
          }
        }
      }
    }
  }

  def retrieveUserMetadataDefs(email: String, url: String) = {

    import play.api.libs.ws._
    import scala.concurrent.Future
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    // Reset to empty if the URL is blank.
    if (url.isEmpty) {
      users.updateUserField(email, "userMetadataDef_files_nodes", "")
      users.updateUserField(email, "userMetadataDef_files_relas", "")
      users.updateUserField(email, "userMetadataDef_datasets_nodes", "")
      users.updateUserField(email, "userMetadataDef_datasets_relas", "")
    } else {
      val url_files_nodes    = url + "/files/nodes.txt"
      val url_files_relas    = url + "/files/relationships.txt"
      val url_datasets_nodes = url + "/datasets/nodes.txt"
      val url_datasets_relas = url + "/datasets/relationships.txt"

      WS.url(url_files_nodes).get().map { response =>
        users.updateUserField(email, "userMetadataDef_files_nodes", response.body.trim)
      }
      WS.url(url_files_relas).get().map { response =>
        users.updateUserField(email, "userMetadataDef_files_relas", response.body.trim)
      }
      WS.url(url_datasets_nodes).get().map { response =>
        users.updateUserField(email, "userMetadataDef_datasets_nodes", response.body.trim)
      }
      WS.url(url_datasets_relas).get().map { response =>
        users.updateUserField(email, "userMetadataDef_datasets_relas", response.body.trim)
      }
    }
  }

  def submitChanges = SecuredAction() {  implicit request =>
    implicit val user  = request.user
    bioForm.bindFromRequest.fold(
      errors => BadRequest(views.html.editProfile(errors)),
      form => {
        user match {
          case Some(x) => {
            print(x.email.toString())
            implicit val email = x.email
            email match {
              case Some(addr) => {
                implicit val modeluser = users.findByEmail(addr.toString())
                modeluser match {
                  case Some(muser) => {
                    users.updateUserField(addr.toString(), "avatarUrl", form.avatarUrl)
                    users.updateUserField(addr.toString(), "biography", form.biography)
                    users.updateUserField(addr.toString(), "currentprojects", form.currentprojects)
                    users.updateUserField(addr.toString(), "institution", form.institution)
                    users.updateUserField(addr.toString(), "orcidID", form.orcidID)
                    users.updateUserField(addr.toString(), "pastprojects", form.pastprojects)
                    users.updateUserField(addr.toString(), "position", form.position)
                    val url = form.userMetadataDefUrl.getOrElse("").trim
                    users.updateUserField(addr.toString(), "userMetadataDefUrl", url)
                    retrieveUserMetadataDefs(addr.toString(), url)
                    Redirect(routes.Profile.viewProfile(email))
                  }
                }
              }
            }
          }
          case None => {
            Redirect(routes.RedirectUtility.authenticationRequired())
          }
        }
      }
    )
  }
  
  def userMetadataDef_files_nodes = SecuredAction() {  implicit request =>
    // request.user is an Option, and user.email is also an Option, but in a SecuredAction, the user must be present, and same for the user's email, so just use ".get" and not checking the none-ness below.
    val email  = request.user.get.email.get
    val modeluser = users.findByEmail(email).get
    val per_user = modeluser.userMetadataDef_files_nodes.getOrElse("")
    val global = metadataInfo.getProperty[String]("userMetadataDef_files_nodes", "").trim
    Ok(List(global, per_user).mkString("\n").trim)
  }
  def userMetadataDef_files_relas = SecuredAction() {  implicit request =>
    val email  = request.user.get.email.get
    val modeluser = users.findByEmail(email).get
    val per_user = modeluser.userMetadataDef_files_relas.getOrElse("")
    val global = metadataInfo.getProperty[String]("userMetadataDef_files_relas", "").trim
    Ok(List(global, per_user).mkString("\n").trim)
  }
  def userMetadataDef_datasets_nodes = SecuredAction() {  implicit request =>
    val email  = request.user.get.email.get
    val modeluser = users.findByEmail(email).get
    val per_user = modeluser.userMetadataDef_datasets_nodes.getOrElse("")
    val global = metadataInfo.getProperty[String]("userMetadataDef_datasets_nodes", "").trim
    Ok(List(global, per_user).mkString("\n").trim)
   }
  def userMetadataDef_datasets_relas = SecuredAction() {  implicit request =>
    val email  = request.user.get.email.get
    val modeluser = users.findByEmail(email).get
    val per_user = modeluser.userMetadataDef_datasets_relas.getOrElse("")
    val global = metadataInfo.getProperty[String]("userMetadataDef_datasets_relas", "").trim
    Ok(List(global, per_user).mkString("\n").trim)
  }

}
