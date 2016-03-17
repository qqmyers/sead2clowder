package services

import models.{UUID, User}

/**
  * Manage API keys by user.
  */
trait APIKeyService {

  /**
    * Create a key.
    * @param name a human readable label
    * @param userId the id of the user to which this key belongs
    * @return the key
    */
  def createKey(name: String, userId: UUID): String

  /**
    * Delete a key.
    * @param key
    */
  def deleteKey(key: String)

  /**
    * Get the user this key belongs to.
    * @param key
    * @return the id of the user if found
    */
  def getUserId(key: String): Option[UUID]

}
