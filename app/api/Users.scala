package api

import javax.inject.{ Singleton, Inject }
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import services.UserAccessRightsService
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json._
import models.UserPermissions


/**
 * Manipulate users.
 * 
 * @author Constantinos Sophocleous
 */
@Api(value = "/users", listingPath = "/api-docs.json/users", description = "Users of Medici")
@Singleton
class Users @Inject() (users: UserAccessRightsService) extends ApiController {
  
  /**
   * Initialize user access rights
   */
  @ApiOperation(value = "Initialize user access rights",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def initRights() = SecuredAction(authorization=WithPermission(Permission.PublicOpen)) { 
    request =>
      Logger.debug("Initializing user access rights")
      (request.body \ "email").asOpt[String].map { email =>
        (request.body \ "name").asOpt[String].map { name =>
          val userRights = UserPermissions(name=name, email=email)
          Logger.debug(userRights.toString()  );
          users.initRights(userRights)
          Ok
        }.getOrElse {
          Logger.debug("Missing parameter [name]")
        	BadRequest(toJson("Missing parameter [name]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [email]")
      BadRequest(toJson("Missing parameter [email]"))
    }
    
  }
  
}