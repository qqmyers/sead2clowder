package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import play.api.Play.current
import services.MongoSalatPlugin
import MongoContext.context
import com.mongodb.casbah.Imports._
import services.FacebookService
import play.api.Logger

case class Subscriber (
  id: ObjectId = new ObjectId,
  name: String,
  surname: String,
  email: Option[String] = None,
  FBIdentifier: Option[String] = None,
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
  
  def findOneByIdentifier(identifier: String): Option[Subscriber] = {
    if(!(identifier forall Character.isDigit)){
      //Non-numeric, so input must be email or FB username. However, search by associated FB profile ID as well.
      if(current.plugin[FacebookService].isDefined){
    	dao.findOne(MongoDBObject("$or" -> List(MongoDBObject("email" -> identifier), MongoDBObject("FBIdentifier" -> identifier), MongoDBObject("FBIdentifier" -> current.plugin[FacebookService].get.getIdByUsername(identifier)) )))
      }
      else{
        dao.findOne(MongoDBObject("$or" -> List(MongoDBObject("email" -> identifier), MongoDBObject("FBIdentifier" -> identifier))))
      }
    }
    else{
      //Numeric, so input must be FB profile ID. However, search by associated FB username as well.
      if(current.plugin[FacebookService].isDefined){
    	dao.findOne(MongoDBObject("$or" -> List(MongoDBObject("FBIdentifier" -> identifier), MongoDBObject("FBIdentifier" -> current.plugin[FacebookService].get.getUsernameById(identifier)))))
      }else{
        dao.findOne(MongoDBObject("$or" -> List(MongoDBObject("FBIdentifier" -> identifier))))
      }  
    }
  }
  
}