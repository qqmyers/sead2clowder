package services

import play.api.{ Plugin, Logger, Application }
import com.restfb.FacebookClient
import com.restfb.types.User
import com.restfb.types.FacebookType
import com.restfb.Parameter
import fbutils.LoggedInFacebookClient
import com.restfb.exception.FacebookGraphException
import com.restfb.DefaultFacebookClient
import models.Subscriber
import scala.collection.mutable.ArrayBuffer

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
        		val theUsername = fbObject.getUsername()
        		if(theUsername != null)
        		  theUsername
        		else //user exists but has no username
        		   "0"        		
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
  
  def sendFeedToSubscriberFacebook(subscriberIdentifier : String, html: String, url: String, image: String, name: String, description: String): Boolean = {

		Subscriber.getAuthToken(subscriberIdentifier) match{
		  case Some(authToken) =>{
		    val visibleName = play.Play.application().configuration().getString("fb.visibleName")
		    val visibleLink = play.Play.application().configuration().getString("fb.visibleLink")
		    //val visiblePic = play.Play.application().configuration().getString("fb.visiblePic")
		    val fbClient = new DefaultFacebookClient(authToken)
		    val fbAppId = play.Play.application().configuration().getString("fb.appId")
		    
		    var publishingParams = ArrayBuffer.empty[Parameter]
		    if(!html.equals(""))
		      publishingParams += Parameter.`with`("message", html)
		    if(!url.equals(""))
		      publishingParams += Parameter.`with`("link", url)
		    if(!image.equals(""))
		      publishingParams += Parameter.`with`("picture", image)
		    if(!name.equals(""))
		      publishingParams += Parameter.`with`("name", name)
		    if(!description.equals(""))
		      publishingParams += Parameter.`with`("description", name)
		      
		    
		    try{  //"<a href='"+visibleLink+"'><b>"+visibleName+"</b></a><br /><br />"+
		    	fbClient.publish("me"+"/feed",classOf[FacebookType], publishingParams.toArray:_*)
		    	true
		    }catch{ case ex: Exception => {
		    	Logger.error(ex.toString())
        		Logger.error("Could not send feed to subscriber. Subscriber does not exist on Facebook, or authentication token was invalid.")
        		false
        	}}		    
		  }
		  case None=>{
		    Logger.error("Subscriber or subscriber authentication token not found. Could not send feed to subscriber.")
		    false
		  }		  
		}  	
  }
  
}