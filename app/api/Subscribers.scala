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

object Subscribers extends ApiController {

  def submitLoggedIn = SecuredAction(authorization=WithPermission(Permission.Subscribe)) { request =>    
      Logger.debug("Subscribing")
      
      (request.body \ "identifier").asOpt[String].map { email =>
      	SocialUserDAO.findOneByEmail(email) match {
      	  case Some(user) => {
      	    Subscriber.findOneByEmail(email) match {
      	      case None => {
      	        Logger.debug("Saving subscription with email " + email)
      	        // TODO create a service instead of calling salat directly
		        Subscriber.save(Subscriber(name = user.firstName, surname =  user.lastName, email = Some(email), hashedPassword = user.passwordInfo.get.password))
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
	    	  			  wasSent =  sendFeedToSubscriber(subscriberIdentifier, html)	    	  			  
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
  
  
  def sendFeedToSubscriber(subscriberMail : String, html: String): Boolean = {
		  
	  Logger.info("Sending feed to " + subscriberMail)	  
    
      val from = play.Play.application().configuration().getString("smtp.from")
      val host =  play.Play.application().configuration().getString("smtp.host")
      val properties = System.getProperties()
      properties.setProperty("mail.smtp.host", host)
      
      //SSL or regular SMTP
      var port = play.api.Play.configuration.getInt("smtp.port").getOrElse(0)
      if(play.api.Play.configuration.getBoolean("smtp.ssl").getOrElse(false)){
        if(port == 0)
          port = 465
          
        properties.setProperty("mail.smtp.socketFactory.port", port.toString)
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")          
      }
      else{
        if(port == 0)
          port = 25
      }
      properties.setProperty("mail.smtp.port", port.toString)
      
      //Authenticate if needed
      var session = Session.getDefaultInstance(properties)
      val user = play.api.Play.configuration.getString("smtp.user").getOrElse("")      
      if(!user.equals("")){
        properties.setProperty("mail.smtp.auth", "true")
        session = Session.getInstance(properties,
			  new javax.mail.Authenticator() {
				override def getPasswordAuthentication(): PasswordAuthentication = {
					return new PasswordAuthentication(user, play.api.Play.configuration.getString("smtp.password").getOrElse(""))
				}
			  })
      }
      
      try{
        val message = new MimeMessage(session)
        message.setFrom(new InternetAddress(from))
        message.addRecipient(Message.RecipientType.TO,
                                  new InternetAddress(subscriberMail))
        message.setSubject("Medici 2 newsletter feed.")
        message.setContent(html, "text/html")
        Transport.send(message)
                                  
        Logger.info("Sent message successfully.")        
      }catch {
        case msgex: MessagingException =>{
        	Logger.error(msgex.toString())
        	return false
        }  
      }
      return true
  }
  
}