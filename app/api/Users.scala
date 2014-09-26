package api

import javax.inject.{ Singleton, Inject }
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import services.UserAccessRightsService
import services.FileService
import services.DatasetService
import services.CollectionService
import services.mongodb.MongoUserService
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json._
import models.UserPermissions
import models.UUID


/**
 * Manipulate users.
 * 
 * @author Constantinos Sophocleous
 */
@Api(value = "/users", listingPath = "/api-docs.json/users", description = "Users of Medici")
@Singleton
class Users @Inject() (users: UserAccessRightsService, files: FileService, datasets: DatasetService, collections: CollectionService) extends ApiController {
  
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
          users.findByEmailAndFullName(email, name) match{
            case Some(user)=>{
              Logger.debug("User already signed up")
              BadRequest(toJson("User already signed up"))
            }case None=>{
              val userRights = UserPermissions(name=name, email=email)
	          Logger.debug(userRights.toString()  );
	          users.initRights(userRights)
	          Ok
            }
          }

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
            notes = """Accepted JSON:{<br/>
&emsp;&emsp;"userFullName": The full name the target user uses in Medici<br/>
&emsp;&emsp;"userEmail": The email the target user uses to log in to Medici<br/>
&emsp;&emsp;"newPermissionLevel": Can be "view", "modify", "administrate" or "noaccess"}""",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToFile(fileId: UUID) = SecuredAction(authorization=WithPermission(Permission.AdministrateFiles), resourceId = Some(fileId)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(user) =>{
                  users.setPermissionLevel(user, fileId.stringify, "file", newPermissionLevel)
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
            notes = """Accepted JSON:{<br/>
&emsp;&emsp;"userFullName": The full name the target user uses in Medici<br/>
&emsp;&emsp;"userEmail": The email the target user uses to log in to Medici<br/>
&emsp;&emsp;"newPermissionLevel": Can be "view", "modify", "administrate" or "noaccess"}""",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToDataset(datasetId: UUID) = SecuredAction(authorization=WithPermission(Permission.AdministrateDatasets), resourceId = Some(datasetId)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(user) =>{
                  users.setPermissionLevel(user, datasetId.stringify, "dataset", newPermissionLevel)
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
          Logger.debug("Missing parameter [userEmail]")
          BadRequest(toJson("Missing parameter [userEmail]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [userFullName]")
        BadRequest(toJson("Missing parameter [userFullName]"))
    }
    
  }
  
  /**
   * Upgrade or demote a target user's access rights to all files in a dataset the requester has administrate rights on
   */
  @ApiOperation(value = "Upgrade or demote a target user's access rights to all files in a dataset the requester has administrate rights on",
            notes = """Accepted JSON:{<br/>
&emsp;&emsp;"userFullName": The full name the target user uses in Medici<br/>
&emsp;&emsp;"userEmail": The email the target user uses to log in to Medici<br/>
&emsp;&emsp;"newPermissionLevel": Can be "view", "modify", "administrate" or "noaccess"}""",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToDatasetFiles(datasetId: UUID) = SecuredAction(authorization=WithPermission(Permission.AdministrateDatasets), resourceId = Some(datasetId)) {    
    request => 
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(targetUser) =>{
                 datasets.get(datasetId) match{
                   case Some(theDataset)=>{
                	   val filesChecker = services.DI.injector.getInstance(classOf[api.Files])
			          request.user match{
				        case Some(requester)=>{
				        	val rightsForRequester = users.get(requester)
				        	for (f <- theDataset.files if(filesChecker.checkAccessForFileUsingRightsList(f, request.user , "administrate", rightsForRequester))){
				        	  users.setPermissionLevel(targetUser, f.id.stringify, "file", newPermissionLevel)
				        	}
				        }
				        case None=>{
//				          for (f <- theDataset.files if(filesChecker.checkAccessForFile(f, request.user , "administrate"))){
//				        	  users.setPermissionLevel(targetUser, f.id.stringify, "file", newPermissionLevel)
//				        	}
				        }
				      }
	                  	                  
	                  Ok("Permissions for target user set to chosen level for all files in dataset that the requester has admininstration rights for.")
                   }
                   case None =>{
                     Logger.error("Dataset not found.")
                     Ok("Dataset not found.")
                   }
                 } 
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
          Logger.debug("Missing parameter [userEmail]")
          BadRequest(toJson("Missing parameter [userEmail]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [userFullName]")
        BadRequest(toJson("Missing parameter [userFullName]"))
    }
    
  }
  
  /**
   * Upgrade or demote a target user's access rights to all datasets in a collection the requester has administrate rights on
   */
  @ApiOperation(value = "Upgrade or demote a target user's access rights to all datasets in a collection the requester has administrate rights on",
            notes = """Accepted JSON:{<br/>
&emsp;&emsp;"userFullName": The full name the target user uses in Medici<br/>
&emsp;&emsp;"userEmail": The email the target user uses to log in to Medici<br/>
&emsp;&emsp;"newPermissionLevel": Can be "view", "modify", "administrate" or "noaccess"}""",
      responseClass = "None", httpMethod = "POST")
  def modifyRightsToCollectionDatasets(collectionId: UUID) = SecuredAction(authorization=WithPermission(Permission.AdministrateCollections), resourceId = Some(collectionId)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(targetUser) =>{
                 collections.get(collectionId) match{  
                   case Some(theCollection)=>{
                	   val datasetsChecker = services.DI.injector.getInstance(classOf[api.Datasets])
                	   val filesChecker = services.DI.injector.getInstance(classOf[api.Files])
			          request.user match{
				        case Some(requester)=>{
				        	val rightsForRequester = users.get(requester)
				        	for (d <- theCollection.datasets if(datasetsChecker.checkAccessForDatasetUsingRightsList(d, request.user , "administrate", rightsForRequester))){
				        	  users.setPermissionLevel(targetUser, d.id.stringify, "dataset", newPermissionLevel)
				        	    for (f <- d.files if(filesChecker.checkAccessForFileUsingRightsList(f, request.user , "administrate", rightsForRequester))){
					        	  users.setPermissionLevel(targetUser, f.id.stringify, "file", newPermissionLevel)
					        	}
				        	}
				        }
				        case None=>{
//				          for (d <- theCollection.datasets if(datasetsChecker.checkAccessForDataset(d, request.user , "administrate"))){
//				        	  users.setPermissionLevel(targetUser, d.id.stringify, "dataset", newPermissionLevel)
//				        	}
				        }
				      }
	                  	                  
	                  Ok("Permissions for target user set to chosen level for all datasets in collection that the requester has admininstration rights for.")
                   }
                   case None =>{
                     Logger.error("Collection not found.")
                     Ok("Collection not found.")
                   }
                 } 
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
  def modifyRightsToCollection(collectionId: UUID) = SecuredAction(authorization=WithPermission(Permission.AdministrateCollections), resourceId = Some(collectionId)) { 
    request =>
      Logger.debug("Setting user access rights")
      (request.body \ "userFullName").asOpt[String].map { fullName =>
        (request.body \ "userEmail").asOpt[String].map { email =>
            (request.body \ "newPermissionLevel").asOpt[String].map { newPermissionLevel =>
              users.findByEmailAndFullName(email, fullName) match{
                case Some(user) =>{
                  users.setPermissionLevel(user, collectionId.stringify, "collection", newPermissionLevel)
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
          Logger.debug("Missing parameter [userEmail]")
          BadRequest(toJson("Missing parameter [userEmail]"))
       }
      }.getOrElse {
        Logger.debug("Missing parameter [userFullName]")
        BadRequest(toJson("Missing parameter [userFullName]"))
    }
    
  }
  
}