package services.mongodb

import com.novus.salat.dao.ModelCompanion
import play.api.Play.current
import models.Subscriber
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import services.SubscriberService
import com.mongodb.casbah.commons.MongoDBObject
import services.FacebookService
import com.mongodb.casbah.Imports._
import play.api.Logger
import java.util.Date
import models.UUID
import scala.util.Try

class MongoDBSubscriberService extends SubscriberService {

  def findOneByEmail(email: String): Option[Subscriber] = {
    SubscriberDAO.findOne(MongoDBObject("email" -> email))
  }
  
  def findAllHavingEmail(): List[Subscriber] = {
    SubscriberDAO.find(MongoDBObject("email" -> MongoDBObject("$ne" ->  None))).toList
  }
  
  def findAllHavingFB(): List[Subscriber] = {
    SubscriberDAO.find(MongoDBObject("FBIdentifier" -> MongoDBObject("$ne" ->  None))).toList
  }
  
  def findAll(): List[Subscriber] = {
    SubscriberDAO.findAll.toList
  }
  
  def findOneByIdentifier(identifier: String, translateIdUsername: Boolean = true): Option[Subscriber] = {
    
    var searchList = List(MongoDBObject("FBIdentifier" -> identifier))
        
    if(!(identifier forall Character.isDigit)){
      //Non-numeric, so input must be email or FB username. However, search by associated FB profile ID as well if plugin available to translate and translation is required.
      searchList = searchList :+ MongoDBObject("email" -> identifier)
      if(current.plugin[FacebookService].isDefined && translateIdUsername){
    	searchList = searchList :+ MongoDBObject("FBIdentifier" -> current.plugin[FacebookService].get.getIdByUsername(identifier))
      }
    }
    else{
      //Numeric, so input must be FB profile ID. However, search by associated FB username as well if plugin available to translate and translation is required.
      if(current.plugin[FacebookService].isDefined && translateIdUsername){
    	searchList = searchList :+ MongoDBObject("FBIdentifier" -> current.plugin[FacebookService].get.getUsernameById(identifier))
      } 
    }
    SubscriberDAO.findOne(MongoDBObject("$or" -> searchList))    
  }
  
  def setAuthToken(id: String, token: String, expirationOffset: Int){
    val expirationTime = (expirationOffset * 1000) + System.currentTimeMillis() 
    SubscriberDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("fbAuthToken" -> token, "expirationTime" -> new Date(expirationTime)), false, false, WriteConcern.Safe)
  }
  
  def getAuthToken(identifier: String): Option[String] = {
    
    findOneByIdentifier(identifier, false) match{
      case Some(subscriber) =>{
        subscriber.fbAuthToken
      }
      case None =>{
        Logger.error("Subscriber with identifier "+identifier+" not found.")
        None
      }
      
    }
    
  }

  
  def get(id: String): Option[Subscriber] = {
    SubscriberDAO.findOneById(new ObjectId(id))
  }
  
  def getAllExpiring(): List[Subscriber] = {
    val interval = play.Play.application().configuration().getInt("fb.checkAndRemoveExpired.every") * (24*60*60*1000) * 2 //double the time, for safety
    SubscriberDAO.find("expirationTime" $lt (new Date(System.currentTimeMillis()+interval))).toList
  }
  
  def insert(subscriber: Subscriber) {
    SubscriberDAO.save(subscriber)
  }
  
  def remove(subscriberId: UUID) = Try {
        SubscriberDAO.remove(MongoDBObject("_id" -> new ObjectId(subscriberId.stringify)))
  }
  
}

object SubscriberDAO extends ModelCompanion[Subscriber, ObjectId] {

  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Subscriber, ObjectId](collection = x.collection("subscribers")) {}
  }
}