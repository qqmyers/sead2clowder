package services

import models.{GateOneMachine, GateOneUserOnMachine}
import securesocial.core.Identity

trait GateOneService {

  def insertMachine(machine: GateOneMachine): Option[String]
  
  def updateMachineSecret(apiKey: String, secret: String)
  
  def getMachine(apiKey: String): Option[GateOneMachine]
  
  def removeMachine(apiKey: String)
  
  def removeUsersOfMachine(apiKey: String)
  
  def insertUserOnMachine(user: GateOneUserOnMachine): Option[String]
  
  def removeUserFromMachine(userEmail: String, apiKey: String, accessUsername: String)
  
  def getUserMachines(userEmail: String): List[GateOneUserOnMachine]
  
  def checkUserOnMachine(userEmail: String, apiKey: String, accessUsername: String): Boolean
  
}