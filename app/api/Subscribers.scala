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
import services.MailerPlugin

object Subscribers extends ApiController {

  def submitLoggedIn = SecuredAction(authorization=WithPermission(Permission.Subscribe)) { request =>    
      Logger.debug("Subscribing")
      
      (request.body \ "email").asOpt[String].map { email =>
      	SocialUserDAO.findOneByEmail(email) match {
      	  case Some(user) => {
      	    Subscriber.findOneByEmail(email) match {
      	      case None => {
      	        Logger.debug("Saving subscription with email " + email)
      	        // TODO create a service instead of calling salat directly
		        Subscriber.save(Subscriber(name = user.firstName, surname =  user.lastName, email = email, hashedPassword = user.passwordInfo.get.password))
		        Ok(toJson(Map("status" -> "success")))
      	      }
      	      case Some(subscriber) => {
      	        Logger.info("Subscriber already existed.")
      	        Ok(toJson(Map("status" -> "notmodified")))
      	      }
      	    }      	    
      	  }
      	  case None => {
      	    Logger.error("Error getting user with email " + email); InternalServerError
      	  }      	  
      	}      
      }.getOrElse {
        BadRequest(toJson("Missing parameter [email]"))
      }      
  }
  
  def removeSubscriptionLoggedIn = SecuredAction(authorization=WithPermission(Permission.Unsubscribe)) { request =>    
      Logger.debug("Unsubscribing")
      
      (request.body \ "email").asOpt[String].map { email =>
      	    Subscriber.findOneByEmail(email) match {
      	      case Some(subscriber) => {
      	        Logger.debug("Cancelling subscription with email " + email)
      	        // TODO create a service instead of calling salat directly
		        Subscriber.remove(subscriber)
      	        Ok(toJson(Map("status" -> "success")))
      	      }
      	      case None => {
      	    	  Logger.info("Email was not subscribed.")
      	    	  Ok(toJson(Map("status" -> "notmodified")))
      	      }
      	    }      	          
      }.getOrElse {
        BadRequest(toJson("Missing parameter [email]"))
      }      
  }
  
  def sendFeed = SecuredAction(authorization=WithPermission(Permission.Admin)) { request =>    
      
    (request.body \ "feedHTML").asOpt[String] match { 
    	case Some(html) => {
	    	  (request.body \ "subscribers").asOpt[List[String]] match { 
	    	  	case Some(subscribers) => {
	    	  		Logger.info("Sending feed: "+ html)
	    	  	  
	    	  	    var subscribersNotSent = ""
	    	  		for(subscriberMail <- subscribers){
	    	  			var wasSent =  false
	    	  			
	    	  			current.plugin[MailerPlugin].foreach{currentPlugin => {
	    	  			    		 	wasSent = wasSent || currentPlugin.sendMail(subscriberMail, html, "Medici 2 newsletter feed.")}}
	    	  			
	    	  			if(!wasSent)
	    	  				subscribersNotSent = subscribersNotSent + ", " + subscriberMail
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