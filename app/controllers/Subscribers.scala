package controllers

import play.api.data.Form
import play.api.data.Forms._
import models.Subscriber
import api.WithPermission
import api.Permission
import play.api.Logger

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
  
   def subscribe()  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newSubscriber(subscriptionForm)) 
  }
  
  def unsubscribe()  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newSubscriber(subscriptionForm)) 
  }
  
  /**
   * Create subscription.
   */
  def submit() = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
    
        subscriptionForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newSubscriber(errors)),
	      subscriber => {
		        Logger.debug("Saving subscription " + subscriber.email)
		        		     
			        // TODO create a service instead of calling salat directly
		            Subscriber.save(subscriber)

		            // redirect to main page
		            Redirect(routes.Application.index)
			      } 
	)
  }
  
   
}