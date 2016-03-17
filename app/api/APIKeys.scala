package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import play.api.Logger
import play.api.libs.json.Json
import services.{UserService, APIKeyService}

/**
  * Manage API keys.
  */
class APIKeys @Inject()(keyService: APIKeyService, userService: UserService) extends ApiController {

  /**
    * Returns the user that is making the request. Used to verify authentication, as well as for user data access.
    *
    * TODO switch to an Action that only allows basic auth until AuthenticatedAction which allows anonymous global key.
    */
  @ApiOperation(value = "Creates an API key", responseClass = "", httpMethod = "POST")
  def createKey() = AuthenticatedAction { implicit request =>
    request.user match {
      case Some(identity) => {
        Logger.debug("Creating a key for user " + identity)
        val key = keyService.createKey("foo", identity.id)
        Ok(Json.toJson(Map("key"->key)))
      }
      case None => {
        Unauthorized("Not authenticated")
      }
    }
  }

  /**
    * Returns the user that is making the request. Used to verify authentication, as well as for user data access.
    *
    * TODO switch to an Action that only allows basic auth until AuthenticatedAction which allows anonymous global key.
    */
  @ApiOperation(value = "Get API key info", httpMethod = "GET")
  def getKeyInfo(key: String) = AuthenticatedAction { implicit request =>
    request.user match {
      case Some(identity) => {
        Logger.debug("Getting key info")
        (for (
          userId <- keyService.getUserId(key);
          user <- userService.get(userId)
        ) yield Ok(Json.toJson(Map("user_name" -> user.fullName)))
        ).getOrElse(InternalServerError(Json.toJson(Map("status" -> "Key not found"))))
      }
      case None => {
        Unauthorized("Not authenticated")
      }
    }
  }

  /**
    * Delete API key.
    *
    * TODO switch to an Action that only allows basic auth until AuthenticatedAction which allows anonymous global key.
    */
  @ApiOperation(value = "Delete API key", httpMethod = "DELETE")
  def deleteKey(key: String) = AuthenticatedAction { implicit request =>
    request.user match {
      case Some(identity) =>
        Logger.debug("Deleting key")
        keyService.deleteKey(key)
        Ok(Json.toJson(Map("status" -> "ok")))
      case None =>
        Unauthorized("Not authenticated")
    }
  }

}
