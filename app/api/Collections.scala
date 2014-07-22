package api

import play.api.Logger
import play.api.Play.current
import models.{UUID, Collection}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import services.DatasetService
import services.CollectionService
import services.AdminsNotifierPlugin
import services.UserAccessRightsService
import services.AppConfigurationService
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date
import securesocial.core.Identity
import models.UserPermissions

/**
 * Manipulate collections.
 * 
 * @author Constantinos Sophocleous
 */
@Api(value = "/collections", listingPath = "/api-docs.json/collections", description = "Collections are groupings of datasets")
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService, accessRights: UserAccessRightsService, appConfiguration: AppConfigurationService) extends ApiController {


  @ApiOperation(value = "Create a collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def createCollection() = SecuredAction(authorization=WithPermission(Permission.CreateCollections)) {
    request =>
      Logger.debug("Creating new collection")
      (request.body \ "name").asOpt[String].map {
        name =>
          (request.body \ "description").asOpt[String].map {
            description =>
              var isPublic = false
              (request.body \ "isPublic").asOpt[Boolean].map {
                inputIsPublic=>
                  isPublic = inputIsPublic
              }.getOrElse{}
              
              val c = Collection(name = name, description = description, created = new Date(), author = request.user, isPublic = Some(request.user.get.fullName.equals("Anonymous User")||isPublic))
              accessRights.addPermissionLevel(request.user.get, c.id.stringify, "collection", "administrate")
              collections.insert(c) match {
                case Some(id) => {
                 Ok(toJson(Map("id" -> id)))
                }
                case None => Ok(toJson(Map("status" -> "error")))
              }
          }.getOrElse(BadRequest(toJson("Missing parameter [description]")))
      }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  @ApiOperation(value = "Add dataset to collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachDataset(collectionId: UUID, datasetId: UUID) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections), resourceId = Some(collectionId)) { request =>

    collections.addDataset(collectionId, datasetId) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  @ApiOperation(value = "Remove dataset from collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections), resourceId = Some(collectionId)) { request =>

    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  @ApiOperation(value = "Remove collection",
      notes = "Does not delete the individual datasets in the collection.",
      responseClass = "None", httpMethod = "POST")
  def removeCollection(collectionId: UUID) = SecuredAction(parse.anyContent,
                       authorization=WithPermission(Permission.DeleteCollections), resourceId = Some(collectionId)) { request =>
    collections.delete(collectionId)
    accessRights.removeResourceRightsForAll(collectionId.stringify, "collection")
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "List all collections",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def listCollections() = SecuredAction(parse.anyContent,
                                        authorization=WithPermission(Permission.ListCollections)) { request =>
     
     var list: List[play.api.libs.json.JsValue] = List.empty                                    
     implicit val user = request.user
      var rightsForUser: Option[models.UserPermissions] = None
      user match{
		        case Some(theUser)=>{
		            rightsForUser = accessRights.get(theUser)
		            list = for (collection <- collections.listCollections() if(checkAccessForCollectionUsingRightsList(collection, request.user , "view", rightsForUser))) yield jsonCollection(collection)
		        }
		        case None=>{
		           list = for (collection <- collections.listCollections() if(checkAccessForCollection(collection, request.user, "view"))) yield jsonCollection(collection)
		        }
      }

    Ok(toJson(list))
  }
  
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
               "created" -> collection.created.toString))
  }
  
  @ApiOperation(value = "Set whether a collection is open for public viewing.",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def setIsPublic(id: UUID) = SecuredAction(authorization = WithPermission(Permission.AdministrateCollections), resourceId = Some(id)) {
    request =>
        	(request.body \ "isPublic").asOpt[Boolean].map { isPublic =>
        	  collections.get(id)match{
        	    case Some(collection)=>{
        	      collections.setIsPublic(id, isPublic)
        	      Ok("Done")
        	    }
        	    case None=>{
        	      Logger.error("Error getting collection with id " + id.stringify)
                  Ok("No collection with supplied id exists.")
        	    }
        	  } 
	       }.getOrElse {
	    	   BadRequest(toJson("Missing parameter [isPublic]"))
	       }
  }
  
  def checkAccessForCollection(collection: Collection, user: Option[Identity], permissionType: String): Boolean = {
    var isAuthorless = true
    collection.author match{
      case Some(author)=>{
        isAuthorless = collection.author.get.fullName.equals("Anonymous User")
      }
      case None=>{}
    }
    
    if(permissionType.equals("view") && (collection.isPublic.getOrElse(false) || isAuthorless || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          var userIsAuthor = false
          if(!isAuthorless){
            userIsAuthor = collection.author.get.identityId.userId.equals(theUser.identityId.userId)
          }
          
          theUser.fullName.equals("Anonymous User") || appConfiguration.adminExists(theUser.email.getOrElse("")) || userIsAuthor || accessRights.checkForPermission(theUser, collection.id.stringify, "collection", permissionType)
        }
        case None=>{
          false
        }
      }
    }
  }
  
  def checkAccessForCollectionUsingRightsList(collection: Collection, user: Option[Identity], permissionType: String, rightsForUser: Option[UserPermissions]): Boolean = {
    var isAuthorless = true
    collection.author match{
      case Some(author)=>{
        isAuthorless = collection.author.get.fullName.equals("Anonymous User")
      }
      case None=>{}
    }
    
    if(permissionType.equals("view") && (collection.isPublic.getOrElse(false) || isAuthorless || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          var userIsAuthor = false
          if(!isAuthorless){
            userIsAuthor = collection.author.get.identityId.userId.equals(theUser.identityId.userId)
          }
          
          val canAccessWithoutRightsList = theUser.fullName.equals("Anonymous User") || appConfiguration.adminExists(theUser.email.getOrElse("")) || userIsAuthor
          rightsForUser match{
	        case Some(userRights)=>{
	        	if(canAccessWithoutRightsList)
	        	  true
	        	else{
	        	  if(permissionType.equals("view")){
			        userRights.collectionsViewOnly.contains(collection.id.stringify)
			      }else if(permissionType.equals("modify")){
			        userRights.collectionsViewModify.contains(collection.id.stringify)
			      }else if(permissionType.equals("administrate")){
			        userRights.collectionsAdministrate.contains(collection.id.stringify)
			      }
			      else{
			        Logger.error("Unknown permission type")
			        false
			      }
	        	}
	        }
	        case None=>{
	          canAccessWithoutRightsList
	        }
	      }
        }
        case None=>{
          false
        }
      }
    }
  }
  
  
  
  
}

