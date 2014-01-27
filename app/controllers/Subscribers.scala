package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation
import models.Subscriber
import api.WithPermission
import api.Permission
import play.api.Logger
import play.api.mvc.Flash
import cryptutils.BCrypt

object Subscribers extends SecuredController {

  /**
   * New subscription form.
   */
  val subscriptionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText
    )
    ((name, surname, email, password) => Subscriber(name = name, surname = surname, email = email, hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())))
    ((subscriber: Subscriber) => Some((subscriber.name, subscriber.surname, subscriber.email, "")))
   )
   
   val unsubscriptionForm = Form(
     tuple(
      "email" -> email,
      "password" -> nonEmptyText
     )verifying("Wrong email and/or password.", fields => fields match {
     		case inputEmailPassword => validateRemoval(inputEmailPassword).isDefined
     	})
  )
  
  def validateRemoval(inputEmailPassword :(String,String)): Option[Subscriber] = {
    
    Subscriber.findOneByEmail(inputEmailPassword._1) match{
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
  
   def subscribe()  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newSubscriber(subscriptionForm)) 
  }
  
  def unsubscribe()  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.removeSubscriber(unsubscriptionForm))
  }
  
  /**
   * Create subscription.
   */
  def submit() = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
    
        subscriptionForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newSubscriber(errors)),
	      subscriber => {
		        Logger.debug("Saving subscription with email " + subscriber.email)
		        		     
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
          errors => BadRequest(views.html.removeSubscriber(errors)),
	      inputEmailPassword => {
		        Logger.debug("Deleting subscription with email " + inputEmailPassword._1)
		        		
		        Subscriber.findOneByEmail(inputEmailPassword._1) match{
		          case Some(subscriber) => {
		            // TODO create a service instead of calling salat directly
		            Subscriber.remove(subscriber)
		            // redirect to main page
		            Redirect(routes.Application.index)
		          }
		          case None => {
		            Logger.error("Subscriber with email " + inputEmailPassword._1 + " not found.")
		            Redirect(routes.Application.index)
		          }		          
		        }		        
		} 
	)
  }
  
  
  
   
}