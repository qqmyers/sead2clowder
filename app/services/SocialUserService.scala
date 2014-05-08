package services

import securesocial.core.Identity

trait SocialUserService {

  def findOneByEmail(email: String): Option[Identity]
  
  def findOneByIdentifier(identifier: String): Option[Identity]
  
}