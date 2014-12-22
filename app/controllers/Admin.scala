/**
 *
 */
package controllers

import play.api.mvc.{Results, Controller, Action}
import play.api.Routes
import securesocial.core.SecureSocial._
import securesocial.core.{IdentityProvider, SecureSocial}
import api.ApiController
import api.WithPermission
import api.Permission
import securesocial.core.providers.utils.RoutesHelper
import services.{AppConfigurationService, VersusPlugin}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import models.AppConfiguration
import models.AppAppearance
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.Logger
import scala.concurrent._
import play.api.libs.ws.WS
import play.api.libs.ws.Response
import play.api.libs.concurrent.Promise
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import scala.concurrent.duration.Duration

/**
 * Administration pages.
 *
 * @author Luigi Marini
 *
 */
@Singleton
class Admin @Inject() (appConfiguration: AppConfigurationService) extends SecuredController {

  private val themes = "bootstrap/bootstrap.css" ::
    "bootstrap-amelia.min.css" ::
    "bootstrap-simplex.min.css" :: Nil

  def main = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val themeId = themes.indexOf(getTheme)
    Logger.debug("Theme id " + themeId)
    val appAppearance = AppAppearance.getDefault.get
    implicit val user = request.user
    Ok(views.html.admin(themeId, appAppearance))
  }
  
  def adminIndex = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val themeId = themes.indexOf(getTheme)
    val appAppearance = AppAppearance.getDefault.get
    implicit val user = request.user
    Ok(views.html.adminIndex(themeId, appAppearance))
  }

  def reindexFiles = SecuredAction(parse.json, authorization = WithPermission(Permission.AddIndex)) { request =>
    Ok("Reindexing")
  }

  def test = SecuredAction(parse.json, authorization = WithPermission(Permission.Public)) { request =>
    Ok("""{"message":"test"}""").as(JSON)
  }

  def secureTest = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }

  //get the available Adapters from Versus
  def getAdapters() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
        	//Change to Non-Blocking
            var adapterListResponse = Await.result(plugin.getAdapters(),Duration.Inf)  
//            var adapterListResponse = plugin.getAdapters()
//
//            for {
//              adapterList <- adapterListResponse
//            } yield {
//              Ok(adapterList.json)
//            }
            Ok(adapterListResponse.json)		
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
            Ok("No Versus Service")
          }
        } //match

//      } //Async

  }

  // Get available extractors from Versus
  def getExtractors() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
        	//Change to Non-Blocking
            var extractorListResponse = Await.result(plugin.getExtractors(),Duration.Inf)  
//            var extractorListResponse = plugin.getExtractors()
//
//            for {
//              extractorList <- extractorListResponse
//            } yield {
//              Ok(extractorList.json)
//            }
            //Ok(adapterListResponse)
            Ok(extractorListResponse.json)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
        	  Ok("No Versus Service")
          }
        } //match

//      } //Async

  }
  
  //Get available Measures from Versus 
  def getMeasures() = SecuredAction(authorization=WithPermission(Permission.Admin)){
     request =>
      
//    Async{  
    	current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
			// Change from Blocking to Non-Blocking            
        	var measureListResponse= Await.result(plugin.getMeasures(),Duration.Inf)  
//        	var measureListResponse= plugin.getMeasures()
//        	 
//        	for{
//        	  measureList<-measureListResponse
//        	}yield{
//        	 Ok(measureList.json)
//        	}
        	 //Ok(adapterListResponse)
        	Ok(measureListResponse.json) 
            }//case some
         
		 case None=>{
//		      Future(Ok("No Versus Service"))
		    Ok("No Versus Service")
		       }     
		 } //match
    
//   } //Async
        
  }

  //Get available Indexers from Versus 
  def getIndexers() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
			// Change from Blocking to Non-Blocking  
            var indexerListResponse = Await.result(plugin.getIndexers(),Duration.Inf)
//            var indexerListResponse = plugin.getIndexers()
//
//            for {
//              indexerList <- indexerListResponse
//            } yield {
//              Ok(indexerList.json)
//            }
			Ok(indexerListResponse.json)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
        	Ok("No Versus Service")
          }
        } //match

//      } //Async

  }

  // Get adapter, extractor,measure and indexer value and send it to VersusPlugin to send a create index request to Versus 
  def createIndex() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) {
    implicit request =>
//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
            Logger.debug("INSIDE CreateIndex()")
            val adapter = (request.body \ "adapter").as[String]
            val extractor = (request.body \ "extractor").as[String]
            val measure = (request.body \ "measure").as[String]
            val indexer = (request.body \ "indexer").as[String]
            Logger.debug("Form Parameters: " + adapter + " " + extractor + " " + measure + " " + indexer);
			// Change from Blocking to Non-Blocking  
            var reply = Await.result(plugin.createIndex(adapter, extractor, measure, indexer),Duration.Inf)    	
//            var reply = plugin.createIndex(adapter, extractor, measure, indexer)
//            for (response <- reply) yield Ok(response.body)
            Ok(reply.body)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
            Ok("No Versus Service")
          }
        } //match

