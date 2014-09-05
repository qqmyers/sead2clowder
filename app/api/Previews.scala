package api

import play.api.Logger
import java.io.FileInputStream
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.api.libs.json.JsObject
import com.mongodb.WriteConcern
import models.{UUID, ThreeDAnnotation, Preview}
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._
import java.io.BufferedReader
import java.io.FileReader
import javax.inject.{Inject, Singleton}
import services.{TileService, PreviewService, UserAccessRightsService, FileService, DatasetService}

/**
 * Files and datasets previews.
 *
 * @author Luigi Marini
 *
 */
@Singleton
class Previews @Inject()(previews: PreviewService, tiles: TileService, accessRights: UserAccessRightsService, files: FileService, datasets: DatasetService) extends ApiController {
  

  def downloadPreview(id: UUID, datasetid: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        Redirect(routes.Previews.download(id))
    }
  
  def checkPreviewAccess(user: Option[securesocial.core.Identity], preview: Preview, requestedRight: String): Boolean = {
    val filesChecker = services.DI.injector.getInstance(classOf[api.Files])
    val datasetsChecker = services.DI.injector.getInstance(classOf[api.Datasets])
    
    user match{
	    case Some(theUser)=>{
	    			val rightsForUser = accessRights.get(theUser)
	    			if(preview.file_id.isDefined){
	    				files.get(preview.file_id.get)match{
		    				case Some(file)=>{
		    					filesChecker.checkAccessForFileUsingRightsList(file, user , requestedRight, rightsForUser)
		    				}
		    				case None=>{
		    					false
		    				}
	    				}
	    			}
	    			else if(preview.dataset_id.isDefined){
	    				datasets.get(preview.dataset_id.get)match{
		    				case Some(dataset)=>{
		    					datasetsChecker.checkAccessForDatasetUsingRightsList(dataset, user , requestedRight, rightsForUser)
		    				}
		    				case None=>{
		    					false
		    				}
	    				}
	    			}else{
	    				true
	    			}			        	
	    }
	    case None=>{
	    			if(preview.file_id.isDefined){
	    				files.get(preview.file_id.get)match{
		    				case Some(file)=>{
		    					filesChecker.checkAccessForFile(file, user , requestedRight)
		    				}
		    				case None=>{
		    					false
		    				}
	    				}
	    			}
	    			else if(preview.dataset_id.isDefined){
	    				datasets.get(preview.dataset_id.get)match{
		    				case Some(dataset)=>{
		    					datasetsChecker.checkAccessForDataset(dataset, user , requestedRight)
		    				}
		    				case None=>{
		    					false
		    				}
	    				}
	    			}else{
	    				true
	    			}
	    }
    }
  }

