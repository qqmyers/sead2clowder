package models

import play.api.Play.current
import java.security.MessageDigest
import java.util.Date

import play.api.Play.configuration
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.language.implicitConversions

/**
  * User definition
  */
case class User(
  id: UUID = UUID.generate(),

  // information about user
  firstName: String,
  lastName: String,
  fullName: String,
  email: Option[String] = None,
  avatarUrl: Option[String] = None,

  // what provider did they use to login/create account
  provider: Provider,

  // should user be active
  active: Boolean = false,

  // is the user an admin
  serverAdmin: Boolean = false,

  // has the user escalated privileges, this is never saved to the database
  @transient superAdminMode: Boolean = false,

  // profile
  profile: Option[Profile] = None,

  // following
  followedEntities: List[TypedID] = List.empty,
  followers: List[UUID] = List.empty,
  friends: Option[List[String]] = None,

  // spaces and roles
  spaceandrole: List[UserSpaceAndRole] = List.empty,

  //staging area
  repositoryPreferences: Map[String,Any] = Map.empty,

  // terms of service
  termsOfServices: Option[UserTermsOfServices] = None,

  // social information
  socialInformation: SocialInformation = SocialInformation()) {

  /**
    * Get the avatar URL for this user's profile
    * If user has no avatar URL, this will return a unique URL based on
    * the hash of this user's email address. Gravatar provide an image
    * as specified in application.conf
    *
    * @return Full gravatar URL for the user's profile picture
    */
  def getAvatarUrl(size: Integer = 256): String = {
    val default_gravatar = configuration.getString("default_gravatar").getOrElse("")

    if (profile.isDefined && profile.get.avatarUrl.isDefined) {
      profile.get.avatarUrl.get
    } else if (avatarUrl.isDefined) {
      avatarUrl.get
    } else {
      s"http://www.gravatar.com/avatar/${getEmailHash}?s=${size}&d=${default_gravatar}"
    }
  }

  /**
    * @return lower case md5 hash of the user's email
    */
  def getEmailHash: String = {
    MessageDigest.getInstance("MD5")
      .digest(email.headOption.getOrElse("").getBytes("UTF-8"))
      .map("%02X".format(_))
      .mkString
      .toLowerCase
  }

  def getFollowedObjectList(objectType : String) : List[TypedID] = {
    followedEntities.filter { x => x.objectType == objectType }
  }

  /**
    * return MiniUser constructed from the user model
    */
  def getMiniUser: MiniUser = {
    MiniUser(id = id, fullName = fullName, avatarURL = getAvatarUrl(), email = email.headOption)
  }

  override def toString: String = format(false)

  def format(paren: Boolean): String = {
    val e = email.headOption.fold(" ")(x => s""" <${x}> """)
    val x = s"""${fullName}${e}[${provider}]"""
    if (paren) {
      x.replaceAll("<", "(").replaceAll(">", ")")
    } else {
      x
    }
  }
}

object User {
  def anonymous = new User(UUID("000000000000000000000000"),
    firstName = "Anonymous",
    lastName = "User",
    fullName = "Anonymous User",
    email = None,
    active = true,
    provider = Provider("", ""),
    termsOfServices = Some(UserTermsOfServices(accepted=true, acceptedDate=new Date(), "")))

  implicit def userToMiniUser(x: User): MiniUser = x.getMiniUser
}

case class Provider(
  providerId: String,
  userId: String) {

  override def toString: String = {
    providerId match {
      case "google" => "Google+"
      case p => p.capitalize
    }
  }

  def link(): Option[String] = {
    providerId match {
      case "google" => Some(s"https://plus.google.com/${userId}")
      case "twitter" => Some(s"https://twitter.com/intent/user?user_id=${userId}}")
      case "facebook" => Some(s"https://www.facebook.com/app_scoped_user_id/${userId}")
      case "orcid" => Some("https://orcid.org/${id}")
      case _ => None
    }
  }

  def icon(): Option[String] = {
    providerId match {
      case "userpass" => None
      case _ => Some(controllers.routes.Assets.at("securesocial/images/providers/" + providerId + ".png").toString)
    }
  }
}

case class MiniUser(
   id: UUID,
   fullName: String,
   avatarURL: String,
   email: Option[String])

object MiniUser {
  implicit val miniUserFormat: Format[MiniUser] = (
    (__ \ "id").format[UUID] and
    (__ \ "fullName").format[String] and
    (__ \ "avatarUrl").format[String] and
    (__ \ "email").format[Option[String]]
  ) (MiniUser.apply, unlift(MiniUser.unapply))
}

case class Profile(
  avatarUrl: Option[String] = None,
  biography: Option[String] = None,
  currentprojects: List[String] = List.empty,
  institution: Option[String] = None,
  orcidID: Option[String] = None,
  pastprojects: List[String] = List.empty,
  position: Option[String] = None,
  emailsettings: Option[String] = None
) {
  /** return position at institution */
  def getPositionAtInstitution: String = {
    (position, institution) match {
      case (Some(p), Some(i)) => s"$p at $i"
      case (Some(p), None) => p
      case (None, Some(i)) => i
      case (None, None) => ""
    }
  }
}

case class UserTermsOfServices(
  accepted: Boolean = false,
  acceptedDate: Date = null,
  acceptedVersion: String = ""
)

case class SocialInformation(
  lastLogin: Date = new Date(),
  loginCount: Long = 0)
