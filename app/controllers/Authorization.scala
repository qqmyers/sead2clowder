package controllers

import javax.inject.{Inject, Singleton}

import api.{Permission, WithPermission}
import models.Authorization
import play.api.mvc.Action
import securesocial.core.Identity
import services.{AuthorizationService}

/**
 * Manage user authorization.
 *
 * Created by Luigi Marini on 8/27/14.
 */
@Singleton
class Authorization @Inject()(authorization: AuthorizationService) extends SecuredController {

//  def userManagement() = SecuredAction(authorization = WithPermission(Permission.ManageUsers)) { implicit request =>
  def userManagement() = Action { implicit request =>
    val usersWithRoles = authorization.listUsersWithRoles()
    Ok(views.html.auth.userManagement(usersWithRoles))
  }
}
