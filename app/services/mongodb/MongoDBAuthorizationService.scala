package services.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{Role, Authorization}
import org.bson.types.ObjectId
import play.api.Logger
import play.api.Play._
import securesocial.core.{Identity, IdentityId}
import services.AuthorizationService
import MongoContext.context

/**
 * Created by Luigi Marini on 8/26/14.
 */
class MongoDBAuthorizationService extends AuthorizationService {

  /**
   * Whether the user account has been enabled by an administrator yet or not.
   * @return true if user has been enabled, false otherwise
   */
  def isUserEnabled(identityId: IdentityId): Boolean = {
    AuthorizationDAO.findOne(MongoDBObject("identityId.userId" -> identityId.userId,
      "identityId.providerId" -> identityId.providerId)) match {
      case Some(authorization) => {
        if (authorization.status == "enabled") {
          Logger.debug("User is enabled")
          true
        } else {
          Logger.debug("User is not enabled")
          false
        }
      }
      case None => {
        Logger.debug("User is not enabled")
        false
      }
    }
  }

  /**
   * Check if an entry for a particular user already exists, otherwise create one with default values.
   * @param identityId
   * @return
   */
  def userExists(identityId: IdentityId): Boolean = {
    AuthorizationDAO.findOne(MongoDBObject("identityId.userId" -> identityId.userId,
      "identityId.providerId" -> identityId.providerId)) match {
      case Some(authorization) => {
        Logger.debug("User authorization entry exists")
        true
      }
      case None => {
        Logger.debug("User authorization entry does not exists")
        false
      }
    }
  }

  /**
   * Create default authorization for a user.
   */
  def createDefaultAuthorization(identityId: IdentityId) {
    Logger.debug("Creating default authorization entry for user")
    AuthorizationDAO.save(Authorization(identityId = identityId))
  }

  /**
   * Retrieve all roles for a user and a space.
   *
   * @param identityId
   * @param spaceId
   * @return
   */
  def getRoles(identityId: IdentityId, spaceId: String): List[Role] = {
    List.empty[Role]
  }

  /**
   * Retrieve all users with their authentication as a list of tuples.
   *
   * @return list of tuples with user identity and authorization object
   */
  def listUsersWithRoles(): List[(Identity, Authorization)] = {
    for {
      identity <- SocialUserDAO.find(MongoDBObject()).toList;
      authorization <- AuthorizationDAO.findOne(MongoDBObject("identityId.userId" -> identity.identityId.userId,
        "identityId.providerId" -> identity.identityId.providerId))
    } yield (identity, authorization)
  }

}

object AuthorizationDAO extends ModelCompanion[Authorization, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Authorization, ObjectId](collection = x.collection("authorization")) {}
  }
}
