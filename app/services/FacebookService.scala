package services

import play.api.{ Plugin, Logger, Application }
import com.restfb.FacebookClient
import com.restfb.types.User
import fbutils.LoggedInFacebookClient

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
      case Some(fbClient) => fbClient.fetchObject(id, classOf[User]).getUsername()
      case None => {
        Logger.warn("Could not get user's username by id.")
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
        Logger.warn("Could not get user's id by username.")
        "0"
      }
    }
  }
  
}