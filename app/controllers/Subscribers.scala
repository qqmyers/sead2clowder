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

object Subscribers extends SecuredController {

  /**
   * New subscription form.
   */
  val subscriptionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "identifier" -> {current.plugin[FacebookService].isDefined match{
        case true =>{
          nonEmptyText.verifying(Constraint[String] {
     		inputIdentifier: String => {
     			try{//If input is not an email address, validate by identifier in general.
     					new InternetAddress(inputIdentifier).validate()     				
	     				if(!Subscriber.findOneByEmail(inputIdentifier).isDefined)
	     				  Valid
	     				else
	     				  Invalid(ValidationError("Subscription with this identifier exists already."))     				
		        }catch{ case ex: AddressException => {
			          try{
			        	if(!Subscriber.findOneByIdentifier(inputIdentifier).isDefined)
			        	  Valid
			        	else
			        	  Invalid(ValidationError("Subscription with this email exists already."))	
			          }catch{ case exFB: FacebookGraphException => {
     						Invalid(ValidationError("FB user not found."))
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
     )verifying("Wrong identifier and/or password.", fields => fields match {
     		case inputIdentifierPassword => validateRemoval(inputIdentifierPassword).isDefined
     	})
  )
  
  def validateRemoval(inputEmailPassword :(String,String)): Option[Subscriber] = {
    
    Subscriber.findOneByIdentifier(inputEmailPassword._1) match{
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
		            // redirect to main page
		            Redirect(routes.Application.index)
			      } 
	)
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
		        		
		        Subscriber.findOneByIdentifier(inputIdentifierPassword._1) match{
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
    Ok(views.html.makeNewsFeed(Subscriber.findAll.toList))
  }
  
   
}