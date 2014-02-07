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
import play.libs.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import controllers.routes
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils

class FacebookService (application: Application) extends Plugin  {

  var FBClient: Option[FacebookClient] = None
  
  override def onStart() {
    Logger.debug("Starting Facebook Plugin")
	this.FBClient = Some(new LoggedInFacebookClient())
	
	var timeInterval = play.Play.application().configuration().getInt("fb.checkAndRemoveExpired.every")
	    Akka.system().scheduler.schedule(0.days, timeInterval.intValue().days){
	      checkAndRemoveExpired()
	    }
	
  }
  
  override def onStop() {
    Logger.debug("Shutting down Facebook Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("facebookservice").filter(_ == "disabled").isDefined
  }
  
  def checkAndRemoveExpired(){
    val appName = play.Play.application().configuration().getString("fb.visibleName")
    val resubscribeAnnouncement= "Your " + appName + " subscription has expired. Go to the following link to resubscribe."
    val url= "http://"+play.Play.application().configuration().getString("hostIp").replaceAll("/$", "")+":"+play.Play.application().configuration().getString("http.port")+routes.Subscribers.subscribe.url
    val name = "Subscribe"
    val thisPlugin = this 
    
    for(subscriber <- Subscriber.getAllExpiring){
      this.sendFeedToSubscriberFacebook(subscriber.FBIdentifier.get,resubscribeAnnouncement,url,"",name,"")
      Subscriber.remove(subscriber)
    }
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
  
  def getIfUserGrantedPermissions(authToken : String): Boolean = {
    
    val fbClient = new DefaultFacebookClient(authToken)
    		try{ 
    			val httpclient = new DefaultHttpClient()
    			val httpGet = new HttpGet("https://graph.facebook.com/me/permissions?access_token="+authToken)
    			val ermissionsRequestResponse = httpclient.execute(httpGet)   
    			val responseJSON = play.api.libs.json.Json.parse(EntityUtils.toString(ermissionsRequestResponse.getEntity()))
    			(responseJSON \ "data" \ "publish_stream").asOpt[String].map{ permission =>
    			  permission match{
    			    case "1" => true
    			    case _ => false
    			  }
    			}.getOrElse {
    				false
    			} 
		    }catch{ case ex: Exception => {
		    	Logger.error(ex.toString())
        		Logger.error("Could not get permissions. Assuming failed authentication.")
        		false
        	}}    
  }
  
}