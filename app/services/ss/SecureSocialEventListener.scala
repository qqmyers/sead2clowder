package services.ss

import java.util.Date

import play.api.Logger
import play.api.mvc.{RequestHeader, Session}
import services.{AppConfiguration, DI, SpaceService, UserService}

import securesocial.core._
import models.{Profile, Provider, UUID, User, UserTermsOfServices}

class SecureSocialEventListener(app: play.api.Application) extends EventListener {
  override def id: String = "SecureSocialEventListener"

  lazy val users: UserService = DI.injector.getInstance(classOf[UserService])
  lazy val spaces: SpaceService = DI.injector.getInstance(classOf[SpaceService])

  def onEvent(event: Event, request: RequestHeader, session: Session): Option[Session] = {
    event match {
      case e: LoginEvent => {
        if (users.findByProvider(event.user.identityId.providerId, event.user.identityId.userId).isEmpty) {
          newUser(event.user, request)
        } else {
          updateUser(event.user)
        }
      }
      case e: SignUpEvent => {
        newUser(event.user, request)
      }
      case _ => {}
    }
    None
  }

  private def updateUser(identity: Identity) = {
    // now update/add the actual user
    users.findByProvider(identity.identityId.providerId, identity.identityId.userId) match {
      case Some(u) => {
        users.save(u.copy(firstName=identity.firstName, lastName=identity.lastName, fullName=identity.fullName,
          email=identity.email, avatarUrl=identity.avatarUrl))

        // update user information recording login
        users.recordLogin(u.id)
      }
      case None => Logger.error(s"Could not find user with ${identity.identityId.providerId}/${identity.identityId.userId}")
    }
  }

  private def newUser(identity: Identity, request: RequestHeader) = {
    Logger.info("new user signup : " + identity)

    val register = play.Play.application().configuration().getBoolean("registerThroughAdmins", true)
    val admins = play.Play.application().configuration().getString("initialAdmins").split("\\s*,\\s*")

    // enable account. Admins are always enabled.
    val (active, serverAdmin) = identity.email match {
      case Some(e) if admins.contains(e) => (true, true)
      case _ => (!register, false)
    }

    // if signedup using local account ToS is already accepted
    val tos = if (identity.identityId.providerId == "userpass") {
      Some(UserTermsOfServices(accepted=true, new Date(), AppConfiguration.getTermsOfServicesVersionString))
    } else {
      None
    }

    // always set orcid id if logged in with orcid
    val profile = if (identity.identityId.providerId == ORCIDProvider.ORCID) {
      Some(Profile(orcidID=Some(identity.identityId.userId)))
    } else {
      None
    }

    val user = User(firstName=identity.firstName, lastName=identity.lastName, fullName=identity.fullName,
      email=identity.email, avatarUrl=identity.avatarUrl, termsOfServices=tos, active=active, serverAdmin=serverAdmin,
      provider=Provider(identity.identityId.providerId, identity.identityId.userId))
    users.save(user)

    // send a message
    val subject = s"[${AppConfiguration.getDisplayName}] new user signup"
    val body = views.html.emails.userSignup(user)(request)
    util.Mail.sendEmailAdmins(subject, Some(user), body)

    // see if the user needs to be added to a space
    checkSpaceInvite(user)

    // update user information recording login
    users.recordLogin(user.id)
  }

  private def checkSpaceInvite(user: User) = {
    // check if user should be added to a space
    if (user.email.isDefined) {
      spaces.getInvitationByEmail(user.email.get).foreach { invite =>
        users.findRole(invite.role) match {
          case Some(role) => {
            spaces.addUser(user.id, role, invite.space)
            spaces.removeInvitationFromSpace(UUID(invite.invite_id), invite.space)
          }
          case None => Logger.error(s"Error adding to the invited space. The role '${invite.role}' assigned doesn't exist")
        }
      }
    }
  }
}
