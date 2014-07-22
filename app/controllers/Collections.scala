
package controllers

import play.api.data.Form
import play.api.data.Forms._
import models.{UUID, Collection}
import java.util.Date
import play.api.Logger
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import javax.inject.{ Singleton, Inject }
import services.{ DatasetService, CollectionService, UserAccessRightsService}
import services.AdminsNotifierPlugin
import services.ElasticsearchPlugin
import play.api.Play.current
import securesocial.core.Identity
import services.AppConfigurationService
import models.UserPermissions
import models.Dataset

object ThumbnailFound extends Exception {}

@Singleton
class Collections @Inject()(datasets: DatasetService, collections: CollectionService, accessRights: UserAccessRightsService, appConfiguration: AppConfigurationService) extends SecuredController {

  /**
   * New dataset form.
   */
  val collectionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
      ((name, description) => Collection(name = name, description = description, created = new Date, author = null))
      ((collection: Collection) => Some((collection.name, collection.description)))
  )

  def newCollection() = SecuredAction(authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newCollection(collectionForm))
  }

  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization = WithPermission(Permission.ListCollections)) {
    implicit request =>
      implicit val user = request.user
      var rightsForUser: Option[models.UserPermissions] = None
      user match{
		        case Some(theUser)=>{
		            rightsForUser = accessRights.get(theUser)
		        }
		        case None=>{
		        }
      }
      
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var collectionList = List.empty[models.Collection]
      if (direction == "b") {
        collectionList = collections.listCollectionsBefore(date, limit, user)
      } else if (direction == "a") {
        collectionList = collections.listCollectionsAfter(date, limit, user)
      } else {
        badRequest
      }

      // latest object
      val latest = collections.latest(user)
      // first object
      val first = collections.first(user)
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = collectionList.exists(_.id.equals(latest.get.id))
        lastPage = collectionList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (collectionList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(collectionList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(collectionList.last.created)
        }
      }

      Ok(views.html.collectionList(collectionList, prev, next, limit, rightsForUser))
    
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }

  /**
   * Create collection.
   */
  def submit() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user
      user match {
	      case Some(identity) => {
	      
	      collectionForm.bindFromRequest.fold(
	        errors => BadRequest(views.html.newCollection(errors)),
	        collection => {
	          Logger.debug("Saving collection " + collection.name)

	          accessRights.addPermissionLevel(request.user.get, collection.id.stringify, "collection", "administrate")
	          var isPublicOption = request.body.asFormUrlEncoded.get("collectionPrivatePublic")
		        if(!isPublicOption.isDefined)
		          isPublicOption = Some(List("false"))	        
		        val isPublic = isPublicOption.get(0).toBoolean
	          
	          collections.insert(Collection(id = collection.id, name = collection.name, description = collection.description, created = collection.created, author = Some(identity), isPublic=Some(isPublic) ))
	          
	          // index collection
		            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id, 
		                List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}
	                    
	          // redirect to collection page
	          Redirect(routes.Collections.collection(collection.id))
	          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Collection","added",collection.id.toString,collection.name)}
	          Redirect(routes.Collections.collection(collection.id))
	        })
	      }
	      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
      }
  }

  /**
   * Collection.
   */
  def collection(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowCollection), resourceId = Some(id)) {
    implicit request =>
      Logger.debug(s"Showing collection $id")
      implicit val user = request.user
      var rightsForUser: Option[UserPermissions] = None
      collections.get(id) match {
        case Some(collection) => { 
          Logger.debug(s"Found collection $id")
          var datasetsInCollection: List[Dataset] = List.empty
          val datasetsChecker = services.DI.injector.getInstance(classOf[controllers.Datasets])
          user match{
		        case Some(theUser)=>{
		            rightsForUser = accessRights.get(theUser)
		            datasetsInCollection = for(checkedDataset <- datasets.listInsideCollection(id); if(datasetsChecker.checkAccessForDatasetUsingRightsList(checkedDataset, user, "view", rightsForUser))) yield checkedDataset
		        }
		        case None=>{
		          datasetsInCollection = for(checkedDataset <- datasets.listInsideCollection(id); if(datasetsChecker.checkAccessForDataset(checkedDataset, user, "view"))) yield checkedDataset
		        }
          }
          
          Ok(views.html.collectionofdatasets(datasetsInCollection, collection, rightsForUser))
        }
        case None => {
          Logger.error("Error getting collection " + id); BadRequest("Collection not found")
        }
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
          
          appConfiguration.adminExists(theUser.email.getOrElse("")) || userIsAuthor || accessRights.checkForPermission(theUser, collection.id.stringify, "collection", permissionType)
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
          
          val canAccessWithoutRightsList =  appConfiguration.adminExists(theUser.email.getOrElse("")) || userIsAuthor
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
