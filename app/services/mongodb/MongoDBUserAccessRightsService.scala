package services.mongodb

import javax.inject.{Singleton, Inject}
import services.UserAccessRightsService
import models.UserPermissions
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import securesocial.core.Identity



/**
 * Use Mongodb to store user access rights.
 * 
 * @author Constantinos Sophocleous
 *
 */
@Singleton
class MongoDBUserAccessRightsService extends UserAccessRightsService {

  def initRights(userPermissions: UserPermissions): Option[String] = {  
    UserPermissions.insert(userPermissions).map(_.toString) 
  }
  
  def get(user: Identity): Option[UserPermissions] = {
    UserPermissions.findOne($and("email" -> user.email.getOrElse(""), "name" -> user.fullName))
  }
  
  def addPermissionLevel(user: Identity, resourceId: String, resourceType: String, permissionType: String) = {
    if(permissionType.equals("administrate")){
      addPermission(user, resourceId, resourceType, "view")
      addPermission(user, resourceId, resourceType, "modify")
      addPermission(user, resourceId, resourceType, "administrate")
    }else if(permissionType.equals("modify")){
      addPermission(user, resourceId, resourceType, "view")
      addPermission(user, resourceId, resourceType, "modify")
    }else if(permissionType.equals("view")){
      addPermission(user, resourceId, resourceType, "view")
    }
    else
    	  Logger.error("Unknown permission type")  
  }
  
  def addPermission(user: Identity, resourceId: String, resourceType: String, permissionType: String)  {
    var updateAction: DBObject = null
    if(resourceType.equals("file")){
      if(permissionType.equals("view")){
        updateAction = $addToSet("filesViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        updateAction = $addToSet("filesViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        updateAction = $addToSet("filesAdministrate" -> resourceId)
      }     
    }else if(resourceType.equals("dataset")){
      if(permissionType.equals("view")){
        updateAction = $addToSet("datasetsViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        updateAction = $addToSet("datasetsViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        updateAction = $addToSet("datasetsAdministrate" -> resourceId)
      }
    }else if(resourceType.equals("collection")){
      if(permissionType.equals("view")){
        updateAction = $addToSet("collectionsViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        updateAction = $addToSet("collectionsViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        updateAction = $addToSet("collectionsAdministrate" -> resourceId)
      }
    }
    else{
      Logger.error("Unknown resource type")
      return
    }
    
    UserPermissions.update(MongoDBObject("email" -> user.email.getOrElse(""), "name" -> user.fullName), updateAction, false, false, WriteConcern.Safe)
    
  }
  
  def removePermissionLevel(user: Identity, resourceId: String, resourceType: String, permissionType: String) = {
    if(permissionType.equals("view")){
      removePermission(user, resourceId, resourceType, "view")
      removePermission(user, resourceId, resourceType, "modify")
      removePermission(user, resourceId, resourceType, "administrate")
    }else if(permissionType.equals("modify")){
      removePermission(user, resourceId, resourceType, "administrate")
      removePermission(user, resourceId, resourceType, "modify")
    }else if(permissionType.equals("administrate")){
      removePermission(user, resourceId, resourceType, "administrate")
    }
    else
    	  Logger.error("Unknown permission type")  
  }
  
  def removePermission(user: Identity, resourceId: String, resourceType: String, permissionType: String)  {
    var updateAction: DBObject = null
    if(resourceType.equals("file")){
      if(permissionType.equals("view")){
        updateAction = $pull("filesViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        updateAction = $pull("filesViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        updateAction = $pull("filesAdministrate" -> resourceId)
      }     
    }else if(resourceType.equals("dataset")){
      if(permissionType.equals("view")){
        updateAction = $pull("datasetsViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        updateAction = $pull("datasetsViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        updateAction = $pull("datasetsAdministrate" -> resourceId)
      }
    }else if(resourceType.equals("collection")){
      if(permissionType.equals("view")){
        updateAction = $pull("collectionsViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        updateAction = $pull("collectionsViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        updateAction = $pull("collectionsAdministrate" -> resourceId)
      }
    }
    else{
      Logger.error("Unknown resource type")
      return
    }
    
    UserPermissions.update(MongoDBObject("email" -> user.email.getOrElse(""), "name" -> user.fullName), updateAction, false, false, WriteConcern.Safe)
    
  }
  
}

object UserPermissions extends ModelCompanion[UserPermissions, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[UserPermissions, ObjectId](collection = x.collection("useraccessrights")) {}
  }
}