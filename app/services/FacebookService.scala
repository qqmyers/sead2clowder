package services

import play.api.{ Plugin, Logger, Application }
import com.restfb.FacebookClient
import com.restfb.types.User
import com.restfb.types.FacebookType
import com.restfb.Parameter
import fbutils.LoggedInFacebookClient
import com.restfb.exception.FacebookGraphException

class FacebookService (application: Application) extends Plugin  {

  var FBClient: Option[FacebookClient] = None
  
  override def onStart() {
    Logger.debug("Starting Facebook Plugin")
	this.FBClient = Some(new LoggedInFacebookClient())
  }
  
  override def onStop() {
    Logger.debug("Shutting down Facebook Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("facebookservice").filter(_ == "disabled").isDefined
  }
  
  def getUsernameById(id: String): String = {
    FBClient match{
      case Some(fbClient) => {        
        	val fbObject = fbClient.fetchObject(id, classOf[User])
        	//exception thrown from fetchObject if user does not exist
        	try{
        		fbObject.getUsername()
        	}catch{ case ex: FacebookGraphException => {
        		//user exists but has no username
        		"0"
        	}}
      }
      case None => {
        Logger.warn("Could not get user's username by id. No active Facebook client.")
        "0"
      }
    }
  }
  def getIdByUsername(username: String): String = {    
    FBClient match{
      case Some(fbClient) => {
        fbClient.fetchObject(username, classOf[User]).getId()
      }
      case None => {
        Logger.warn("Could not get user's id by username. No active Facebook client.")
        "0"
      }
    }
  }
  
//  def sendFeedToSubscriberFacebook(subscriberIdentifier : String, html: String): Boolean = {
//    FBClient match{
//      case Some(fbClient) => {
//        fbClient.publish(subscriberIdentifier+"/feed", classOf[FacebookType], Parameter.`with`("message", html))
//        true
//      }
//      case None => {
//        Logger.warn("Could not publish feed to Facebook subscriber. No active Facebook client.")
//        false
//      }
//    }
//  }
  
}