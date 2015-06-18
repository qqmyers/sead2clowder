package services

import play.api.{Plugin, Logger, Application}
import play.api.Play.current
import models.{GateOneMachine, GateOneUserOnMachine}
import scala.util.Try
import com.roundeights.hasher.{Hasher, Algo}

class GateOnePlugin(application: Application) extends Plugin {
  
  val gateOne: GateOneService = DI.injector.getInstance(classOf[GateOneService])
  
  var gateOneServer: String = ""

  override def onStart() {
    this.gateOneServer = play.api.Play.configuration.getString("gateone.server").getOrElse("http://localhost")
    Logger.debug("Gate One Plugin started")
  }
  
   override def onStop() {
    Logger.debug("Shutting down Gate One Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("gateoneplugin").filter(_ == "disabled").isDefined
  }
  
  def addMachine(apiKey: String, secret: String) = {
    gateOne.insertMachine(GateOneMachine(apiKey=apiKey, secret=secret))
  }
  
  def addUserOnMachine(userEmail: String, apiKey: String, accessUsername: String) = {
    gateOne.insertUserOnMachine(GateOneUserOnMachine(apiKey=apiKey, userEmail=userEmail, accessUsername=accessUsername))
  }
  
  def getUserMachines(email:String) = {
    gateOne.getUserMachines(email)
  }
  
  def getAuth(apiKey: String, accessUsername: String): Option[(String, String, String)] = {
    val machine = gateOne.getMachine(apiKey)
    Try{
      if(!machine.isDefined){
	      Logger.error("Machine with apiKey "+apiKey+" not found")
	      throw new AssertionError
      }
      val secret = machine.get.secret
      val upn = accessUsername + "@" + apiKey
      val timestamp = System.currentTimeMillis
      val signature = Algo.hmac(secret).sha1(apiKey+upn+timestamp).hex
      
      return Some((upn,timestamp.toString,signature))
    }.toOption
  }
  
}