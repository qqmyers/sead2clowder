package services.mongodb

import javax.inject.{Singleton}
import services.GateOneService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{GateOneMachine, GateOneUserOnMachine}
import org.bson.types.ObjectId
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import MongoContext.context
import securesocial.core.Identity

@Singleton
class MongoDBGateOneService extends GateOneService {

  def insertMachine(machine: GateOneMachine): Option[String] = {
    GateOneMachineDAO.insert(machine).map(_.toString)    
  }
  
  def updateMachineSecret(apiKey: String, secret: String) { 
    GateOneMachineDAO.dao.collection.update(MongoDBObject("apiKey" -> apiKey),
      $set("secret" -> secret), false, false, WriteConcern.Safe)
  }
  
  def getMachine(apiKey: String): Option[GateOneMachine] = {
    GateOneMachineDAO.findOne(MongoDBObject("apiKey" -> apiKey))
  }
  
  def removeMachine(apiKey: String) {
    GateOneMachineDAO.remove(MongoDBObject("apiKey" -> apiKey))
  }
  
  def removeUsersOfMachine(apiKey: String){
    GateOneUserDAO.remove(MongoDBObject("apiKey" -> apiKey))
  }
  
  
  def insertUserOnMachine(user: GateOneUserOnMachine): Option[String] = {
    GateOneUserDAO.insert(user).map(_.toString)    
  }
  
  def removeUserFromMachine(userEmail: String, apiKey: String, accessUsername: String) {
    GateOneUserDAO.remove(MongoDBObject("userEmail" -> userEmail, "apiKey" -> apiKey, "accessUsername" -> accessUsername))
  }
  
  def getUserMachines(userEmail: String): List[GateOneUserOnMachine] = {
    (for (userMachine <- GateOneUserDAO.find(MongoDBObject("userEmail" -> userEmail) )) yield userMachine).toList
  }
  
  def checkUserOnMachine(userEmail: String, apiKey: String, accessUsername: String): Boolean = {
    GateOneUserDAO.findOne(MongoDBObject("userEmail" -> userEmail, "apiKey" -> apiKey, "accessUsername" -> accessUsername)).isDefined
  }
  
}

object GateOneMachineDAO extends ModelCompanion[GateOneMachine, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[GateOneMachine, ObjectId](collection = x.collection("gateonemachines")) {}
  }
}
object GateOneUserDAO extends ModelCompanion[GateOneUserOnMachine, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[GateOneUserOnMachine, ObjectId](collection = x.collection("gateoneusersonmachines")) {}
  }
}