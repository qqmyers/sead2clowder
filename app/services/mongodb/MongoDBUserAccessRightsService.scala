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
  
  def setPermissionLevel(user: Identity, resourceId: String, resourceType: String, permissionType: String)  {
    if(permissionType.equals("view")){
      removePermissionLevel(user, resourceId, resourceType, "modify")
      addPermissionLevel(user, resourceId, resourceType, "view")
    }else if(permissionType.equals("modify")){
      removePermissionLevel(user, resourceId, resourceType, "administrate")
      addPermissionLevel(user, resourceId, resourceType, "modify")
    }else if(permissionType.equals("administrate")){
      addPermissionLevel(user, resourceId, resourceType, "administrate")
    }else if(permissionType.equals("noaccess")){
      removePermissionLevel(user, resourceId, resourceType, "view")
    }
    else
    	  Logger.error("Unknown permission level")  
  }
  
  def removeResourceRightsForAll(resourceId: String, resourceType: String) {
    
    Logger.debug("Removing rights for "+resourceId)
        
    if(resourceType.equals("file")){
	     UserPermissions.update(MongoDBObject(), $pull("filesViewOnly" -> resourceId), false, true, WriteConcern.Safe)
	     UserPermissions.update(MongoDBObject(), $pull("filesViewModify" -> resourceId), false, true, WriteConcern.Safe)
	     UserPermissions.update(MongoDBObject(), $pull("filesAdministrate" -> resourceId), false, true, WriteConcern.Safe)
    }else if(resourceType.equals("dataset")){
    	UserPermissions.update(MongoDBObject(), $pull("datasetsViewOnly" -> resourceId), false, true, WriteConcern.Safe)
    	UserPermissions.update(MongoDBObject(), $pull("datasetsViewModify" -> resourceId), false, true, WriteConcern.Safe)
    	UserPermissions.update(MongoDBObject(), $pull("datasetsAdministrate" -> resourceId), false, true, WriteConcern.Safe)
    }else if(resourceType.equals("collection")){
    	UserPermissions.update(MongoDBObject(), $pull("collectionsViewOnly" -> resourceId), false, true, WriteConcern.Safe)
    	UserPermissions.update(MongoDBObject(), $pull("collectionsViewModify" -> resourceId), false, true, WriteConcern.Safe)
    	UserPermissions.update(MongoDBObject(), $pull("collectionsAdministrate" -> resourceId), false, true, WriteConcern.Safe)
    }
    else{
      Logger.error("Unknown resource type")
      return
    }
    
  }
  
  def getAllRightsForResource(resourceId: String, resourceType: String): List[(String,String,String)] = {
    
    var allUsers: List[(String,String,String)] = List.empty 
    
    val usersWithAdminRights = UserPermissions.find(MongoDBObject((resourceType+"sAdministrate") -> resourceId)).toList
    for(singleUser <- usersWithAdminRights){
      allUsers = allUsers :+ (singleUser.name, singleUser.email, "administrate")
    }
    val usersWithModifyRights = UserPermissions.find(MongoDBObject((resourceType+"sViewModify") -> resourceId)).toList
    for(singleUser <- usersWithModifyRights){
      allUsers = allUsers :+ (singleUser.name, singleUser.email, "modify")
    }
    val usersWithViewRights = UserPermissions.find(MongoDBObject((resourceType+"sViewOnly") -> resourceId)).toList
    for(singleUser <- usersWithViewRights){
      allUsers = allUsers :+ (singleUser.name, singleUser.email, "view")
    }
    
    var allUsersNoDuplicates: List[(String,String,String)] = List.empty
    var previousUser = ("","","")
    for(singleUser <- allUsers.sorted){
      if(!(singleUser._1.equals(previousUser._1) && singleUser._2.equals(previousUser._2))){
        allUsersNoDuplicates = allUsersNoDuplicates :+ singleUser
        previousUser = singleUser
      }
    }
    
    allUsersNoDuplicates
    
  }
  
  
  def checkForPermission(user: Identity, resourceId: String, resourceType: String, permissionType: String): Boolean = {
    var searchList = List(MongoDBObject("email" -> user.email.getOrElse("")))
    searchList = searchList :+ MongoDBObject("name" -> user.fullName)
    if(resourceType.equals("file")){
      if(permissionType.equals("view")){
        searchList = searchList :+ MongoDBObject("filesViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        searchList = searchList :+ MongoDBObject("filesViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        searchList = searchList :+ MongoDBObject("filesAdministrate" -> resourceId)
      }else{
    	  Logger.error("Unknown permission type")
    	  throw new Exception("Unknown permission type")
      }     
    }else if(resourceType.equals("dataset")){
      if(permissionType.equals("view")){
        searchList = searchList :+ MongoDBObject("datasetsViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        searchList = searchList :+ MongoDBObject("datasetsViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        searchList = searchList :+ MongoDBObject("datasetsAdministrate" -> resourceId)
      }else{
    	  Logger.error("Unknown permission type")
    	  throw new Exception("Unknown permission type")
      } 
    }else if(resourceType.equals("collection")){
      if(permissionType.equals("view")){
        searchList = searchList :+ MongoDBObject("collectionsViewOnly" -> resourceId)
      }else if(permissionType.equals("modify")){
        searchList = searchList :+ MongoDBObject("collectionsViewModify" -> resourceId)
      }else if(permissionType.equals("administrate")){
        searchList = searchList :+ MongoDBObject("collectionsAdministrate" -> resourceId)
      }else{
    	  Logger.error("Unknown permission type")
    	  throw new Exception("Unknown permission type")
      } 
    }
    else{
      Logger.error("Unknown resource type")
      throw new Exception("Unknown resource type")
    }
    
    UserPermissions.findOne(MongoDBObject("$and" -> searchList)).isDefined
  }
  
   
  def findByEmailAndFullName(email: String, fullName: String): Option[Identity] = {
    Logger.trace("Searching for user " + email + " " + fullName)
    SocialUserDAO.findOne(MongoDBObject("email"->email, "fullName"->fullName))
  }
  
  
}

object UserPermissions extends ModelCompanion[UserPermissions, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[UserPermissions, ObjectId](collection = x.collection("useraccessrights")) {}
  }
}