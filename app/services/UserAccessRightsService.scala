package services

import models.UserPermissions

trait UserAccessRightsService {

  /**
   * Initialize user access rights.
   */
  def initRights(userPermissions: UserPermissions): Option[String]
  
}