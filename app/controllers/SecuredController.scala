/**
 *
 */
package controllers

import org.apache.commons.codec.binary.Base64
import org.mindrot.jbcrypt.BCrypt
import api.Permission
import api.RequestWithUser
import api.WithPermission
import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.BodyParser
import play.api.mvc.Controller
import play.api.mvc.Result
import play.api.mvc.Results
import securesocial.core.{AuthenticationMethod, Authorization, IdentityProvider, IdentityId, SecureSocial, SocialUser, UserService, Authenticator, Identity}
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.providers.utils.RoutesHelper
import models.UUID
import play.api.Play.current

/**
 * Enforce authentication and authorization.
 * 
 * @author Luigi Marini
 * @author Rob Kooper
 * @author Constantinos Sophocleous
 *
 */
trait SecuredController extends Controller {
  
    var appPort = play.api.Play.configuration.getString("https.port").getOrElse("")
  val httpProtocol = {
					if(!appPort.equals("")){
						"https"
					}
					else{
						appPort = play.api.Play.configuration.getString("http.port").getOrElse("9000")
						"http"
					}
		}
  
  val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

  def SecuredAction[A](p: BodyParser[A] = parse.anyContent, authorization: Authorization = WithPermission(Permission.Public), resourceId: Option[UUID] = None)(f: RequestWithUser[A] => Result) = Action(p) {
    implicit request =>
      {
        if(!Utils.protocol(request).equals(httpProtocol)){
          
	        Results.Redirect(httpProtocol + "://" + request.host.substring(0, request.host.lastIndexOf(":")+1) + appPort + request.path)
	      }
	      else{
		        request.headers.get("Authorization") match { // basic authentication
	          case Some(authHeader) => {
	            val header = new String(Base64.decodeBase64(authHeader.slice(6, authHeader.length).getBytes))
	            val credentials = header.split(":")
	            UserService.findByEmailAndProvider(credentials(0), UsernamePasswordProvider.UsernamePassword) match {
	              case Some(identity) => {
	                if(identity.fullName.equals("Anonymous User")){
	                	Unauthorized("Not authorized")
	                }
                   else{
	                if (BCrypt.checkpw(credentials(1), identity.passwordInfo.get.password)) {
	                  if (authorization.isInstanceOf[WithPermission]){
	                    var authorPermission = authorization.asInstanceOf[WithPermission]
	                    if (WithPermission(authorPermission.permission,resourceId).isAuthorized(identity))
	                    	f(RequestWithUser(Some(identity), request))
	                    else{
		                    if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
		                    	Results.Redirect(routes.Authentication.notAuthorized)
		                    }
		                    else{   //User not logged in, so redirect to login page
		                    	Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
		                    }
	                    }	
	                  }
	                  else{
		                    if(SecureSocial.currentUser.isDefined){  //User logged in but not authorized, so redirect to 'not authorized' page
		                    	Results.Redirect(routes.Authentication.notAuthorized)
		                    }
		                    else{   //User not logged in, so redirect to login page
		                    	Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not authorized.")
		                    }
	                    }
	                } else {
	                  Logger.debug("Password doesn't match")
	                  Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "Username/password are not valid.")
	                }
	               }
	              }
	              case None => {
	                Logger.debug("User not found")
	                Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "Username/password are not valid.")
	              }
	            }
	          }
	          case None => {
	            var identityOption: Option[Identity] = None
	            val authenticator = SecureSocial.authenticatorFromRequest
	            authenticator match{
	              case Some(authenticator) =>{
	                identityOption = UserService.find(authenticator.identityId)
	              }
	              case None =>{}
	            }
	            
	            identityOption match { // calls from browser
	              case Some(identity) => {
	               if(identity.fullName.equals("Anonymous User")){
	            	   Unauthorized("Not authorized")
                  }
                   else{
                    Authenticator.save(authenticator.get.touch)
	                if (authorization.isInstanceOf[WithPermission]){
	                  var authorPermission = authorization.asInstanceOf[WithPermission]
	                  if (WithPermission(authorPermission.permission,resourceId).isAuthorized(identity))
	                	  f(RequestWithUser(Some(identity), request))
	                  else
		                    Results.Redirect(routes.Authentication.notAuthorized)
	                }
	                else
		                    Results.Redirect(routes.Authentication.notAuthorized)
	               }
	              }
	              case None => {
	                if (authorization.isAuthorized(null))
	                  f(RequestWithUser(None, request))
	                else
	                  Results.Redirect(RoutesHelper.login.absoluteURL(IdentityProvider.sslEnabled)).flashing("error" -> "You are not logged in.")
	              }
	            }
	          }
	        }
	      }
        
        
        
      }
  }
}
