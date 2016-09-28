package services.mongodb

import java.util.Date

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{Provider, User}
import org.bson.types.ObjectId
import play.api.Application
import play.api.Play._
import securesocial.core._
import securesocial.core.providers.{Token, UsernamePasswordProvider}
import services.{AppConfiguration, DI, UserService}
import services.ss.ORCIDProvider
import services.mongodb.MongoContext.context

/**
  * Used to store the users created by secure social. This is only used for the login. All code in clowder will
  * use the user as specified by models.User
  */
class MongoDBSecureSocialUserService(application: Application) extends UserServicePlugin(application) {
  lazy val users: UserService = DI.injector.getInstance(classOf[UserService])

  override def find(id: IdentityId): Option[SocialUser] = {
    // Convert userpass to lowercase so emails aren't case sensitive
    if (id.providerId == "userpass")
      UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> id.userId.toLowerCase, "identityId.providerId" -> id.providerId))
    else
      UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> id.userId, "identityId.providerId" -> id.providerId))
  }

  override def findByEmailAndProvider(email: String, providerId: String): Option[SocialUser] = {
    if (providerId == "userpass")
      UserDAO.dao.findOne(MongoDBObject("email" -> email.toLowerCase, "identityId.providerId" -> providerId))
    else
      UserDAO.dao.findOne(MongoDBObject("email" -> email, "identityId.providerId" -> providerId))
  }

  override def save(user: Identity): SocialUser = {
    // user is always of type SocialUser when this function is entered
    // first convert the socialuser object to a mongodbobject
    val userobj = com.novus.salat.grater[Identity].asDBObject(user)

    // replace email with forced lowercase for userpass provider
    if (user.identityId.providerId == "userpass") {
      userobj.put("email", user.email.map(_.toLowerCase))
      val identobj = MongoDBObject("userId" -> user.identityId.userId.toLowerCase(),
        "providerId" -> user.identityId.providerId)
      userobj.put("identityId", identobj)
    }

    // query to find the user based on identityId
    val query = MongoDBObject("identityId.userId" -> user.identityId.userId, "identityId.providerId" -> user.identityId.providerId)

    // update all fields from past in user object
    val dbobj = MongoDBObject("$set" -> userobj)

    // update, if it does not exist do an insert (upsert = true)
    UserDAO.update(query, dbobj, upsert = true, multi = false, WriteConcern.Safe)

    // return the user object
    find(user.identityId).get
  }

  // ----------------------------------------------------------------------
  // Code to deal with tokens
  // ----------------------------------------------------------------------
  override def deleteToken(uuid: String): Unit = {
    TokenDAO.remove(MongoDBObject("uuid" -> uuid))
  }

  override def save(token: Token): Unit = {
    TokenDAO.save(token)
  }

  override def deleteExpiredTokens(): Unit = {
    TokenDAO.remove("expirationTime" $lt new Date)
    val invites = SpaceInviteDAO.find("expirationTime" $lt new Date)
    for(inv <- invites) {
      ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(inv.space.stringify)),
        $pull("invitations" -> MongoDBObject( "_id" -> new ObjectId(inv.id.stringify))), upsert=false, multi=false, WriteConcern.Safe)
    }
    SpaceInviteDAO.remove("expirationTime" $lt new Date)
  }

  override def findToken(token: String): Option[Token] = {
    TokenDAO.findOne(MongoDBObject("uuid" -> token))
  }

  object TokenDAO extends ModelCompanion[Token, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Token, ObjectId](collection = x.collection("social.token")) {}
    }
  }

  object UserDAO extends ModelCompanion[SocialUser, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[SocialUser, ObjectId](collection = x.collection("social.users")) {}
    }
  }
}
