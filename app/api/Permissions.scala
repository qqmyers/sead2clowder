package api

import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request
import models.AppConfiguration
import services.AppConfigurationService
import models.UUID
import services.FileService
import services.DatasetService
import services.CollectionService
import services.UserAccessRightsService
import play.api.Logger

 /**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](user: Option[Identity], request: Request[A]) extends WrappedRequest(request)

/**
 * List of all permissions available in Medici
 * 
 * @author Rob Kooper
 */
object Permission extends Enumeration {
	type Permission = Value
	val Public,					// Page is public accessible if set so by the admin, i.e. no login needed
		PublicOpen,				//Page always accessible
		Admin,
		Subscribe,
		Unsubscribe,
		CreateCollections,
		DeleteCollections,
		AdministrateCollections,
		ListCollections,
		ShowCollection,             
		CreateDatasets,
		DeleteDatasets,
		AdministrateDatasets,
		ListDatasets,
		ShowDataset,				
		SearchDatasets,				
		AddDatasetsMetadata,
		ShowDatasetsMetadata,
		ShowTags,
		CreateTagsDatasets,
		DeleteTagsDatasets,
		UpdateDatasetInformation,
		UpdateLicenseFiles,
		UpdateLicenseDatasets,
		CreateComments,
		RemoveComments,
		EditComments,
		CreateNotesDatasets,	
		AddSections,
		GetSections,
		CreateTagsSections,
		DeleteTagsSections,
		CreateFiles,
		DeleteFiles,
		AdministrateFiles,
		ListFiles,
		AddFilesMetadata,
		ShowFilesMetadata,			
		ShowFile,					
		SearchFiles,
		CreateTagsFiles,
		DeleteTagsFiles,
		CreateNotesFiles,
		CreateStreams,
		AddDataPoints,
		SearchStreams,
		AddZoomTile,
		Add3DTexture,
		AddIndex,
		CreateSensors,
		ListSensors,
		GetSensors,
		SearchSensors,
		RemoveSensors,
		AddThumbnail,
		DownloadFiles = Value		
}

import api.Permission._

/**
 * Specific implementation of an Authorization
 * 
 * @author Rob Kooper
 */
