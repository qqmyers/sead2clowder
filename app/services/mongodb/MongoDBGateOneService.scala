package services.mongodb

import javax.inject.{Singleton}
import services.GateOneService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{GateOneMachine, GateOneUserOnMachine}
import org.bson.types.ObjectId
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject
import MongoContext.context
import securesocial.core.Identity

@Singleton
class MongoDBGateOneService extends GateOneService {

  def insertMachine(machine: GateOneMachine): Option[String] = {
    GateOneMachineDAO.insert(machine).map(_.toString)    
  }
  def updateMachine(machine: GateOneMachine) {
    GateOneMachineDAO.save(machine)
  }
  
  def getMachine(apiKey: String): Option[GateOneMachine] = {
    GateOneMachineDAO.findOne(MongoDBObject("apiKey" -> apiKey))
  }
  
  
  def insertUserOnMachine(user: GateOneUserOnMachine): Option[String] = {
    GateOneUserDAO.insert(user).map(_.toString)    
  }
  def updateUserOnMachine(user: GateOneUserOnMachine) {
    GateOneUserDAO.save(user)
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