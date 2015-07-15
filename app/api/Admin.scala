package api

import play.api.mvc.Controller
import play.api.Play.current
import play.api.libs.json.Json.toJson
import services._
import services.mongodb.MongoSalatPlugin
import play.api.Logger

/**
 * Admin endpoints for JSON API.
 *
 * @author Luigi Marini
 * @author Constantinos Sophocleous
 */
object Admin extends Controller with ApiController {
  
  val appConfiguration: AppConfigurationService = services.DI.injector.getInstance(classOf[AppConfigurationService])
  val appAppearance: AppAppearanceService = services.DI.injector.getInstance(classOf[AppAppearanceService])

  /**
   * DANGER: deletes all data, keep users.
   */
  def deleteAllData = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.Admin)) { request =>
    current.plugin[MongoSalatPlugin].map(_.dropAllData())
    Ok(toJson("done"))
  }
  
  
  def removeAdmin = SecuredAction(authorization=WithPermission(Permission.Admin)) { request =>    
      Logger.debug("Removing admin")
      
      (request.body \ "email").asOpt[String].map { email =>        
      		appConfiguration.adminExists(email) match {
      	      case true => {
      	        Logger.debug("Removing admin with email " + email)     	        
      	        appConfiguration.removeAdmin(email)	
      	        
      	        Ok(toJson(Map("status" -> "success")))
      	      }
      	      case false => {
      	    	  Logger.info("Identified admin does not exist.")
      	    	  Ok(toJson(Map("status" -> "notmodified")))
      	      }
      	    }      	          
      }.getOrElse {
        BadRequest(toJson("Missing parameter [email]"))
      }      
  }
  
  
  def submitAppearance = SecuredAction(parse.json, authorization=WithPermission(Permission.Admin)) { request =>  
    
    (request.body \ "displayName").asOpt[String] match {
      case Some(displayName) =>{
        appAppearance.setDisplayedName(displayName)
        (request.body \ "welcomeMessage").asOpt[String] match {
          case Some(welcomeMessage)=>{
            appAppearance.setWelcomeMessage(welcomeMessage)
          }
          case None=>{}         
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case None =>{
        Logger.error("Missing parameter [displayName].")
    	BadRequest(toJson("Missing parameter [displayName]"))
      }      
    }  
  }
  

  def setViewNoLoggedIn = SecuredAction(parse.json, authorization=WithPermission(Permission.Admin)) { request =>
    (request.body \ "viewNoLoggedIn").asOpt[Boolean] match {
      case Some(viewNoLoggedIn) =>{
        appConfiguration.setViewNoLoggedIn(viewNoLoggedIn)
        Ok(toJson(Map("status" -> "success")))        
      }
      case None =>{
        Logger.error("Missing parameter [viewNoLoggedIn].")
    	BadRequest(toJson("Missing parameter [viewNoLoggedIn]"))
      }
    }
    
  }
  
  def submitAliases = SecuredAction(parse.json, authorization=WithPermission(Permission.Admin)) { request =>
    (request.body \ "datasetsAliasSingular").asOpt[String] match {
      case Some(datasetsAliasSingular) => AppConfiguration.setResourceDisplayAlias(datasetsAliasSingular.capitalize,"dataset","singular")
    }
    (request.body \ "datasetsAliasPlural").asOpt[String] match {
      case Some(datasetsAliasPlural) => AppConfiguration.setResourceDisplayAlias(datasetsAliasPlural.capitalize,"dataset","plural")
    }
    (request.body \ "collectionsAliasSingular").asOpt[String] match {
      case Some(collectionsAliasSingular) => AppConfiguration.setResourceDisplayAlias(collectionsAliasSingular.capitalize,"collection","singular")
    }
    (request.body \ "collectionsAliasPlural").asOpt[String] match {
      case Some(collectionsAliasPlural) => AppConfiguration.setResourceDisplayAlias(collectionsAliasPlural.capitalize,"collection","plural")
    }
    Ok(toJson(Map("status" -> "success")))
  }
  

}