  /**
   * Download preview bytes.
   */
  def download(id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        previews.get(id) match{
          case Some(preview)=>{
            
            checkPreviewAccess(request.user, preview, "view") match{
              case true=>{
            	  		previews.getBlob(id) match {
	
				          case Some((inputStream, filename, contentType, contentLength)) => {
				            request.headers.get(RANGE) match {
				              case Some(value) => {
				                val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
				                  case x if x.length == 1 => (x.head.toLong, contentLength - 1)
				                  case x => (x(0).toLong, x(1).toLong)
				                }
				                range match {
				                  case (start, end) =>
				
				                    inputStream.skip(start)
				                    import play.api.mvc.{SimpleResult, ResponseHeader}
				                    SimpleResult(
				                      header = ResponseHeader(PARTIAL_CONTENT,
				                        Map(
				                          CONNECTION -> "keep-alive",
				                          ACCEPT_RANGES -> "bytes",
				                          CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
				                          CONTENT_LENGTH -> (end - start + 1).toString,
				                          CONTENT_TYPE -> contentType
				                        )
				                      ),
				                      body = Enumerator.fromStream(inputStream)
				                    )
				                }
				              }
				              case None => {
				                Ok.chunked(Enumerator.fromStream(inputStream))
				                  .withHeaders(CONTENT_TYPE -> contentType)
				                  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
				
				              }
				            }
				          }
				          case None => Logger.error("No preview find " + id); InternalServerError("No preview found")
				        }
              }
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }
		               
	        
          }
          case None=>{
            Logger.error("No preview find " + id); InternalServerError("No preview found")
          }
        }  
    }


  /**
   * Upload a preview.
   */
  def upload(iipKey: String = "") =
    SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
      implicit request =>
        request.body.file("File").map {
          f =>
            Logger.debug("Uploading file " + f.filename)
            // store file
            //change stored preview type for zoom.it previews to avoid messup with uploaded XML metadata files
            var realContentType = f.contentType
            if(f.contentType.getOrElse("application/octet-stream").equals("application/xml"))
              realContentType = Some("application/dzi")
            
            val id = UUID(previews.save(new FileInputStream(f.ref.file), f.filename, realContentType))
            Logger.debug("ctp: "+realContentType)
            // for IIP server references, store the IIP URL, key and filename on the IIP server for possible later deletion of the previewed file
            if (f.filename.endsWith(".imageurl")) {
              val iipRefReader = new BufferedReader(new FileReader(f.ref.file));

              val serverLine = iipRefReader.readLine();
              var urlEnd = serverLine.indexOf("/", serverLine.indexOf("://") + 3)
              if (urlEnd == -1) {
                urlEnd = serverLine.length()
              }
              val iipURL = serverLine.substring(8, urlEnd)

              val imageLine = iipRefReader.readLine();
              val iipImage = imageLine.substring(imageLine.lastIndexOf("/") + 1)

              iipRefReader.close()

              previews.setIIPReferences(id, iipURL, iipImage, iipKey)
            }
            else if(f.filename.endsWith(".multispectral")){
	          previews.setIIPKey(id, iipKey)  
	        }
            Ok(toJson(Map("id" -> id.stringify)))
        }.getOrElse {
          BadRequest(toJson("File not attached."))
        }
    }

  /**
   * Upload preview metadata.
   *
   */
  def uploadMetadata(id: UUID) =
    SecuredAction(authorization = WithPermission(Permission.CreateFiles)) {
      request =>
        Logger.debug(request.body.toString)
        request.body match {
          case JsObject(fields) => {
            Logger.debug(fields.toString)
            previews.get(id) match {
              case Some(preview) =>{
                checkPreviewAccess(request.user, preview, "modify") match{
                  case true=>{
                    previews.updateMetadata(id, request.body)
                    Ok(toJson(Map("status" -> "success")))
                  }
                  case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
                }                
              }
              case None => BadRequest(toJson("Preview not found"))
            }
          }
          case _ => Logger.error("Expected a JSObject"); BadRequest(toJson("Expected a JSObject"))
        }
    }

  /**
   * Get preview metadata.
   *
   */
  def getMetadata(id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        previews.get(id) match {
          case Some(preview) => {
            checkPreviewAccess(request.user, preview, "view") match{
              case true=>{Ok(toJson(Map("id" -> preview.id.toString)))}
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }        	  
          }
          case None => Logger.error("Preview metadata not found " + id); InternalServerError
        }
    }

  /**
   * Add pyramid tile to preview.
   */
  def attachTile(preview_id: UUID, tile_id: UUID, level: String) =
    SecuredAction(authorization = WithPermission(Permission.CreateFiles)) {
      request =>
        request.body match {
          case JsObject(fields) => {
            previews.get(preview_id) match {
              case Some(preview) => {
                checkPreviewAccess(request.user, preview, "modify") match{
                  case true=>{
                	  tiles.get(tile_id) match {
		                  case Some(tile) => {
		                    tiles.updateMetadata(tile_id, preview_id, level, request.body)
		                    Ok(toJson(Map("status" -> "success")))
		                  }
		                  case None => BadRequest(toJson("Tile not found"))
		              }
                  }
                  case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
                }
              }
              case None => BadRequest(toJson("Preview not found " + preview_id))
            }
          }
          case _ => Ok("received something else: " + request.body + '\n')
        }
    }


  /**
   * Find tile for given preview, level and filename (row and column).
   */
  def getTile(dzi_id_dir: String, level: String, filename: String) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        val dzi_id = dzi_id_dir.replaceAll("_files", "")
        previews.get(UUID(dzi_id)) match {
          case Some(preview) => {
            checkPreviewAccess(request.user, preview, "view") match{
              case true=>{
            	  	tiles.findTile(UUID(dzi_id), filename, level) match {
			          case Some(tile) => {
			
			            tiles.getBlob(tile.id) match {
			
			              case Some((inputStream, filename, contentType, contentLength)) => {
			                request.headers.get(RANGE) match {
			                  case Some(value) => {
			                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
			                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
			                      case x => (x(0).toLong, x(1).toLong)
			                    }
			                    range match {
			                      case (start, end) =>
			
			                        inputStream.skip(start)
			                        import play.api.mvc.{SimpleResult, ResponseHeader}
			                        SimpleResult(
			                          header = ResponseHeader(PARTIAL_CONTENT,
			                            Map(
			                              CONNECTION -> "keep-alive",
			                              ACCEPT_RANGES -> "bytes",
			                              CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
			                              CONTENT_LENGTH -> (end - start + 1).toString,
			                              CONTENT_TYPE -> contentType
			                            )
			                          ),
			                          body = Enumerator.fromStream(inputStream)
			                        )
			                    }
			                  }
			                  case None => {
			                    Ok.chunked(Enumerator.fromStream(inputStream))
			                      .withHeaders(CONTENT_TYPE -> contentType)
			                      .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
			
			                  }
			                }
			              }
			              case None => Logger.error("No tile found: " + tile.id.toString()); InternalServerError("No tile found")
			
			            }
			
			          }
			          case None => Logger.error("Tile not found"); InternalServerError
			        }
              }
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }
          }
          case None => BadRequest(toJson("Preview not found " + dzi_id))
        }
        
        
        
        
    }

  /**
   * Add annotation to 3D model preview.
   */
  def attachAnnotation(preview_id: UUID, file_id: UUID) =
    SecuredAction(parse.json, authorization = WithPermission(Permission.CreateFiles), Some(file_id)) {
      request =>
        val x_coord = (request.body \ "x_coord").asOpt[String].getOrElse("0.0")
        val y_coord = (request.body \ "y_coord").asOpt[String].getOrElse("0.0")
        val z_coord = (request.body \ "z_coord").asOpt[String].getOrElse("0.0")
        val description = (request.body \ "description").asOpt[String].getOrElse("")

        previews.get(preview_id) match {
          case Some(preview) => {
            checkPreviewAccess(request.user, preview, "modify") match{
              case true=>{
                val annotation = ThreeDAnnotation(x_coord, y_coord, z_coord, description)
	            previews.annotation(preview_id, annotation)
	            Ok(toJson(Map("status" -> "success")))
              }
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }            
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }

  def editAnnotation(preview_id: UUID, file_id: UUID) =
    SecuredAction(parse.json, authorization = WithPermission(Permission.CreateFiles), Some(file_id)) {
      request =>
        Logger.debug("thereq: " + request.body.toString)
        val x_coord = (request.body \ "x_coord").asOpt[String].getOrElse("0.0")
        val y_coord = (request.body \ "y_coord").asOpt[String].getOrElse("0.0")
        val z_coord = (request.body \ "z_coord").asOpt[String].getOrElse("0.0")
        val description = (request.body \ "description").asOpt[String].getOrElse("")

        previews.get(preview_id) match {
          case Some(preview) => {
            checkPreviewAccess(request.user, preview, "modify") match{
              case true=>{
                previews.findAnnotation(preview_id, x_coord, y_coord, z_coord) match {
	              case Some(annotation) => {
	                previews.updateAnnotation(preview_id, annotation.id, description)
	                Ok(toJson(Map("status" -> "success")))
	              }
	              case None => Ok(toJson(Map("status" -> "success"))) //What the user sees locally must not change if an annotation is deleted after the user loads the dataset
	              //but before attempting to modify the selected annotation's description.
	              //BadRequest(toJson("Annotation for preview " + preview_id + " not found: " + x_coord + "," + y_coord + "," + z_coord))
	            }
              }
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }
  
  def deleteAnnotation(preview_id: UUID, file_id: UUID) =
    SecuredAction(parse.json, authorization = WithPermission(Permission.DeleteFiles), Some(file_id)) {
      request =>
        Logger.debug("thereq: " + request.body.toString)
        val x_coord = (request.body \ "x_coord").asOpt[String].getOrElse("0.0")
        val y_coord = (request.body \ "y_coord").asOpt[String].getOrElse("0.0")
        val z_coord = (request.body \ "z_coord").asOpt[String].getOrElse("0.0")

        previews.get(preview_id) match {
          case Some(preview) => {
            checkPreviewAccess(request.user, preview, "modify") match{
              case true=>{
                previews.findAnnotation(preview_id, x_coord, y_coord, z_coord) match {
	              case Some(annotation) => {
	                previews.deleteAnnotation(preview_id, annotation.id)
	                Ok(toJson(Map("status" -> "success")))
	              }
	              case None => Ok(toJson(Map("status" -> "success"))) //What the user sees locally must not change if an annotation is deleted after the user loads the dataset
	              //but before attempting to delete the selected annotation.
	              //BadRequest(toJson("Annotation for preview " + preview_id + " not found: " + x_coord + "," + y_coord + "," + z_coord))
	            }
              }
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }

  def listAnnotations(preview_id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        previews.get(preview_id) match {
          case Some(preview) => {
            checkPreviewAccess(request.user, preview, "view") match{
              case true=>{
                val annotationsOfPreview = previews.listAnnotations(preview_id)
	            val list = for (annotation <- annotationsOfPreview) yield jsonAnnotation(annotation)
	            Logger.debug("thelist: " + toJson(list))
	            Ok(toJson(list))
              }
              case false=>{Logger.error("Not authorized");BadRequest("Not authorized")}
            }
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }

  def jsonAnnotation(annotation: ThreeDAnnotation): JsValue = {
    toJson(Map("x_coord" -> annotation.x_coord.toString, "y_coord" -> annotation.y_coord.toString, "z_coord" -> annotation.z_coord.toString, "description" -> annotation.description))
  }

}

