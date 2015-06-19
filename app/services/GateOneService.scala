package services

import models.{GateOneMachine, GateOneUserOnMachine}
import securesocial.core.Identity

trait GateOneService {

  def insertMachine(machine: GateOneMachine): Option[String]
  
  def updateMachine(machine: GateOneMachine)
  
  def getMachine(apiKey: String): Option[GateOneMachine]
  
  def insertUserOnMachine(user: GateOneUserOnMachine): Option[String]
  
  def updateUserOnMachine(user: GateOneUserOnMachine)
  
  def getUserMachines(userEmail: String): List[GateOneUserOnMachine]
  
  def checkUserOnMachine(userEmail: String, apiKey: String, accessUsername: String): Boolean
  
}