case class WithPermission(permission: Permission, resourceId: Option[UUID] = None) extends Authorization {
  
  val appConfiguration: AppConfigurationService = services.DI.injector.getInstance(classOf[AppConfigurationService])
  val files: FileService = services.DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = services.DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])
  var accessRights: UserAccessRightsService = services.DI.injector.getInstance(classOf[UserAccessRightsService])
  

	def isAuthorized(user: Identity): Boolean = {
    
	  	val externalViewingEnabled = appConfiguration.getDefault.get.viewNoLoggedIn
	  
		// order is important
		(user, permission) match {
		  case (_, requestedPermission)  =>{
		    resourceId match{
		      case Some(idOfResource) => {	//A request for accessing a specific resource, subject to individualized resource access rights
		        var administrateOrModify = "modify"
		        if(requestedPermission == AdministrateFiles || requestedPermission == AdministrateDatasets || requestedPermission == AdministrateCollections
		             || requestedPermission == UpdateLicenseFiles  || requestedPermission == UpdateLicenseDatasets){
		                administrateOrModify = "administrate"
		        }

		        //Modification and administration permissions require the user to be logged in
		        if((requestedPermission == CreateFiles || requestedPermission == DeleteFiles || requestedPermission == AddFilesMetadata || requestedPermission == AdministrateFiles || requestedPermission == CreateNotesFiles
		            || requestedPermission == UpdateLicenseFiles)){
		          if(user != null){
		           files.get(idOfResource) match{
		            case Some(file)=>{		              
		              
		              //User is the author of the resource
		              if(file.author.identityId.userId.equals(user.identityId.userId))
		                true
		              //User has permission to modify the resource  
		              else if(accessRights.checkForPermission(user, idOfResource.stringify, "file", administrateOrModify))
		                true
		              //Finally, is the user admin?  
		              else
		                appConfiguration.adminExists(user.email.getOrElse("none"))
		            }
		            case _ =>{
		              Logger.error("File requested to be accessed not found. Denying request.")
		              false
		            }
		          }
		         }
		          else false
		        }	
		        else if((requestedPermission == CreateDatasets || requestedPermission == DeleteDatasets || requestedPermission == AddDatasetsMetadata || requestedPermission == AdministrateDatasets 
		            || requestedPermission == CreateNotesDatasets || requestedPermission == UpdateLicenseDatasets || requestedPermission == UpdateDatasetInformation)){
		          if(user != null){
			           datasets.get(idOfResource) match{
			            case Some(dataset)=>{
			              if(dataset.author.identityId.userId.equals(user.identityId.userId))
			                true
			              else if(accessRights.checkForPermission(user, idOfResource.stringify, "dataset", administrateOrModify))
			                true  
			              else
			                appConfiguration.adminExists(user.email.getOrElse("none"))
			            }
			            case _ =>{
			              Logger.error("Dataset requested to be accessed not found. Denying request.")
			              false
			            }
			          }
		          }
		          else false
		          
		        }
		        else if((requestedPermission == CreateCollections || requestedPermission == DeleteCollections || requestedPermission == AdministrateCollections)){
		          if(user != null){
			           collections.get(idOfResource) match{
			            case Some(collection)=>{
			              collection.author match{
			                case Some(collectionAuthor)=>{
			                  if(collectionAuthor.identityId.userId.equals(user.identityId.userId))
			                	  true
			                  else if(accessRights.checkForPermission(user, idOfResource.stringify, "collection", administrateOrModify))
			                	  true  
			                  else
			                	  appConfiguration.adminExists(user.email.getOrElse("none"))
			                }
			                //Anonymous collections are free-for-all
			                case None=>{
			                 if(requestedPermission != AdministrateCollections){ 
			                  Logger.info("Requested collection is anonymous, anyone can modify, granting modification request.")
			                  true
			                 }
			                 else
			                   false
			                }
			              }		              
			            }
			            case _ =>{
			              Logger.error("Collection requested to be accessed not found. Denying request.")
			              false
			            }
			          }
		          }
		          else false
		          
		        }
		        
		        //Viewing permissions do not require a logged in user (at least for public resources)
		        else if(requestedPermission == ShowFilesMetadata || requestedPermission == ShowFile || requestedPermission == DownloadFiles){
		          //If external viewing is enabled (public repository), all can view and download
		          if(externalViewingEnabled)
		            true
		          else{
			              files.get(idOfResource) match{
				            case Some(file)=>{
				              
				              //Resource is public
				              if(file.isPublic.getOrElse(false))
				            	true
				              //Anonymous resource	
//				              else if(file.author.fullName.equals("Anonymous User"))
//				                true
				              else if(user != null){  
					              //User is the author of the resource
					              if(file.author.identityId.userId.equals(user.identityId.userId))
					                true
					              //User has permission to view the resource  
					              else if(accessRights.checkForPermission(user, idOfResource.stringify, "file", "view"))
					                true
					              //Finally, is the user admin?  
					              else
					                appConfiguration.adminExists(user.email.getOrElse("none"))
				              }
				              else
				                false
				            }
				            case _ =>{
				              Logger.error("File requested to be accessed not found. Denying request.")
				              false
				            }
			          }
		            }  
		        }
		        else if(requestedPermission == ShowDataset || requestedPermission == ShowDatasetsMetadata){
		          if(externalViewingEnabled)
		            true
		          else{
			              datasets.get(idOfResource) match{
				            case Some(dataset)=>{
				              
				              if(dataset.isPublic.getOrElse(false))
				            	true
//				              else if(dataset.author.fullName.equals("Anonymous User"))
//				                true
				              else if(user != null){  
					              if(dataset.author.identityId.userId.equals(user.identityId.userId))
					                true
					              else if(accessRights.checkForPermission(user, idOfResource.stringify, "dataset", "view"))
					                true
					              else
					                appConfiguration.adminExists(user.email.getOrElse("none"))
				              }
				              else
				                false
				            }
				            case _ =>{
				              Logger.error("Dataset requested to be accessed not found. Denying request.")
				              false
				            }
			          }
		            }  
		        }
		        else if(requestedPermission == ShowCollection){
		          if(externalViewingEnabled)
		            true
		          else{
			              collections.get(idOfResource) match{
				            case Some(collection)=>{				              
				              collection.author match{
				                case Some(collectionAuthor)=>{
				                  				                  
				                  if(collection.isPublic.getOrElse(false))
				                	  true
//				                  else if(collectionAuthor.fullName.equals("Anonymous User"))
//				                	  true
				                  else if(user != null){  
					                  if(collectionAuthor.identityId.userId.equals(user.identityId.userId))
					                	  true
					                  else if(accessRights.checkForPermission(user, idOfResource.stringify, "collection", "view"))
					                	  true  
					                  else
					                	  appConfiguration.adminExists(user.email.getOrElse("none"))
				                  }
				                  else
				                    false
				                }
				                //Anonymous collections are free-for-all
				                case None=>{
				                  Logger.info("Requested collection is anonymous, anyone can view, granting view request.")
				                  true				                 
				                }
				              }	
				            }
				            case _ =>{
				              Logger.error("Collection requested to be accessed not found. Denying request.")
				              false
				            }
			          }
		            }  
		        }
		        
		        else{
		          true
		        }
		      }
		      case _ =>{	//Not a request for a specific resource (or request was done using REST API secret key), so not subject to individualized resource access rights
		        (user, permission) match {
		          // anybody can list/show if admin decides so (or else must be logged in), 'open public' pages always
			  	  case (theUser, PublicOpen)           => true
				  case (theUser, Public)         	   => (externalViewingEnabled || theUser != null)		  
				  case (theUser, ListCollections)      => (externalViewingEnabled || theUser != null)
				  case (theUser, ShowCollection)       => (externalViewingEnabled || theUser != null)
				  case (theUser, ListDatasets)         => (externalViewingEnabled || theUser != null)
				  case (theUser, ShowDataset)          => (externalViewingEnabled || theUser != null)
				  case (theUser, SearchDatasets)       => (externalViewingEnabled || theUser != null)
				  case (theUser, SearchFiles)	       => (externalViewingEnabled || theUser != null)
				  case (theUser, GetSections)          => (externalViewingEnabled || theUser != null)
				  case (theUser, ListFiles)            => (externalViewingEnabled || theUser != null)
				  case (theUser, ShowFile)             => (externalViewingEnabled || theUser != null)
				  case (theUser, ShowFilesMetadata)    => (externalViewingEnabled || theUser != null)
				  case (theUser, ShowDatasetsMetadata) => (externalViewingEnabled || theUser != null)
				  case (theUser, SearchStreams)        => (externalViewingEnabled || theUser != null)
				  case (theUser, ListSensors)          => (externalViewingEnabled || theUser != null)
				  case (theUser, GetSensors)           => (externalViewingEnabled || theUser != null)
				  case (theUser, SearchSensors)        => (externalViewingEnabled || theUser != null)
				  case (theUser, DownloadFiles)        => (externalViewingEnabled || theUser != null)
				  case (theUser, ShowTags)             => (externalViewingEnabled || theUser != null)
				  
				  // all other permissions require authenticated user
				  case (null, _)                 => false
				  case(_, Permission.Admin) =>{
				    if(!user.email.isEmpty)
				    	if(appConfiguration.adminExists(user.email.get))
				    	  true
				    	else
				    	  false  
				    else	  
				    	false	  
				  }
				  case(_,_) => true
		        } 
		      }
		    }
		  }
		}
	}
}
