package api

import play.api.Logger
import play.api.Play.current
import models.{UUID, Collection}
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import services.{PreviewService, DatasetService, CollectionService, AdminsNotifierPlugin, UserAccessRightsService, AppConfigurationService}
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date
import securesocial.core.Identity
import models.UserPermissions
import controllers.Utils

/**
 * Manipulate collections.
 * 
 * @author Constantinos Sophocleous
 */
@Api(value = "/collections", listingPath = "/api-docs.json/collections", description = "Collections are groupings of datasets")
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService, accessRights: UserAccessRightsService, appConfiguration: AppConfigurationService, previews: PreviewService) extends ApiController {
    
  @ApiOperation(value = "Create a collection",
      notes = "Accepted JSON:{\"name\":\"select a name\",\"description\":\"select a description\"}",
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
    collections.get(collectionId) match{
      case Some(collection) => {
        collections.delete(collectionId)
        current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",collection.id.stringify, collection.name)}
      }
    }                                             
    //Success anyway, as if collection is not found it is most probably deleted already
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
        isAuthorless = false
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
        isAuthorless = false
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

  /**
   * Add preview to file.
   */
  @ApiOperation(value = "Attach existing preview to collection",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def attachPreview(collection_id: UUID, preview_id: UUID) = SecuredAction(authorization = WithPermission(Permission.EditCollection)) {
    request =>
      // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        eid
      } else {
        Logger.debug("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        Some("Other")
      }
      val preview_type = (request.body \ "preview_type").asOpt[String].getOrElse("")
      request.body match {
        case JsObject(fields) => {
          collections.get(collection_id) match {
            case Some(collection) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToCollection(preview_id, collection_id, preview_type, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("Collection not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("Collection not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

}

