/**
 *
 */
package api

import java.util.Date
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json._
import jsonutils.JsonUtil
import controllers.Previewers
import org.bson.types.ObjectId
import play.api.Play.current
import javax.inject.{Singleton, Inject}
import com.mongodb.casbah.Imports._
import org.json.JSONObject
import Transformation.LidoToCidocConvertion
import java.io.BufferedWriter
import java.io.FileWriter
import play.api.libs.iteratee.Enumerator
import java.io.FileInputStream
import play.api.libs.concurrent.Execution.Implicits._
import services._
import play.api.libs.json.JsString
import scala.Some
import models.File
import play.api.Play.configuration
import securesocial.core.Identity

/**
 * Dataset API.
 *
 * @author Luigi Marini
 *
 */
@Api(value = "/datasets", listingPath = "/api-docs.json/datasets", description = "A dataset is a container for files and metadata")
@Singleton
class Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  extractions: ExtractionService,
  accessRights: UserAccessRightsService,
  rdfsparql: RdfSPARQLService,
  appConfiguration: AppConfigurationService) extends ApiController {

  /**
   * List all datasets.
   */
  @ApiOperation(value = "List all datasets",
      notes = "Returns list of datasets and descriptions.",
      responseClass = "None", httpMethod = "GET")
  def list = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ListDatasets)) {
    request =>
      var list: List[JsValue] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = (for (d <- datasets.listDatasets if(checkAccessForDatasetUsingRightsList(d, request.user , "view", rightsForUser))) yield d).map(datasets.toJSON(_))
	        }
	        case None=>{
	          list = (for (d <- datasets.listDatasets if(checkAccessForDataset(d, request.user , "view"))) yield d).map(datasets.toJSON(_))
	        }
	      }

      Ok(toJson(list))
  }

  /**
   * List all datasets outside a collection.
   */
  def listOutsideCollection(collectionId: UUID) = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.ShowCollection), resourceId = Some(collectionId)) {
    request =>

      collections.get(collectionId) match {
        case Some(collection) => {
        	request.user match{
		        case Some(theUser)=>{
		            val rightsForUser = accessRights.get(theUser)
		            val list = for (dataset <- datasets.listDatasetsChronoReverse; if (!datasets.isInCollection(dataset, collection) && checkAccessForDatasetUsingRightsList(dataset, request.user, "view", rightsForUser) )) yield datasets.toJSON(dataset)
		            Ok(toJson(list))
		        }
		        case None=>{
		        	val list = for (dataset <- datasets.listDatasetsChronoReverse; if (!datasets.isInCollection(dataset, collection) && checkAccessForDataset(dataset, request.user, "view") )) yield datasets.toJSON(dataset)
		            Ok(toJson(list))
		        }
          }
        }
        case None => {
        	request.user match{
		        case Some(theUser)=>{
		            val rightsForUser = accessRights.get(theUser)
		            val list = for (dataset <- datasets.listDatasetsChronoReverse; if (checkAccessForDatasetUsingRightsList(dataset, request.user, "view", rightsForUser) )) yield datasets.toJSON(dataset)
		            Ok(toJson(list))
		        }
		        case None=>{
		        	val list = for (dataset <- datasets.listDatasetsChronoReverse; if (checkAccessForDataset(dataset, request.user, "view") )) yield datasets.toJSON(dataset)
		            Ok(toJson(list))
		        }
          }
        }
      }
  }
  
  /**
   * Sort all datasets in the input list in reverse chronological order.
   */
  def sortDatasetsChronoReverse(inOrder: List[Dataset]): List[Dataset] = {
    inOrder.sortBy(_.created).reverse
  }

  /**
   * Create new dataset
   */
  @ApiOperation(value = "Create new dataset",
      notes = "New dataset containing one existing file, based on values of fields in attached JSON. Returns dataset id as JSON object.",
      responseClass = "None", httpMethod = "POST")
  def createDataset() = SecuredAction(authorization = WithPermission(Permission.CreateDatasets)) {
    request =>
      Logger.debug("Creating new dataset")
      (request.body \ "name").asOpt[String].map { name =>
      	  (request.body \ "description").asOpt[String].map { description =>
      	    (request.body \ "file_id").asOpt[String].map { file_id =>
      	      files.get(UUID(file_id)) match {
      	        case Some(file) =>
      	           var isPublic = false
	              (request.body \ "isPublic").asOpt[Boolean].map {
	                inputIsPublic=>
	                  isPublic = inputIsPublic
	              }.getOrElse{}
      	          
      	           val d = Dataset(name=name,description=description, created=new Date(), files=List(file), author=request.user.get, isPublic = Some(request.user.get.fullName.equals("Anonymous User")||isPublic ))
      	           accessRights.addPermissionLevel(request.user.get, d.id.stringify, "dataset", "administrate")
		      	   datasets.insert(d) match {
		      	     case Some(id) => {
                       files.index(UUID(file_id))
                       if(!file.xmlMetadata.isEmpty){
                         val xmlToJSON = files.getXMLMetadataJSON(UUID(file_id))
                         datasets.addXMLMetadata(UUID(id), UUID(file_id), xmlToJSON)
                         current.plugin[ElasticsearchPlugin].foreach {
                              _.index("data", "dataset", UUID(id),
                                List(("name", d.name), ("description", d.description), ("xmlmetadata", xmlToJSON)))
                            }
                       }
                       else{
                         current.plugin[ElasticsearchPlugin].foreach {
                              _.index("data", "dataset", UUID(id),
                                List(("name", d.name), ("description", d.description)))
                            }
                       }
                       Ok(toJson(Map("id" -> id)))
                       current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Dataset","added",id, name)} 
                       Ok(toJson(Map("id" -> id)))
		      	     }
		      	     case None => Ok(toJson(Map("status" -> "error")))
		      	   }
      	        case None => BadRequest(toJson("Bad file_id = " + file_id))
      	      }
      	   }.getOrElse {
      		  BadRequest(toJson("Missing parameter [file_id]"))
      	   }
      	  }.getOrElse {
      		BadRequest(toJson("Missing parameter [description]"))
      	  }
    }.getOrElse {
      BadRequest(toJson("Missing parameter [name]"))
    }
  }
  
  @ApiOperation(value = "Set whether a dataset is open for public viewing.",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def setIsPublic() = SecuredAction(authorization = WithPermission(Permission.AdministrateDatasets)) {
    request =>
      (request.body \ "resourceId").asOpt[String].map { datasetId =>
        	(request.body \ "isPublic").asOpt[Boolean].map { isPublic =>
        	  datasets.get(UUID(datasetId))match{
        	    case Some(dataset)=>{
        	      datasets.setIsPublic(UUID(datasetId), isPublic)
        	      Ok("Done")
        	    }
        	    case None=>{
        	      Logger.error("Error getting dataset with id " + datasetId)
                  Ok("No dataset with supplied id exists.")
        	    }
        	  } 
	       }.getOrElse {
	    	   BadRequest(toJson("Missing parameter [isPublic]"))
	       }
      }.getOrElse {
    	   BadRequest(toJson("Missing parameter [resourceId]"))
       }
  }

  @ApiOperation(value = "Attach existing file to dataset",
      notes = "If the file is an XML metadata file, the metadata are added to the dataset.",
      responseClass = "None", httpMethod = "POST")
  def attachExistingFile(dsId: UUID, fileId: UUID) = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.CreateDatasets), resourceId = Some(dsId)) {
	request =>
     datasets.get(dsId) match {
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            if (!files.isInDataset(file, dataset)) {
	            datasets.addFile(dsId, file)	            
	            files.index(fileId)
	            if (!file.xmlMetadata.isEmpty){
                  datasets.index(dsId)
	            }	            
       
	            if(dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
		                        datasets.updateThumbnail(dataset.id, UUID(file.thumbnail_id.get))
		                        
		                        for(collectionId <- dataset.collections){
		                          collections.get(UUID(collectionId)) match{
		                            case Some(collection) =>{
		                            	if(collection.thumbnail_id.isEmpty){ 
		                            		collections.updateThumbnail(collection.id, UUID(file.thumbnail_id.get))
		                            	}
		                            }
		                            case None=>{}
		                          }
		                        }
	            }
	            
	            //add file to RDF triple store if triple store is used
                if (file.filename.endsWith(".xml")) {
                  configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                    case "yes" => rdfsparql.linkFileToDataset(fileId, dsId)
                    case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
                  }
                }
                Logger.info("Adding file to dataset completed")
              } else Logger.info("File was already in dataset.")
              Ok(toJson(Map("status" -> "success")))
            }
            case None => Logger.error("Error getting file" + fileId); InternalServerError
          }
        }
        case None => Logger.error("Error getting dataset" + dsId); InternalServerError
      }
  }
  
  @ApiOperation(value = "Detach file from dataset",
      notes = "File is not deleted, only separated from the selected dataset. If the file is an XML metadata file, the metadata are removed from the dataset.",
      responseClass = "None", httpMethod = "POST")
  def detachFile(datasetId: UUID, fileId: UUID, ignoreNotFound: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateDatasets), resourceId = Some(datasetId)) {
    request =>
     datasets.get(datasetId) match{
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            if(files.isInDataset(file, dataset)){
	            //remove file from dataset
	            datasets.removeFile(dataset.id, file.id)
	            files.index(fileId)
	            if (!file.xmlMetadata.isEmpty)
                  datasets.index(datasetId)
                  
	            Logger.info("Removing file from dataset completed")
	            
	            if(!dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
	              if(dataset.thumbnail_id.get == file.thumbnail_id.get){
	            	  datasets.createThumbnail(dataset.id)
		             
		             			for(collectionId <- dataset.collections){
		                          collections.get(UUID(collectionId)) match{
		                            case Some(collection) =>{		                              
		                            	if(!collection.thumbnail_id.isEmpty){
		                            		if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
		                            			collections.createThumbnail(collection.id)
		                            		}		                        
		                            	}
		                            }
		                            case None=>{}
		                          }
		                        }
	              }		                        
	            }
	            
	           //remove link between dataset and file from RDF triple store if triple store is used
                if (file.filename.endsWith(".xml")) {
                  configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                    case "yes" => rdfsparql.detachFileFromDataset(fileId, datasetId)
                    case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
                  }
                }
               }
              else  Logger.info("File was already out of the dataset.")
              Ok(toJson(Map("status" -> "success")))
            }
            case None => Ok(toJson(Map("status" -> "success")))
          }
        }
        case None => {
          ignoreNotFound match {
            case "True" => Ok(toJson(Map("status" -> "success")))
            case "False" => Logger.error(s"Error getting dataset $datasetId"); InternalServerError
          }
        }
      }
  }
  
  //////////////////

  @ApiOperation(value = "List all datasets in a collection", notes = "Returns list of datasets and descriptions.", responseClass = "None", httpMethod = "GET")
  def listInCollection(collectionId: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowCollection), resourceId = Some(collectionId)) {
    request =>
      collections.get(collectionId) match {
        case Some(collection) => {
        	request.user match{
		        case Some(theUser)=>{
		            val rightsForUser = accessRights.get(theUser)
		            val list = for (dataset <- datasets.listInsideCollection(collectionId); if (checkAccessForDatasetUsingRightsList(dataset, request.user, "view", rightsForUser) )) yield datasets.toJSON(dataset)
		            Ok(toJson(list))
		        }
		        case None=>{
		        	val list = for (dataset <- datasets.listInsideCollection(collectionId); if (checkAccessForDataset(dataset, request.user, "view") )) yield datasets.toJSON(dataset)
		            Ok(toJson(list))
		        }
          }
        }
        case None => Logger.error("Error getting collection" + collectionId); InternalServerError
      }
  }

  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: UUID) = SecuredAction(authorization = WithPermission(Permission.AddDatasetsMetadata), resourceId = Some(id)) {
    request =>
      Logger.debug(s"Adding metadata to dataset $id")
      datasets.addMetadata(id, Json.stringify(request.body))
      datasets.index(id)
      Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "Add user-generated metadata to dataset",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def addUserMetadata(id: UUID) = SecuredAction(authorization = WithPermission(Permission.AddDatasetsMetadata), resourceId = Some(id)) {
    request =>
      Logger.debug(s"Adding user metadata to dataset $id")
      datasets.addUserMetadata(id, Json.stringify(request.body))
      datasets.index(id)
      configuration.getString("userdfSPARQLStore").getOrElse("no") match {
        case "yes" => datasets.setUserMetadataWasModified(id, true)
        case _ => Logger.debug("userdfSPARQLStore not enabled")
      }
      Ok(toJson(Map("status" -> "success")))
  }


  def datasetFilesGetIdByDatasetAndFilename(datasetId: UUID, filename: String): Option[String] = {
    datasets.get(datasetId) match {
      case Some(dataset) => {
        for (file <- dataset.files) {
          if (file.filename.equals(filename)) {
            return Some(file.id.toString)
          }
        }
        Logger.error(s"File does not exist in dataset $datasetId.")
        None
      }
      case None => Logger.error(s"Error getting dataset $datasetId."); None
    }
  }

  @ApiOperation(value = "List files in dataset",
      notes = "Datasets and descriptions.",
      responseClass = "None", httpMethod = "GET")
  def datasetFilesList(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset), resourceId = Some(id)) {
    request =>
      datasets.get(id) match {
        case Some(dataset) => {
          var list: List[JsValue] = List.empty
          val filesChecker = services.DI.injector.getInstance(classOf[api.Files])
          request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = for (f <- dataset.files if(filesChecker.checkAccessForFileUsingRightsList(f, request.user , "view", rightsForUser))) yield jsonFile(f, request.user, rightsForUser)
	        }
	        case None=>{
	          list = for (f <- dataset.files if(filesChecker.checkAccessForFile(f, request.user, "view"))) yield jsonFile(f, request.user)
	        }
	      }  
            
          Ok(toJson(list))
        }
        case None => Logger.error("Error getting dataset" + id); InternalServerError
      }
  }

  def jsonFile(file: File, user: Option[Identity] = None, rightsForUser: Option[UserPermissions] = None): JsValue = {
    var userRequested = "None"
    var userCanEdit = false
    val filesChecker = services.DI.injector.getInstance(classOf[api.Files])
    user match{
        case Some(theUser)=>{
          userRequested = theUser.fullName
          userCanEdit = filesChecker.checkAccessForFileUsingRightsList(file, user, "modify", rightsForUser)
        }
        case None=>{
          userRequested = "None"
          userCanEdit = filesChecker.checkAccessForFile(file, user, "modify")
        }
      }  
    
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType,
               "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString, "authorId" -> file.author.identityId.userId,
               "usercanedit" -> userCanEdit.toString, "userThatRequested" -> userRequested))
  }
  


  // ---------- Tags related code starts ------------------
  /**
   * REST endpoint: GET: get the tag data associated with this section.
   * Returns a JSON object of multiple fields.
   * One returned field is "tags", containing a list of string values.
   */
  @ApiOperation(value = "Get the tags associated with this dataset", notes = "Returns a JSON object of multiple fields", responseClass = "None", httpMethod = "GET")
  def getTags(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset)) {
    implicit request =>
      Logger.info(s"Getting tags for dataset with id  $id.")
      /* Found in testing: given an invalid ObjectId, a runtime exception
       * ("IllegalArgumentException: invalid ObjectId") occurs.  So check it first.
       */
      if (UUID.isValid(id.stringify)) {
        datasets.get(id) match {
          case Some(dataset) =>
            Ok(Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "tags" -> Json.toJson(dataset.tags.map(_.name))))
          case None => {
            Logger.error(s"The dataset with id $id is not found.")
            NotFound(toJson(s"The dataset with id $id is not found."))
          }
        }
      } else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  @ApiOperation(value = "Remove tag of dataset",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeTag(id: UUID) = SecuredAction(parse.json, authorization = WithPermission(Permission.DeleteTagsDatasets)) {
    implicit request =>
      Logger.debug("Removing tag " + request.body)
      request.body.\("tagId").asOpt[String].map {
        tagId =>
          Logger.debug(s"Removing $tagId from $id.")
          datasets.removeTag(id, UUID(tagId))
      }
      Ok(toJson(""))
  }

  /**
   * REST endpoint: POST: Add tags to a dataset.
   * Requires that the request body contains a "tags" field of List[String] type.
   */
  @ApiOperation(value = "Add tags to dataset",
      notes = "Requires that the request body contains a 'tags' field of List[String] type.",
      responseClass = "None", httpMethod = "POST")
  def addTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateTagsDatasets)) {
    implicit request =>
      addTagsHelper(TagCheck_Dataset, id, request)
  }

  /**
   * REST endpoint: POST: remove tags.
   * Requires that the request body contains a "tags" field of List[String] type.
   */
  @ApiOperation(value = "Remove tags of dataset",
      notes = "Requires that the request body contains a 'tags' field of List[String] type.",
      responseClass = "None", httpMethod = "POST")
  def removeTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DeleteTagsDatasets)) {
    implicit request =>
      removeTagsHelper(TagCheck_Dataset, id, request)
  }

  /*
 *  Helper function to handle adding and removing tags for files/datasets/sections.
 *  Input parameters:
 *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
 *      op_type:  one of the two strings "add", "remove"
 *      id:       the id in the original addTags call
 *      request:  the request in the original addTags call
 *  Return type:
 *      play.api.mvc.SimpleResult[JsValue]
 *      in the form of Ok, NotFound and BadRequest
 *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
 *      which contains the cause of the error, such as "No 'tags' specified", and
 *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
 */
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: adding the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.addTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => {
          datasets.addTags(id, userOpt, extractorOpt, tagsCleaned)
          datasets.index(id)
        }
        case TagCheck_Section => sections.addTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: removing the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.removeTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => datasets.removeTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Section => sections.removeTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  // TODO: move helper methods to standalone service
  val USERID_ANONYMOUS = "anonymous"

  // Helper class and function to check for error conditions for tags.
  class TagCheck {
    var error_str: String = ""
    var not_found: Boolean = false
    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var tags: Option[List[String]] = None
  }

  /*
  *  Helper function to check for error conditions.
  *  Input parameters:
  *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
  *      id:       the id in the original addTags call
  *      request:  the request in the original addTags call
  *  Returns:
  *      tagCheck: a TagCheck object, containing the error checking results:
  *
  *      If error_str == "", then no error is found;
  *      otherwise, it contains the cause of the error.
  *      not_found is one of the error conditions, meaning the object with
  *      the given id is not found in the DB.
  *      userOpt, extractorOpt and tags are set according to the request's content,
  *      and will remain None if they are not specified in the request.
  *      We change userOpt from its default None value, only if the userId
  *      is not USERID_ANONYMOUS.  The use case for this is the extractors
  *      posting to the REST API -- they'll use the commKey to post, and the original
  *      userId of these posts is USERID_ANONYMOUS -- in this case, we'd like to
  *      record the extractor_id, but omit the userId field, so we leave userOpt as None.
  */
  def checkErrorsForTag(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]): TagCheck = {
    val userObj = request.user
    Logger.debug("checkErrorsForTag: user id: " + userObj.get.identityId.userId + ", user.firstName: " + userObj.get.firstName
      + ", user.LastName: " + userObj.get.lastName + ", user.fullName: " + userObj.get.fullName)
    val userId = userObj.get.identityId.userId
    if (USERID_ANONYMOUS == userId) {
      Logger.debug("checkErrorsForTag: The user id is \"anonymous\".")
    }

    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var error_str = ""
    var not_found = false
    val tags = request.body.\("tags").asOpt[List[String]]

    if (tags.isEmpty) {
      error_str = "No \"tags\" specified, request.body: " + request.body.toString
    } else if (!UUID.isValid(id.stringify)) {
      error_str = "The given id " + id + " is not a valid ObjectId."
    } else {
      obj_type match {
        case TagCheck_File => not_found = files.get(id).isEmpty
        case TagCheck_Dataset => not_found = datasets.get(id).isEmpty
        case TagCheck_Section => not_found = sections.get(id).isEmpty
        case _ => error_str = "Only file/dataset/section is supported in checkErrorsForTag()."
      }
      if (not_found) {
        error_str = s"The $obj_type with id $id is not found"
      }
    }
    if ("" == error_str) {
      if (USERID_ANONYMOUS == userId) {
        val eid = request.body.\("extractor_id").asOpt[String]
        eid match {
          case Some(extractor_id) => extractorOpt = eid
          case None => error_str = "No \"extractor_id\" specified, request.body: " + request.body.toString
        }
      } else {
        userOpt = Option(userId)
      }
    }
    val tagCheck = new TagCheck
    tagCheck.error_str = error_str
    tagCheck.not_found = not_found
    tagCheck.userOpt = userOpt
    tagCheck.extractorOpt = extractorOpt
    tagCheck.tags = tags
    tagCheck
  }

  /**
   * REST endpoint: POST: remove all tags.
   */
  @ApiOperation(value = "Remove all tags of dataset",
      notes = "Forcefully remove all tags for this dataset.  It is mainly intended for testing.",
      responseClass = "None", httpMethod = "POST")
  def removeAllTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DeleteTagsDatasets)) {
    implicit request =>
      Logger.info(s"Removing all tags for dataset with id: $id.")
      if (UUID.isValid(id.stringify)) {
        datasets.get(id) match {
          case Some(dataset) => {
            datasets.removeAllTags(id)
            Ok(Json.obj("status" -> "success"))
          }
          case None => {
            val msg = s"The dataset with id $id is not found."
            Logger.error(msg)
            NotFound(toJson(msg))
          }
        }
      } else {
        val msg = s"The given id $id is not a valid ObjectId."
        Logger.error(msg)
        BadRequest(toJson(msg))
      }
  }

  // ---------- Tags related code ends ------------------

  @ApiOperation(value = "Add comment to dataset", notes = "", responseClass = "None", httpMethod = "POST")
  def comment(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateComments)) {
    implicit request =>
      request.user match {
        case Some(identity) => {
          request.body.\("text").asOpt[String] match {
            case Some(text) => {
              val comment = new Comment(identity, text, dataset_id = Some(id))
              comments.insert(comment)
              datasets.index(id)
              Ok(comment.id.toString())
            }
            case None => {
              Logger.error("no text specified.")
              BadRequest
            }
          }
        }
        case None => BadRequest
      }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchDatasetsUserMetadata = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    request =>
      Logger.debug("Searching datasets' user metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = datasets.searchUserMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")
      
      var list: List[JsValue] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = for (dataset <- searchQuery; if(checkAccessForDatasetUsingRightsList(dataset, request.user , "view", rightsForUser))) yield datasets.toJSON(dataset, request.user, rightsForUser)
	        }
	        case None=>{
	          list = for (dataset <- searchQuery; if(checkAccessForDataset(dataset, request.user , "view"))) yield datasets.toJSON(dataset, request.user)
	        }
	 }

      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }

  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchDatasetsGeneralMetadata = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    request =>
      Logger.debug("Searching datasets' metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = datasets.searchAllMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")
      
      var list: List[JsValue] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	list = for (dataset <- searchQuery; if(checkAccessForDatasetUsingRightsList(dataset, request.user , "view", rightsForUser))) yield datasets.toJSON(dataset, request.user, rightsForUser)
	        }
	        case None=>{
	          list = for (dataset <- searchQuery; if(checkAccessForDataset(dataset, request.user , "view"))) yield datasets.toJSON(dataset, request.user)
	        }
	 }

      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }

  /**
   * Return whether a dataset is currently being processed.
   */
  @ApiOperation(value = "Is being processed",
      notes = "Return whether a dataset is currently being processed by a preprocessor.",
      responseClass = "None", httpMethod = "GET")
  def isBeingProcessed(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset)) {
    request =>
      datasets.get(id) match {
        case Some(dataset) => {
          val filesInDataset = dataset.files map {f => files.get(f.id).get}

          var isActivity = "false"
          try {
            for (f <- filesInDataset) {
              extractions.findIfBeingProcessed(f.id) match {
                case false =>
                case true => {
                  isActivity = "true"
                  throw ActivityFound
                }
              }
            }
          } catch {
            case ActivityFound =>
          }

          Ok(toJson(Map("isBeingProcessed" -> isActivity)))
        }
        case None => {
          Logger.error(s"Error getting dataset $id"); InternalServerError
        }
      }
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7, prvFile.id)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: String, pId: String, pPath: String, pMain: String, pvRoute: String, pvContentType: String, pvLength: Long, originalFileId: UUID): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId, "p_id" -> pId,
                 "p_path" -> controllers.routes.Assets.at(pPath).toString,
                 "p_main" -> pMain, "pv_route" -> pvRoute,
                 "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
                 "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(UUID(pvId), originalFileId).toString,
                 "pv_annotationsDeletePath" -> api.routes.Previews.deleteAnnotation(UUID(pvId), originalFileId).toString,
                 "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(UUID(pvId)).toString,
                 "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(UUID(pvId), originalFileId).toString))
    else
      toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }

  @ApiOperation(value = "Get dataset previews",
      notes = "Return the currently existing previews of the selected dataset (full description, including paths to preview files, previewer names etc).",
      responseClass = "None", httpMethod = "GET")
  def getPreviews(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDataset), resourceId = Some(id)) {
    request =>
      datasets.get(id) match {
        case Some(dataset) => {
          val innerFiles = dataset.files map {f => files.get(f.id).get}
          val datasetWithFiles = dataset.copy(files = innerFiles)
          val previewers = Previewers.findPreviewers
          val previewslist = for (f <- datasetWithFiles.files; if (f.showPreviews.equals("DatasetLevel"))) yield {
            val pvf = for (p <- previewers; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield {
              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
            }
            if (pvf.length > 0) {
              (f -> pvf)
            } else {
              val ff = for (p <- previewers; if (p.contentType.contains(f.contentType))) yield {
                (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id) + "/blob", f.contentType, f.length)
              }
              (f -> ff)
            }
          }
          Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
        }
        case None => {
          Logger.error("Error getting dataset" + id); InternalServerError
        }
      }
  }

  @ApiOperation(value = "Delete dataset",
      notes = "Cascading action (deletes all previews and metadata of the dataset and all files existing only in the deleted dataset).",
      responseClass = "None", httpMethod = "POST")
  def deleteDataset(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DeleteDatasets), resourceId = Some(id)) {
    request =>
      datasets.get(id) match {
        case Some(dataset) => {
          //remove dataset from RDF triple store if triple store is used
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => rdfsparql.removeDatasetFromGraphs(id)
            case _ => Logger.debug("userdfSPARQLStore not enabled")
          }
          datasets.removeDataset(id)
          accessRights.removeResourceRightsForAll(id.stringify, "dataset")
          Ok(toJson(Map("status" -> "success")))
          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Dataset","removed",dataset.id.stringify, dataset.name)}
          Ok(toJson(Map("status"->"success")))
        }
        case None => Ok(toJson(Map("status" -> "success")))
      }
  }

  @ApiOperation(value = "Get the user-generated metadata of the selected dataset in an RDF file",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def getRDFUserMetadata(id: UUID, mappingNumber: String="1") = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata), resourceId = Some(id)) {implicit request =>
    
    current.plugin[RDFExportService].isDefined match{
      case true => {
        current.plugin[RDFExportService].get.getRDFUserMetadataDataset(id.toString, mappingNumber) match{
          case Some(resultFile) =>{
            Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
			            	.withHeaders(CONTENT_TYPE -> "application/rdf+xml")
			            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + resultFile.getName()))
          }
          case None => BadRequest(toJson("Dataset not found " + id))
        }
      }
      case _ => Ok("RDF export plugin not enabled")
     }
    }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    Logger.debug("thexml: " + xml)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }
  
  @ApiOperation(value = "Get URLs of dataset's RDF metadata exports",
      notes = "URLs of metadata exported as RDF from XML files contained in the dataset, as well as the URL used to export the dataset's user-generated metadata as RDF.",
      responseClass = "None", httpMethod = "GET")
  def getRDFURLsForDataset(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata), resourceId = Some(id)) { request =>

    current.plugin[RDFExportService].isDefined match{
      case true =>{
	    current.plugin[RDFExportService].get.getRDFURLsForDataset(id.toString)  match {
	      case Some(listJson) => {
	        Ok(listJson) 
	      }
	      case None => Logger.error(s"Error getting dataset $id"); InternalServerError
	    }
      }
      case false => {
        Ok("RDF export plugin not enabled")
      }
    }
  }

  @ApiOperation(value = "Get technical metadata of the dataset",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def getTechnicalMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowDatasetsMetadata), resourceId = Some(id)) {
    request =>
      datasets.get(id) match {
        case Some(dataset) => Ok(datasets.getTechnicalMetadataJSON(id))
        case None => Logger.error("Error finding dataset" + id); InternalServerError
      }
  }

  def getXMLMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata), resourceId = Some(id)) { request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getXMLMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }
  def getUserMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata), resourceId = Some(id)) { request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getUserMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }
  
  
  
  
  def checkAccessForDataset(dataset: models.Dataset, user: Option[Identity], permissionType: String): Boolean = {
    if(permissionType.equals("view") && (dataset.isPublic.getOrElse(false) || dataset.author.fullName.equals("Anonymous User") || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          theUser.fullName.equals("Anonymous User") || appConfiguration.adminExists(theUser.email.getOrElse("")) || dataset.author.identityId.userId.equals(theUser.identityId.userId) || accessRights.checkForPermission(theUser, dataset.id.stringify, "dataset", permissionType)
        }
        case None=>{
          false
        }
      }
    }
  }
  
  def checkAccessForDatasetUsingRightsList(dataset: models.Dataset, user: Option[Identity], permissionType: String, rightsForUser: Option[UserPermissions]): Boolean = {
    if(permissionType.equals("view") && (dataset.isPublic.getOrElse(false) || dataset.author.fullName.equals("Anonymous User") || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          val canAccessWithoutRightsList =  theUser.fullName.equals("Anonymous User") || appConfiguration.adminExists(theUser.email.getOrElse("")) || dataset.author.identityId.userId.equals(theUser.identityId.userId)
          rightsForUser match{
	        case Some(userRights)=>{
	        	if(canAccessWithoutRightsList)
	        	  true
	        	else{
	        	  if(permissionType.equals("view")){
			        userRights.datasetsViewOnly.contains(dataset.id.stringify)
			      }else if(permissionType.equals("modify")){
			        userRights.datasetsViewModify.contains(dataset.id.stringify)
			      }else if(permissionType.equals("administrate")){
			        userRights.datasetsAdministrate.contains(dataset.id.stringify)
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

object ActivityFound extends Exception {}
