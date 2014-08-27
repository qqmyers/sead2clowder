package core

import play.api.{ Plugin, Logger, Application }
import play.api.mvc.{Session, RequestHeader}
import securesocial.core._
import services.{DI, AuthorizationService}

/**
 * Listen for logins.
 *
 * Created by Luigi Marini on 8/26/14.
 */
class LoginEventListener (app: Application) extends EventListener with Plugin {

  val authorizationService: AuthorizationService =  DI.injector.getInstance(classOf[AuthorizationService])
  override def id: String = "login_event_listener"

  def onEvent(event: Event, request: RequestHeader, session: Session): Option[Session] = {
    val eventName = event match {
      case e: LoginEvent => onLogin(e.user.identityId)
      case e: LogoutEvent => "logout"
      case e: SignUpEvent => "signup"
      case e: PasswordResetEvent => "password reset"
      case e: PasswordChangeEvent => "password change"
    }
    Logger.info("traced %s event for user %s".format(eventName, event.user.fullName))
    None
  }

  def onLogin(identityId: IdentityId) {
    if (!authorizationService.userExists(identityId)) authorizationService.createDefaultAuthorization(identityId)
    "login"
  }
}
