package services

import models.Role
import securesocial.core.IdentityId

/**
 * Manage users. A user is the same as an account.
 *
 * Created by Luigi Marini on 8/26/14.
 */
trait AuthorizationService {

  /**
   * Whether the user account has been enabled by an administrator yet or not.
   * @return true if user has been enabled, false otherwise
   */
  def isUserEnabled(identityId: IdentityId): Boolean

  /**
   * Check if an entry for a particular user already exists, otherwise create one with default values.
   * @param identityId
   * @return
   */
  def userExists(identityId: IdentityId): Boolean

  /**
   * Create default authorization for a user.
   */
  def createDefaultAuthorization(identityId: IdentityId)

  /**
   * Retrieve all roles for a user and a space.
   *
   * @param identityId
   * @param spaceId
   * @return
   */
  def getRoles(identityId: IdentityId, spaceId: String): List[Role]

}
