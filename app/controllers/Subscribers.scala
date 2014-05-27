package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation
import models.Subscriber
import api.WithPermission
import api.Permission
import play.api.Logger
import play.api.Play.current
import play.api.mvc.Flash
import cryptutils.BCrypt
import play.api.mvc.{AnyContent, Request}
import javax.mail.internet.InternetAddress
import javax.mail.internet.AddressException
import services.FacebookService
import play.api.data.validation.Constraint
import play.api.data.validation.Valid
import play.api.data.validation.Invalid
import play.api.data.validation.ValidationError
import com.restfb.exception.FacebookGraphException
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.util.ArrayList
import play.api.data.FormError
import models.FBNotFoundException

object Subscribers extends SecuredController {
  
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

  /**
   * New subscription form.
   */
  val subscriptionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "identifier" -> {current.plugin[FacebookService].isDefined match{
        case true =>{
          text.verifying(Constraint[String] {
     		inputIdentifier: String => {
     		    if(inputIdentifier.equals(""))
     					  Invalid(ValidationError("This field is required"))
     			else{
	     			try{	     					
	     					//If input is not an email address, validate by identifier in general.
	     					new InternetAddress(inputIdentifier).validate()     				
		     				if(!Subscriber.findOneByEmail(inputIdentifier).isDefined)
		     				  Valid
		     				else
		     				  Invalid(ValidationError("Subscription with this email exists already."))     				
			        }catch{ case ex: AddressException => {
				          try{
				        	if(!Subscriber.findOneByIdentifier(inputIdentifier).isDefined)
				        	  Valid
				        	else
				        	  Invalid(ValidationError("Subscription with this identifier exists already."))	
				          }catch{ case exFB: FBNotFoundException => {
	     						Invalid(ValidationError("FB user not found."))
	     					}     				
				          }
			        	}		        
			        }
     			}
     		  } 
          })
        }
        case false =>{
          email.verifying("Subscription with this email exists already.", fields => fields match {
     		case inputEmail => !Subscriber.findOneByEmail(inputEmail).isDefined 
          })
        }
      }},
      "password" -> nonEmptyText
    )
    ((name, surname, identifier, password) => {
      current.plugin[FacebookService].isDefined match{
	        case true =>{
		      try{
		        new InternetAddress(identifier).validate()
		        //If exception not thrown, then user has entered email address
		        Subscriber(name = name, surname = surname, email = Some(identifier), hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()))
		      }catch{ case ex: AddressException => {
		    	  //Identifier is not valid email, so assume Facebook identifier	    	  
	//	    	  //if(!(identifier forall Character.isDigit))
	//	    		  //FB username
		    		  Subscriber(name = name, surname = surname, FBIdentifier = Some(identifier), hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()))
	//	    	  else
	//	    		  //FB ID
	//	    		  Subscriber(name = name, surname = surname, FBIdentifier = Some(identifier), hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()))
		      	}
		      }      
	      }
	        case false =>{
	          Subscriber(name = name, surname = surname, email = Some(identifier), hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()))
	        }  
      }
     }
    )
    ((subscriber: Subscriber) => Some((subscriber.name, subscriber.surname, subscriber.email.getOrElse(subscriber.FBIdentifier.getOrElse("")), "")))
   )
   
   val unsubscriptionForm = Form(
     tuple(
      "identifier" -> nonEmptyText,
      "password" -> nonEmptyText
     )verifying("No subscriber found with this identifier and password.", fields => fields match {
     		case inputIdentifierPassword => validateRemoval(inputIdentifierPassword).isDefined
     	})
  )
  
  def validateRemoval(inputEmailPassword :(String,String)): Option[Subscriber] = {
    
    var subscriberExisting : Option[Subscriber] = None
    try{
		new InternetAddress(inputEmailPassword._1).validate()
		subscriberExisting = Subscriber.findOneByEmail(inputEmailPassword._1)
    }catch{case ex: AddressException => {
      subscriberExisting = Subscriber.findOneByIdentifier(inputEmailPassword._1, false)
    }}
    
    subscriberExisting match{
      case Some(subscriber) => {
        if(BCrypt.checkpw(inputEmailPassword._2, subscriber.hashedPassword)){
        	Some(subscriber)
          }
        else{
          None
        }
      }
      case None => None      
    }    
  }
  
   def subscribe() = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    //in case it is called from admin add subscription screen 
    implicit val user = request.user
    if(current.plugin[FacebookService].isDefined)
    	Ok(views.html.newSubscriber(subscriptionForm, true))
    else
    	Ok(views.html.newSubscriber(subscriptionForm, false))  
   }
   
  def addSubscriber()  = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user 
  	if(current.plugin[FacebookService].isDefined)
    	Ok(views.html.newSubscriber(subscriptionForm, true))
    else
    	Ok(views.html.newSubscriber(subscriptionForm, false)) 
  }
  
  def unsubscribe()  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    if(current.plugin[FacebookService].isDefined)
    	Ok(views.html.removeSubscriber(unsubscriptionForm, true))
    else
    	Ok(views.html.removeSubscriber(unsubscriptionForm, false))
  }
  
  /**
   * Create subscription.
   */
  def submit() = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
        subscriptionForm.bindFromRequest.fold(
          errors => {
	        	  current.plugin[FacebookService].isDefined match{
	        	  	case true =>{
	        	  		BadRequest(views.html.newSubscriber(errors, true))
	        	  	}
	        	  	case false =>{
	        	  		BadRequest(views.html.newSubscriber(errors, false))
	        	  	}
        	  	}
            },
	      subscriber => {
		        Logger.debug("Saving subscription with identifier " + subscriber.email.getOrElse(subscriber.FBIdentifier))
		        		     
			        // TODO create a service instead of calling salat directly
		            Subscriber.save(subscriber)
		            
		            subscriber.email match{
		            	case Some(email) => {
		            	  // redirect to main page if subscribed using email
		            	  Redirect(routes.Application.index)
		            	}
		            	case None => {
		            	  //Redirect to FB oauth page to get user token if subscribed using FB
		            	  val fbAppId = play.Play.application().configuration().getString("fb.appId")
		            	  val hostIp = play.Play.application().configuration().getString("hostIp")
		            	  Redirect("https://www.facebook.com/dialog/oauth?client_id="+fbAppId+"&redirect_uri="+httpProtocol+hostIp+":"+appPort+routes.Subscribers.getAuthToken(subscriber.id.toString)+"&scope=publish_actions")		            	  
		            	}
		        	}		        
			      } 
	)
  }
  
  def getAuthToken(subscriberId: String, code: String) = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    
    Subscriber.get(subscriberId) match{
      case Some(subscriber) => {
        val fbAppId = play.Play.application().configuration().getString("fb.appId")
        val fbAppSecret = play.Play.application().configuration().getString("fb.appSecret")
        val hostIp = play.Play.application().configuration().getString("hostIp")
        val httpclient = new DefaultHttpClient()
        val httpGet = new HttpGet("https://graph.facebook.com/oauth/access_token?client_id="+fbAppId+"&redirect_uri="+httpProtocol+hostIp+":"+appPort+routes.Subscribers.getAuthToken(subscriber.id.toString)+"&client_secret="+fbAppSecret+"&code="+code)
        val tokenRequestResponse = httpclient.execute(httpGet)
        Logger.info(tokenRequestResponse.getStatusLine().toString())
        if(tokenRequestResponse.getStatusLine().getStatusCode().toString.startsWith("4")){
          //subscriber refused to allow access to FB account
          Logger.error("Error authenticating user. User probably refused to allow access to FB account by the feeder app. Removing subscriber.")
          Subscriber.remove(subscriber)
          implicit val user = request.user
     
          BadRequest(views.html.newSubscriber(subscriptionForm.copy(errors = subscriptionForm.errors :+ new FormError("error.key", "Subscriber refused to authenticate with Facebook.")), true))
        }
        else{
        	val tokenResponseString = EntityUtils.toString(tokenRequestResponse.getEntity())
        	Logger.info("Response: "+tokenResponseString)
        	val authToken = tokenResponseString.substring(13, tokenResponseString.indexOf("&"))
        	
        	if(current.plugin[FacebookService].get.getIfUserGrantedPermissions(authToken)){
        	  val expirationTime = tokenResponseString.substring(tokenResponseString.indexOf("&")+9).toInt
        
        	  Subscriber.setAuthToken(subscriberId, authToken, expirationTime)
        	
        	  Redirect(routes.Application.index)
        	}else{ //user has not granted publish permission to the feed app
        	  Logger.error("Error authenticating user. User refused to grant publish permission to the feeder app. Removing subscriber.")
        	  Subscriber.remove(subscriber)
        	  implicit val user = request.user
        	  BadRequest(views.html.newSubscriber(subscriptionForm.copy(errors = subscriptionForm.errors :+ new FormError("error.key", "Subscriber refused to grant publish feeds permission.")), true))
        	}    	
        }
      }
      case None =>{
        Logger.error("Subscriber with id " + subscriberId + " not found. Coludn't set FB authentication token.")
        InternalServerError("Subscriber not found. Coludn't authenticate.")
      }     
    }        
  } 
  
  def removeSubscription() = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
    
        unsubscriptionForm.bindFromRequest.fold(
          errors => {
	        	  current.plugin[FacebookService].isDefined match{
	        	  	case true =>{
	        	  		BadRequest(views.html.removeSubscriber(errors, true))
	        	  	}
	        	  	case false =>{
	        	  		BadRequest(views.html.removeSubscriber(errors, false))
	        	  	}
        	  	}
            },
	      inputIdentifierPassword => {
		        Logger.debug("Deleting subscription with identifier " + inputIdentifierPassword._1)
		        
		        var subscriberExisting : Option[Subscriber] = None
		        try{
		        	new InternetAddress(inputIdentifierPassword._1).validate()
		        	subscriberExisting = Subscriber.findOneByEmail(inputIdentifierPassword._1)
		        }catch{case ex: AddressException => {
		        	subscriberExisting = Subscriber.findOneByIdentifier(inputIdentifierPassword._1, false)
		        }} 
		        
		        subscriberExisting match{
		          case Some(subscriber) => {
		            // TODO create a service instead of calling salat directly
		            Subscriber.remove(subscriber)
		            // redirect to main page
		            Redirect(routes.Application.index)
		          }
		          case None => {
		            Logger.error("Subscriber with identifier " + inputIdentifierPassword._1 + " not found.")
		            Redirect(routes.Application.index)
		          }		          
		        }		        
		} 
	)
  }
  
  def list = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.subscriptions(Subscriber.findAll.toList))
  }
  
  def makeNewsFeed = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.makeNewsFeed(Subscriber.findAllHavingEmail, false))
  }
  
  def makeNewsFeedFB = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.makeNewsFeed(Subscriber.findAllHavingFB, true))
  }
  
   
}