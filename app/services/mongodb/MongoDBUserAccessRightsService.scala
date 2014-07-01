package services.mongodb

import javax.inject.{Singleton, Inject}
import services.UserAccessRightsService
import models.UserPermissions
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current



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
  
}

object UserPermissions extends ModelCompanion[UserPermissions, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[UserPermissions, ObjectId](collection = x.collection("useraccessrights")) {}
  }
}