//      } //Async
  }

  //Get list the of indexes from Versus 
  def getIndexes() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
           Logger.debug("::::Inside getIndexes()::::")
			// Change from Blocking to Non-Blocking  
            var indexListResponse = Await.result(plugin.getIndexes(),Duration.Inf)              
//            var indexListResponse = plugin.getIndexes()
//
//            for {
//              indexList <- indexListResponse
//            } yield {
//             if(indexList.body.isEmpty())
//              { 
//                Logger.debug(":::::::::::No:indexList.json")
//                Ok("No Index")
//                
//              }
//                else{
//                  Ok(indexList.json)
//                }
//            }
			if(indexListResponse.body.isEmpty())
			{ 
				Logger.debug(":::::::::::No:indexList.json")
				Ok("No Index")

			}
			else{
				Ok(indexListResponse.json)
			}

          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
			Ok("No Versus Service")
          }
        } //match

//      } //Async
  }

  //build a specific index in Versus
  def buildIndex(id: String) = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {

          case Some(plugin) => {
			// Change from Blocking to Non-Blocking  
            var buildResponse = Await.result(plugin.buildIndex(id),Duration.Inf) 
//            var buildResponse = plugin.buildIndex(id)
//
//            for {
//              buildRes <- buildResponse
//            } yield {
//              Ok(buildRes.body)
//            }
			Ok(buildResponse.body)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
			Ok("No Versus Service")
          }
        } //match

//      }

  }
  
  //Delete a specific index in Versus
  def deleteIndex(id: String)=SecuredAction(authorization=WithPermission(Permission.Admin)){
    request =>
//    Async{  
      current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
			// Change from Blocking to Non-Blocking  
            var deleteIndexResponse = Await.result(plugin.deleteIndex(id),Duration.Inf)         	 
//        	var deleteIndexResponse= plugin.deleteIndex(id)
//        	 
//        	for{
//        	  deleteIndexRes<-deleteIndexResponse
//        	}yield{
//        	 Ok(deleteIndexRes.body)
//        	}
        	Ok(deleteIndexResponse.body)
        	         
            }//case some
         
		 case None=>{
//		      Future(Ok("No Versus Service"))
			Ok("No Versus Service")		   
		       }     
		 } //match
    
//    }
  }

  //Delete all indexes in Versus

  def deleteAllIndexes() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>

//      Async {
        current.plugin[VersusPlugin] match {
        	
          case Some(plugin) => {
			// Change from Blocking to Non-Blocking  
			var deleteAllResponse = Await.result(plugin.deleteAllIndexes,Duration.Inf) 
//            var deleteAllResponse = plugin.deleteAllIndexes()
//
//            for {
//              deleteAllRes <- deleteAllResponse
//            } yield {
//              Ok(deleteAllRes.body)
//            }
			Ok(deleteAllResponse.body)
          } //case some

          case None => {
//            Future(Ok("No Versus Service"))
			Ok("No Versus Service")            
          }
        } //match

//      }
  }
  
  def setTheme() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { implicit request =>
    request.body.\("theme").asOpt[Int] match {
      case Some(theme) => {
        appConfiguration.setTheme(themes(theme))
        Ok("""{"status":"ok"}""").as(JSON)
      }
      case None => {
        Logger.error("no theme specified")
        BadRequest
      }
    }
  }

  def getTheme(): String = appConfiguration.getTheme()
  
  val adminForm = Form(
  single(
    "email" -> email
  )verifying("Admin already exists.", fields => fields match {
     		case adminMail => !appConfiguration.adminExists(adminMail)
     	})
)
  
  def newAdmin()  = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newAdmin(adminForm))
  }
  
  def submitNew() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        if (x.email.nonEmpty && appConfiguration.adminExists(x.email.get)) {
          adminForm.bindFromRequest.fold(
            errors => BadRequest(views.html.newAdmin(errors)),
            newAdmin => {
              appConfiguration.addAdmin(newAdmin)
              Redirect(routes.Application.index)
            }
          )
        } else {
          Unauthorized("Not authorized")
        }
      }
      case None => Unauthorized("Not authorized")
    }
  }
  
  def listAdmins() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        if (x.email.nonEmpty && appConfiguration.adminExists(x.email.get)) {
          appConfiguration.getDefault match{
            case Some(conf) =>{
              Ok(views.html.listAdmins(conf.admins))
            }
            case None => {
              Logger.error("Error getting application configuration!"); InternalServerError
            }
          }
        } else {
          Unauthorized("Not authorized")
        }
      }
      case None => Unauthorized("Not authorized")
    }
  }
  
}
