package api

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import models.SocialUserDAO
import models.Subscriber

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
  
}