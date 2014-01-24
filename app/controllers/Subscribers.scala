package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation
import models.Subscriber
import api.WithPermission
import api.Permission
import play.api.Logger
import play.api.mvc.Flash

object Subscribers extends SecuredController {

  /**
   * New subscription form.
   */
  val subscriptionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "email" -> email
    )
    ((name, surname, email) => Subscriber(name = name, surname = surname, email = email))
    ((subscriber: Subscriber) => Some((subscriber.name, subscriber.surname, subscriber.email)))
   )
   
   val unsubscriptionForm = Form(
     single(
      "email" -> email
     )verifying("Subscriber with input email not found.", fields => fields match {
     		case inputEmail => Subscriber.findOneByEmail(inputEmail).isDefined
     	})
  )
  
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
	      email => {
		        Logger.debug("Deleting subscription with email " + email)
		        		
		        Subscriber.findOneByEmail(email) match{
		          case Some(subscriber) => {
		            // TODO create a service instead of calling salat directly
		            Subscriber.remove(subscriber)
		            // redirect to main page
		            Redirect(routes.Application.index)
		          }
		          case None => {
		            Logger.error("Subscriber with email " + email + " not found.")
		            Redirect(routes.Application.index)
		          }		          
		        }		        
		} 
	)
  }
  
  
  
   
}