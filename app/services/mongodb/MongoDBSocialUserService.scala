package services.mongodb

import services.SocialUserService
import securesocial.core.Identity
import com.novus.salat.dao.ModelCompanion
import play.api.Play.current
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject


class MongoDBSocialUserService extends SocialUserService {
  
  def findOneByEmail(email: String): Option[Identity] = {
    SocialUserDAO.findOne(MongoDBObject("email" -> email))
  }
  
  def findOneByIdentifier(identifier: String): Option[Identity] = {
    SocialUserDAO.findOne(MongoDBObject("identityId.userId" -> identifier))
  }
  
}
