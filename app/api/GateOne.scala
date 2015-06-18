package api

import javax.inject.{ Singleton, Inject }
import services.GateOnePlugin
import play.api.Play.current
import play.api.libs.json.Json.toJson
import models.GateOneUserOnMachine
import play.api.libs.json.JsValue

@Singleton
class GateOne extends ApiController {

	def addMachine() = SecuredAction(parse.json, authorization=WithPermission(Permission.Admin)) { request =>
      current.plugin[GateOnePlugin].map{ gateOnePlugin =>
      	var hostname = request.host
      	if(hostname.indexOf(":") > 0)
      	  hostname = hostname.substring(0, hostname.indexOf(":"))
      	if(!hostname.equals("localhost"))
      	  BadRequest(toJson("For security reasons, adding a new machine can only be done from localhost."))
      	else{
      	  (request.body \ "api_key").asOpt[String].map { apiKey =>
            (request.body \ "secret").asOpt[String].map { secret =>
	            gateOnePlugin.addMachine(apiKey, secret)
	            Ok(toJson(Map("status" -> "success")))
	        }.getOrElse {
	            BadRequest(toJson("Missing parameter [secret]"))
	        }
          }.getOrElse {
            BadRequest(toJson("Missing parameter [api_key]"))
          } 
      	}
      }.getOrElse {
            InternalServerError(toJson("ERROR: GateOnePlugin is not activated"))
      }
    }
    
    def addUserOnMachine() = SecuredAction(parse.json, authorization=WithPermission(Permission.Admin)) { request =>
      current.plugin[GateOnePlugin].map{ gateOnePlugin =>
      	  (request.body \ "api_key").asOpt[String].map { apiKey =>
            (request.body \ "medici_email").asOpt[String].map { userEmail =>
              (request.body \ "machine_username").asOpt[String].map { accessUsername =>
	            gateOnePlugin.addUserOnMachine(userEmail, apiKey, accessUsername)
	            Ok(toJson(Map("status" -> "success")))
	          }.getOrElse {
	            BadRequest(toJson("Missing parameter [machine_username]"))
	          }
	        }.getOrElse {
	            BadRequest(toJson("Missing parameter [medici_email]"))
	        }
          }.getOrElse {
            BadRequest(toJson("Missing parameter [api_key]"))
          }
      }.getOrElse {
            InternalServerError(toJson("ERROR: GateOnePlugin is not activated"))
      }
    }
    
    def getUserMachines() = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.AccessGateOne)) { request =>
      current.plugin[GateOnePlugin].map{ gateOnePlugin =>   	
    	request.user.map { currUser =>
    	   val userMachinesList = gateOnePlugin.getUserMachines(currUser.email.getOrElse(""))
    	   val userMachinesListJSON = for (userMachine <- userMachinesList) yield jsonUserMachine(userMachine)
    	   Ok(toJson(userMachinesListJSON))
    	}.getOrElse {
            BadRequest(toJson("Must be requesting as a Medici user"))
        }
      }.getOrElse {
            InternalServerError(toJson("ERROR: GateOnePlugin is not activated"))
      }	
    }
    
    def jsonUserMachine(userMachine: GateOneUserOnMachine): JsValue = {
    	toJson(Map("userEmail" -> userMachine.userEmail, "apiKey" -> userMachine.apiKey, "accessUsername" -> userMachine.accessUsername))
    }
  
}