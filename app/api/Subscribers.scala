package api

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.Play.current
import models.SocialUserDAO
import models.Subscriber
import javax.mail._
import javax.mail.internet._
import javax.activation._
import services.FacebookService
import services.MailerPlugin

object Subscribers extends ApiController {
  
  var appPort = play.api.Play.configuration.getString("https.port").getOrElse("")
  val httpProtocol = {
					if(!appPort.equals("")){
						"https://"
					}
					else{
						appPort = play.api.Play.configuration.getString("http.port").getOrElse("")
						"http://"
					}
		}

  def submitLoggedIn = SecuredAction(authorization=WithPermission(Permission.Subscribe)) { request =>    
      Logger.debug("Subscribing")
      
      (request.body \ "identifier").asOpt[String].map { identifier =>
      	SocialUserDAO.findOneByIdentifier(identifier) match {
      	  case Some(user) => {
      	    var subscriberExisting : Option[Subscriber] = None
      	    var isEmail = true
        	try{
		        	new InternetAddress(identifier).validate()
		        	subscriberExisting = Subscriber.findOneByEmail(identifier)
		        }catch{case ex: AddressException => {
		        	subscriberExisting = Subscriber.findOneByIdentifier(identifier, false)
		        	isEmail = false
		    }} 
      	    
      	    subscriberExisting match {
      	      case None => {
      	        if(isEmail){
	      	        Logger.debug("Saving subscription with email " + identifier)
	      	        // TODO create a service instead of calling salat directly
			        Subscriber.save(Subscriber(name = user.firstName, surname =  user.lastName, email = Some(identifier), hashedPassword = user.passwordInfo.get.password))
			        Ok("success")
		        }
      	        else{
      	        	Logger.debug("Saving subscription with FB identifier " + identifier)
      	        	val newSubscriber = Subscriber(name = user.firstName, surname =  user.lastName, FBIdentifier = Some(identifier), hashedPassword = "")
	      	        // TODO create a service instead of calling salat directly
			        Subscriber.save(newSubscriber)
			        
			        //Redirect to FB oauth page to get user token if subscribed using FB
			        val fbAppId = play.Play.application().configuration().getString("fb.appId")
			        val hostIp = play.Play.application().configuration().getString("hostIp")
			        
			        Ok("https://www.facebook.com/dialog/oauth?client_id="+fbAppId+"&redirect_uri="+httpProtocol+hostIp+":"+appPort+controllers.routes.Subscribers.getAuthToken(newSubscriber.id.toString)+"&scope=publish_stream")
      	        }		        		        
      	      }
      	      case Some(subscriber) => {
      	        Logger.info("Subscriber already existed.")
      	        Ok("notmodified")
      	      }
      	    }
      	  }
      	  case None => {
      	    Logger.error("Error getting user with identifier " + identifier); InternalServerError
      	  }      	  
      	}      
      }.getOrElse {
        BadRequest(toJson("Missing parameter [identifier]"))
      }      
  }
  
  def removeSubscriptionLoggedIn = SecuredAction(authorization=WithPermission(Permission.Unsubscribe)) { request =>    
      Logger.debug("Unsubscribing")
      
      (request.body \ "identifier").asOpt[String].map { identifier =>
        	var subscriberExisting : Option[Subscriber] = None
        	try{
		        	new InternetAddress(identifier).validate()
		        	subscriberExisting = Subscriber.findOneByEmail(identifier)
		        }catch{case ex: AddressException => {
		        	subscriberExisting = Subscriber.findOneByIdentifier(identifier, false)
		    }} 
        
      	    subscriberExisting match {
      	      case Some(subscriber) => {
      	        Logger.debug("Cancelling subscription with identifier " + identifier)
      	        // TODO create a service instead of calling salat directly
		        Subscriber.remove(subscriber)
      	        Ok(toJson(Map("status" -> "success")))
      	      }
      	      case None => {
      	    	  Logger.info("Identified person was not subscribed.")
      	    	  Ok(toJson(Map("status" -> "notmodified")))
      	      }
      	    }      	          
      }.getOrElse {
        BadRequest(toJson("Missing parameter [identifier]"))
      }      
  }
  
  def sendFeed = SecuredAction(authorization=WithPermission(Permission.Admin)) { request =>    
      
    (request.body \ "feedHTML").asOpt[String] match { 
    	case Some(html) => {
	    	  (request.body \ "subscribers").asOpt[List[String]] match { 
	    	  	case Some(subscribers) => {
	    	  		Logger.info("Sending feed: "+ html)
	    	  	  
	    	  	    var subscribersNotSent = ""
	    	  		for(subscriberIdentifier <- subscribers){
	    	  			var wasSent = false
	    	  			try{//If input is not an email address, try sending to Facebook.
	    	  			  new InternetAddress(subscriberIdentifier).validate() 

	    	  			  current.plugin[MailerPlugin].foreach{currentPlugin => {
	    	  			    		 	wasSent = wasSent || currentPlugin.sendMail(subscriberIdentifier, html, "Medici 2 newsletter feed.")}}
	    	  			  
	    	  			}catch{ case ex: AddressException => {
	    	  					val url = (request.body \ "url").asOpt[String].getOrElse("")
	    	  					val image = (request.body \ "image").asOpt[String].getOrElse("")
	    	  					val name = (request.body \ "name").asOpt[String].getOrElse("")
	    	  					val description = (request.body \ "description").asOpt[String].getOrElse("")
	    	  			  
	    	  			    	 current.plugin[FacebookService].foreach{currentPlugin => {
	    	  			    		 	wasSent = wasSent || currentPlugin.sendFeedToSubscriberFacebook(subscriberIdentifier,html,url,image,name,description)}}
	    	  			  	}
	    	  			}		
	    	  			if(!wasSent)
	    	  				subscribersNotSent = subscribersNotSent + ", " + subscriberIdentifier
	    	  		}
	    	  		if(subscribersNotSent.equals("")){
	    	  		  Ok("Feed posted successfully.")
	    	  		}
	    	  		else{
	    	  		  Ok("Feed was posted to all but the following, for which posting failed: " + subscribersNotSent.substring(2))
	    	  		}
	    	  	}
	    	  	case None =>{
	    	  		Logger.error("Missing parameter [subscribers].")
	    	  		BadRequest(toJson("Missing parameter [subscribers]"))
		    	} 
	    	  }
    	}
    	case None =>{
    	  Logger.error("Missing parameter [feedHTML].")
    	  BadRequest(toJson("Missing parameter [feedHTML]"))
    	} 
    }
  }

}  
