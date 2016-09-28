package services.ss

import java.util.Date

import api.{Permission, UserRequest}
import models.UUID
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import play.api.{Logger, Play}
import play.api.mvc.Request
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import securesocial.core.{Authenticator, SecureSocial}
import services.{DI, UserService}

/**
  * Wrapper for secure social code to users
  */
object SecureSocialUser {
  lazy val users: UserService = DI.injector.getInstance(classOf[UserService])

  def saveToken(uuid: UUID, email: String): Date = {
    val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
    val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
    val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
    val expires = DateTime.now().plusMinutes(TokenDuration)

    val token = Token(uuid.stringify, email, DateTime.now(), DateTime.now().plusMinutes(TokenDuration), isSignUp=true)
    securesocial.core.UserService.save(token)

    expires.toDate
  }

  def checkUser[A](request: Request[A]): Boolean = {
    val result = for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <-  securesocial.core.UserService.find(authenticator.identityId)
    ) yield {
      // we should be able to use the authenticator.timedOut directly but it never returns true
      identity
    }

    result.isDefined
  }

  def getUser[A](request: Request[A], checkUserPassword: Boolean): Option[UserRequest[A]] = {
    val superAdmin = request.cookies.get("superAdmin").exists(_.value.toBoolean)

    for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <- securesocial.core.UserService.find(authenticator.identityId)
    ) yield {
      Authenticator.save(authenticator.touch)
      users.findByProvider(identity.identityId.providerId, identity.identityId.userId) match {
        case Some(x) if Permission.checkServerAdmin(Some(x)) => {
          return Some(UserRequest(Some(x.copy(superAdminMode=superAdmin)), request))
        }
        case Some(x) => return Some(UserRequest(Some(x), request))
        case None => Logger.error(s"Could not find user with ${identity.identityId.providerId}/${identity.identityId.userId}")
      }
    }

    if (checkUserPassword) {
      request.headers.get("Authorization").foreach { authHeader =>
        val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
        val credentials = header.split(":")

        securesocial.core.UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword).foreach { identity =>
          if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
            users.findByProvider(identity.identityId.providerId, identity.identityId.userId) match {
              case Some(x) if Permission.checkServerAdmin(Some(x)) => {
                return Some(UserRequest(Some(x.copy(superAdminMode=superAdmin)), request))
              }
              case Some(x) => return Some(UserRequest(Some(x), request))
              case None => Logger.error(s"Could not find user with ${identity.identityId.providerId}/${identity.identityId.userId}")
            }
          } else {
            Logger.debug(s"Password did not match for ${identity.email}")
          }
        }
      }
    }

    None
  }
}
