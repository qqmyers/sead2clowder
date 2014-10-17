package api

import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.BodyParser
import play.api.mvc.Controller
import play.api.mvc.Result
import securesocial.core.AuthenticationMethod
import securesocial.core.Authorization
import securesocial.core.SecureSocial
import securesocial.core.SocialUser
import securesocial.core.IdentityId
import securesocial.core.UserService 
import securesocial.core.providers.UsernamePasswordProvider
import org.mindrot.jbcrypt._
import models.UUID

/**
 * New way to wrap actions for authentication so that we have access to Identity.
 *
 * @author Rob Kooper
 *
 */
trait ApiController extends Controller {
  val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

  def SecuredAction[A](p: BodyParser[A] = parse.json, authorization: Authorization = WithPermission(Permission.Public), resourceId: Option[UUID] = None)(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request =>
      {
        request.queryString.get("key") match { // token in url
          case Some(key) => {
            if (key.length > 0) {
              // TODO Check for key in database
              if (key(0).equals(play.Play.application().configuration().getString("commKey"))) {
                if (authorization.isAuthorized(anonymous))
                  f(RequestWithUser(Some(anonymous), request))
                else
                  Unauthorized("Not authorized")
              } else {
                Logger.debug("Key doesn't match")
                Unauthorized("Not authenticated")
              }
            } else
              Unauthorized("Not authenticated")
          }
          case None => {
            SecureSocial.currentUser(request) match { 
              case Some(identity) => {   //User cookie
                if(identity.fullName.equals("Anonymous User")){
                  Unauthorized("Not authorized")
                }
                else{
                	if (authorization.isInstanceOf[WithPermission]){
	                  var authorPermission = authorization.asInstanceOf[WithPermission]
	                  if (WithPermission(authorPermission.permission,resourceId).isAuthorized(identity))
	                	  f(RequestWithUser(Some(identity), request))
	                  else
	                	  Unauthorized("Not authorized")
	                }
	                else
	                	Unauthorized("Not authorized")
                }
              }
              case None => {
                request.queryString.get("username") match {
                  case Some(username)=>{	//Username + password
                    request.queryString.get("password") match {
	                  case Some(password)=>{
	                    val theUsername = username(0)
	                    val thePassword = password(0)
	                    UserService.findByEmailAndProvider(theUsername, UsernamePasswordProvider.UsernamePassword) match {
	                      case Some(identity) => {
	                        identity.passwordInfo match {
	                          case Some(pInfo)=>{
		                        if (BCrypt.checkpw(thePassword, pInfo.password)){
		                        	f(RequestWithUser(Some(identity), request))
		                        }
		                        else{
		                        	Unauthorized("Wrong password")
		                        }
	                          }
	                          case None =>{
	                            InternalServerError("Password validation error") //Not reporting the exact cause to not expose possible security vulnerabilities
	                          }
	                        }
	                      }
	                      case None =>{
	                        Unauthorized("User not found")
	                      } 
	                    }	                    
	                  }
	                  case None=>{
	                    Unauthorized("No password entered")
	                  }
                    }
                  }
                  case None=>{	
                    if (authorization.isAuthorized(null))
	                  f(RequestWithUser(None, request))
	                else
	                  Unauthorized("Not authorized")
                  }
                }
              }
            }
          }
        }
      }
  }
}
