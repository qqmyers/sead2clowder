package services

import models.UserPermissions
import securesocial.core.Identity


trait UserAccessRightsService {
  
  /**
   * Initialize user access rights.
   */
  def initRights(userPermissions: UserPermissions): Option[String]
  
  def get(user: Identity): Option[UserPermissions]
  
  def addPermissionLevel(user: Identity, resourceId: String, resourceType: String, permissionType: String)
  
  def removePermissionLevel(user: Identity, resourceId: String, resourceType: String, permissionType: String)
  
  def setPermissionLevel(user: Identity, resourceId: String, resourceType: String, permissionType: String)
  
  def checkForPermission(user: Identity, resourceId: String, resourceType: String, permissionType: String): Boolean
  
  def removeResourceRightsForAll(resourceId: String, resourceType: String)
  
  def findByEmailAndFullName(email: String, fullName: String): Option[Identity]
  
  def getAllRightsForResource(resourceId: String, resourceType: String): List[(String,String,String)]
}