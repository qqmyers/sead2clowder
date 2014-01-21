package controllers

import scala.collection.mutable.Map
import com.mongodb.casbah.commons.MongoDBObject
import jsonutils.JsonUtil
import models.FileDAO
import play.api.Logger
import play.api.Routes
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Concurrent.Channel
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.WebSocket
import play.api.libs.json.Json

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
object Application extends SecuredController {
  
  /**
   * Main page.
   */
//  def index = Action { implicit request =>
//    Ok(views.html.index())
//  }
  def index = SecuredAction() { request =>
  	implicit val user = request.user
  	val latestFiles = FileDAO.find(MongoDBObject()).sort(MongoDBObject("uploadDate" -> -1)).limit(5).toList
    Ok(views.html.index(latestFiles))
  }

  //Map to dataset id and (enumerator, channel) pair
  val channelMap = Map[String, (Enumerator[String], Channel[String])]()

  def webSocket(datasetId: String) = WebSocket.using[String] { request =>

    val iterator = Iteratee.foreach[String] { message =>
      Logger.info("Logging message: " + message)
      channelMap(datasetId)._2 push (message)
    }

    //Dataset not found; add a new entry
    if (channelMap.contains(datasetId) == false) {
      Logger.info("Dataset id not found")

      //val (enumerator, channel) = Concurrent.broadcast[String]
      channelMap += datasetId -> Concurrent.broadcast[String]
    }
    //the Enumerator returned by Concurrent.broadcast subscribes to the channel and will 
    //receive the pushed messages
    (iterator, channelMap(datasetId)._1)
  }

  /*lazy val (enumerator, channel) = Concurrent.broadcast[String]

  def webSocketSimple = WebSocket.using[String] { request =>
    val iteratee = Iteratee.foreach[String] { message =>
      channel push (message)
    }
    (iteratee, enumerator)
  }*/
  
  /**
   * Testing action.
   */
  def testJson = SecuredAction()  { implicit request =>
    Ok("{test:1}").as(JSON)
  }
  
    
  /**
   *  Javascript routing.
   */
  def javascriptRoutes = SecuredAction() { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.Admin.test,
        routes.javascript.Admin.secureTest,
        routes.javascript.Admin.reindexFiles,
        routes.javascript.Admin.createIndex,
        routes.javascript.Admin.buildIndex,
        routes.javascript.Admin.deleteIndex,
        routes.javascript.Admin.deleteAllIndexes,
        routes.javascript.Admin.getIndexes,
        routes.javascript.Tags.search,
        routes.javascript.Admin.setTheme,
        
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.getTags,
        api.routes.javascript.Datasets.addTags,
        api.routes.javascript.Datasets.removeTag,
        api.routes.javascript.Datasets.removeTags,
        api.routes.javascript.Datasets.removeAllTags,
        api.routes.javascript.Files.comment,
        api.routes.javascript.Files.getTags,
        api.routes.javascript.Files.addTags,
        api.routes.javascript.Files.removeTags,
        api.routes.javascript.Files.removeAllTags,
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Sections.getTags,
        api.routes.javascript.Sections.addTags,
        api.routes.javascript.Sections.removeTags,
        api.routes.javascript.Sections.removeAllTags,
        api.routes.javascript.Geostreams.searchSensors,
        api.routes.javascript.Geostreams.getSensorStreams,
        api.routes.javascript.Geostreams.searchDatapoints
      )
    ).as(JSON) 
  }
  
}
