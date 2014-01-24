package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import play.api.Play.current
import services.MongoSalatPlugin
import MongoContext.context
import com.mongodb.casbah.Imports._

case class Subscriber (
  id: ObjectId = new ObjectId,
  name: String,
  surname: String,
  email: String,
  hashedPassword: String
)


object Subscriber extends ModelCompanion[Subscriber, ObjectId] {

  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Subscriber, ObjectId](collection = x.collection("subscribers")) {}
  }
  
  def findOneByEmail(email: String): Option[Subscriber] = {
    dao.findOne(MongoDBObject("email" -> email))
  }
  
}