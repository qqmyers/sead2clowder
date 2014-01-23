package controllers

import play.api.data.Form
import play.api.data.Forms._
import models.Subscriber
import api.WithPermission
import api.Permission

object Subscribers extends SecuredController {

  /**
   * New subscription form.
   */
  val subscriptionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "email" -> nonEmptyText
    )
    ((name, surname, email) => Subscriber(name = name, surname = surname, email = email))
    ((subscriber: Subscriber) => Some((subscriber.name, subscriber.surname, subscriber.email)))
   )
  
   def newSubscriber()  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newSubscriber(subscriptionForm)) 
  }
   
}