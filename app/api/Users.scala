package api

import javax.inject.{ Singleton, Inject }
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import services.UserAccessRightsService
import services.mongodb.MongoUserService
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
  
  /**
   * Upgrade or demote a user's access rights to a file
   */
  @ApiOperation(value = "Upgrade or demote a user's access rights to a file",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToFile() = SecuredAction(authorization=WithPermission(Permission.AdministrateFiles)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
          (request.body \ "resourceId").asOpt[String].map { fileId =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(user) =>{
                  users.setPermissionLevel(user, fileId, "file", newPermissionLevel)
                  Ok("Permissions for user to file set to chosen level.")
                }
                case None =>{
                  Logger.error("Error getting user with full name " + fullName + " and email " + email)
                  Ok("No user with supplied full name and email exists.")
                }
              }
            }.getOrElse {
              Logger.debug("Missing parameter [newPermissionLevel]")
        	  BadRequest(toJson("Missing parameter [newPermissionLevel]"))
            }
          }.getOrElse {
            Logger.debug("Missing parameter [resourceId]")
        	BadRequest(toJson("Missing parameter [resourceId]"))
          }
        }.getOrElse {
          Logger.debug("Missing parameter [userEmail]")
          BadRequest(toJson("Missing parameter [userEmail]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [userFullName]")
        BadRequest(toJson("Missing parameter [userFullName]"))
    }
    
  }
  
  
  /**
   * Upgrade or demote a user's access rights to a dataset
   */
  @ApiOperation(value = "Upgrade or demote a user's access rights to a dataset",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToDataset() = SecuredAction(authorization=WithPermission(Permission.AdministrateDatasets)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
          (request.body \ "resourceId").asOpt[String].map { datasetId =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(user) =>{
                  users.setPermissionLevel(user, datasetId, "dataset", newPermissionLevel)
                  Ok("Permissions for user to dataset set to chosen level.")
                }
                case None =>{
                  Logger.error("Error getting user with full name " + fullName + " and email " + email)
                  Ok("No user with supplied full name and email exists.")
                }
              }
            }.getOrElse {
              Logger.debug("Missing parameter [newPermissionLevel]")
        	  BadRequest(toJson("Missing parameter [newPermissionLevel]"))
            }
          }.getOrElse {
            Logger.debug("Missing parameter [resourceId]")
        	BadRequest(toJson("Missing parameter [resourceId]"))
          }
        }.getOrElse {
          Logger.debug("Missing parameter [userEmail]")
          BadRequest(toJson("Missing parameter [userEmail]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [userFullName]")
        BadRequest(toJson("Missing parameter [userFullName]"))
    }
    
  }
  
  /**
   * Upgrade or demote a user's access rights to a collection
   */
  @ApiOperation(value = "Upgrade or demote a user's access rights to a collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToCollection() = SecuredAction(authorization=WithPermission(Permission.AdministrateCollections)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
          (request.body \ "resourceId").asOpt[String].map { collectionId =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(user) =>{
                  users.setPermissionLevel(user, collectionId, "collection", newPermissionLevel)
                  Ok("Permissions for user to collection set to chosen level.")
                }
                case None =>{
                  Logger.error("Error getting user with full name " + fullName + " and email " + email)
                  Ok("No user with supplied full name and email exists.")
                }
              }
            }.getOrElse {
              Logger.debug("Missing parameter [newPermissionLevel]")
        	  BadRequest(toJson("Missing parameter [newPermissionLevel]"))
            }
          }.getOrElse {
            Logger.debug("Missing parameter [resourceId]")
        	BadRequest(toJson("Missing parameter [resourceId]"))
          }
        }.getOrElse {
          Logger.debug("Missing parameter [userEmail]")
          BadRequest(toJson("Missing parameter [userEmail]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [userFullName]")
        BadRequest(toJson("Missing parameter [userFullName]"))
    }
    
  }
  
}