package services.mongodb

import com.mongodb.casbah.Imports._
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UUID, APIKey}
import services.APIKeyService
import MongoContext.context
import play.api.Play.current
import play.api.Logger

/**
  * Manage API keys by user.
  */
class MongoDBApiKeyService extends APIKeyService {
  /**
    * Create a key.
    *
    * @param name a human readable label
    * @param userId the user to which this key belongs
    * @return the key
    */
  override def createKey(name: String, userId: UUID): String = {
    val generatedKey = UUID.generate().stringify
    val key = APIKey(userId = userId, name = name, key = generatedKey)
    val wr = APIKeys.save(key)
    Logger.debug("Created api key with write result " + wr)
    generatedKey
  }

  /**
    * Get the user this key belongs to.
    *
    * @param key
    * @return the optional user if found
    */
  override def getUserId(key: String): Option[UUID] = {
    APIKeys.findOne(MongoDBObject("key" -> key)).map(k => k.userId)
  }

  /**
    * Delete a key.
    *
    * @param key
    */
  override def deleteKey(key: String) {
    APIKeys.remove(MongoDBObject("key" -> key))
  }

  object APIKeys extends ModelCompanion[APIKey, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[APIKey, ObjectId](collection = x.collection("api.keys")) {}
    }
  }
}
