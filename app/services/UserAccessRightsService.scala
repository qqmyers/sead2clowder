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
}