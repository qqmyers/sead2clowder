package controllers

import play.api.mvc.Action
import services.ss.SecureSocialUser

/**
  * Login class for checking if User is still logged in.
  */
class Login extends SecuredController {
  def isLoggedIn = Action { implicit request =>
    if (SecureSocialUser.checkUser(request)) {
      Ok("yes")
    } else {
      Ok("no")
    }
  }
}
