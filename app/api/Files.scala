package api

import java.io.FileInputStream
import java.net.{URL, URLEncoder}
import javax.inject.Inject
import javax.mail.internet.MimeUtility

import _root_.util.{FileUtils, Parsers, JSONLD, SearchUtils}

import com.mongodb.casbah.Imports._
import controllers.Previewers
import jsonutils.JsonUtil
import models._
import play.api.Logger
import play.api.Play.{configuration, current}
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc.{ResponseHeader, SimpleResult}

import services._

import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray

import java.text.SimpleDateFormat
import java.util.Date

import controllers.Utils

/**
 * Json API for files.
 */
class Files @Inject()(
  files: FileService,
  datasets: DatasetService,
  collections: CollectionService,
  queries: MultimediaQueryService,
  tags: TagService,
  comments: CommentService,
  extractions: ExtractionService,
  dtsrequests:ExtractionRequestsService,
  previews: PreviewService,
  threeD: ThreeDService,
  sqarql: RdfSPARQLService,
  metadataService: MetadataService,
  contextService: ContextLDService,
  thumbnails: ThumbnailService,
  events: EventService,
  folders: FolderService,
  spaces: SpaceService,
  userService: UserService,
  appConfig: AppConfigurationService) extends ApiController {

  def get(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    Logger.debug("GET file with id " + id)
    files.get(id) match {
      case Some(file) => {
        val serveradmin = request.user match {
          case Some(u) => (u.status==UserStatus.Admin)
          case None => false
        }
        Ok(jsonFile(file, serveradmin))
      }
      case None => {
        Logger.error("Error getting file" + id)
        InternalServerError
      }
    }
  }

  /**
   * List all files.
   */
  def list = DisabledAction { implicit request =>
    val serveradmin = request.user match {
      case Some(u) => (u.status==UserStatus.Admin)
      case None => false
    }
    val list = for (f <- files.listFilesNotIntermediate()) yield jsonFile(f, serveradmin)
    Ok(toJson(list))
  }

  def downloadByDatasetAndFilename(datasetId: UUID, filename: String, preview_id: UUID) =
    PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    datasets.getFileId(datasetId, filename) match {
      case Some(id) => Redirect(routes.Files.download(id))
      case None => Logger.error("Error getting dataset" + datasetId); InternalServerError
    }
  }

  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: UUID) =
    PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
      //Check the license type before doing anything.
      files.get(id) match {
          case Some(file) => {    
              if (file.licenseData.isDownloadAllowed(request.user) || Permission.checkPermission(request.user, Permission.DownloadFiles, ResourceRef(ResourceRef.file, file.id))) {
		        files.getBytes(id) match {            
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
                    val userAgent = request.headers.get("user-agent").getOrElse("")
                    Ok.chunked(Enumerator.fromStream(inputStream))
		                  .withHeaders(CONTENT_TYPE -> contentType)
                      .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, userAgent)))
		              }
		            }
		          }
		          case None => {
		            Logger.error("Error getting file" + id)
		            NotFound
		          }
		        }
              }
              else {
            	  //Case where the checkLicenseForDownload fails
            	  Logger.error("The file is not able to be downloaded")
            	  BadRequest("The license for this file does not allow it to be downloaded.")
              }
          }
          case None => {
        	  //Case where the file could not be found
        	  Logger.debug(s"Error getting the file with id $id.")
        	  BadRequest("Invalid file ID")
          }
      }
    }

  /**
   *
   * Download query used by Versus
   *
   */
  def downloadquery(id: UUID) =
    PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
        queries.get(id) match {
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
                  .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, request.headers.get("user-agent").getOrElse(""))))
              }
            }
          }
          case None => {
            Logger.error("Error getting file" + id)
            NotFound
          }
        }
    }
  def getMetadataDefinitions(id: UUID, space: Option[String]) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
  implicit val user = request.user
    files.get(id) match {
      case Some(file) => {
        val spacesToCheck = collection.mutable.HashSet[models.UUID]()
        space match {
          case Some(spaceId) => {
            spaces.get(UUID(spaceId)) match {
              case Some(space) => {
                spacesToCheck += space.id
              }
              case None =>
            }
          }
          case None => {
            val datasetsContainingFile = datasets.findByFileId(file.id).sortBy(_.name)
            val foldersContainingFile = folders.findByFileId(file.id).sortBy(_.name)

            datasetsContainingFile.foreach{ dataset =>
              dataset.spaces.foreach{space => spacesToCheck += space}
            }

            foldersContainingFile.foreach{ folder =>
              datasets.get(folder.parentDatasetId) match {
                case Some(dataset) => dataset.spaces.foreach{space => spacesToCheck += space}
                case None =>
              }

            }
          }
        }

        val metadataDefinitions = collection.mutable.HashSet[models.MetadataDefinition]()
        spacesToCheck.foreach{ spaceId =>
          spaces.get(spaceId) match {
            case Some(space) => metadataService.getDefinitions(Some(space.id)).foreach{definition => metadataDefinitions += definition}
            case None =>
          }
        }
        if (metadataDefinitions.size == 0) {
          metadataService.getDefinitions().foreach{definition => metadataDefinitions += definition}
        }
        Ok(toJson(metadataDefinitions.toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )))
      }
      case None => BadRequest(toJson("The requested file does not exist"))
    }
  }


  /**
   * Add metadata to file.
   */
  def addMetadata(id: UUID) =
    PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
        Logger.debug(s"Adding metadata to file $id")
        val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
        files.get(id) match {
          case Some(x) => {
            val json = request.body
            //parse request for agent/creator info
            //creator can be UserAgent or ExtractorAgent
            val creator = ExtractorAgent(id = UUID.generate(),
              extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/deprecatedapi")))

            // check if the context is a URL to external endpoint
            val contextURL: Option[URL] = None

            // check if context is a JSON-LD document
            val contextID: Option[UUID] = None

            // when the new metadata is added
            val createdAt = new Date()

            //parse the rest of the request to create a new models.Metadata object
            val attachedTo = ResourceRef(ResourceRef.file, id)
            val version = None
            val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
              json, version)

            //add metadata to mongo
            metadataService.addMetadata(metadata)
            val mdMap = metadata.getExtractionSummary

            //send RabbitMQ message
            current.plugin[RabbitmqPlugin].foreach { p =>
              val dtkey = s"${p.exchange}.metadata.added"
              p.extract(ExtractorMessage(metadata.attachedTo.id, UUID(""), controllers.Utils.baseEventUrl(request), dtkey, mdMap, "", UUID(""), ""))
            }

            files.index(id)
            Ok(toJson(Map("status" -> "success")))
          }
          case None => Logger.error(s"Error getting file $id"); NotFound
        }
        Ok(toJson("success"))
    }

  /**
   * Add metadata in JSON-LD format.
   */
  def addMetadataJsonLD(id: UUID) =
    PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
      files.get(id) match {
        case Some(x) => {
          val json = request.body

          // parse request for JSON-LD model
          var model: RDFModel = null
          json.validate[RDFModel] match {
            case e: JsError => {
              Logger.error("Errors: " + JsError.toFlatForm(e) + "\n\t" + json.toString())
              BadRequest(JsError.toFlatJson(e))
            }
            case s: JsSuccess[RDFModel] => { 
              model = s.get 
          
              //parse request for agent/creator info
              //creator can be UserAgent or ExtractorAgent
              var creator: models.Agent = null
              json.validate[Agent] match {
                case s: JsSuccess[Agent] => {
                  creator = s.get
                  //if creator is found, continue processing
                  val context: JsValue = (json \ "@context")
    
                  // check if the context is a URL to external endpoint
                  val contextURL: Option[URL] = context.asOpt[String].map(new URL(_))
    
                  // check if context is a JSON-LD document
                  val contextID: Option[UUID] =
                    if (context.isInstanceOf[JsObject]) {
                      context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _))
                    } else if (context.isInstanceOf[JsArray]) {
                      context.asOpt[JsArray].map(contextService.addContext(new JsString("context name"), _))
                    } else None
    
                  // when the new metadata is added
                  val createdAt = Parsers.parseDate((json \ "created_at")).fold(new Date())(_.toDate)
    
                  //parse the rest of the request to create a new models.Metadata object
                  val attachedTo = ResourceRef(ResourceRef.file, id)
                  val content = (json \ "content")
                  val version = None
                  val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
                    content, version)
    
                  //add metadata to mongo
                  metadataService.addMetadata(metadata)
                  val mdMap = metadata.getExtractionSummary
    
                  //send RabbitMQ message
                  current.plugin[RabbitmqPlugin].foreach { p =>
                    val dtkey = s"${p.exchange}.metadata.added"
                    p.extract(ExtractorMessage(metadata.attachedTo.id, UUID(""), controllers.Utils.baseEventUrl(request), dtkey, mdMap, "", UUID(""), ""))
                  }
                  
                  files.index(id)
                  Ok(toJson("Metadata successfully added to db"))
                }
                case e: JsError => {
                  Logger.error("Error getting creator")
                  BadRequest(toJson(s"Creator data is missing or incorrect."))
                }
              }
            }
          }
        }
        case None => Logger.error(s"Error getting file $id"); NotFound
      }
    }

  def getMetadataJsonLD(id: UUID, extFilter: Option[String]) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    files.get(id) match {
      case Some(file) => {
        //get metadata and also fetch context information
        val listOfMetadata = extFilter match {
          case Some(f) => metadataService.getExtractedMetadataByAttachTo(ResourceRef(ResourceRef.file, id), f)
            .map(JSONLD.jsonMetadataWithContext(_))
          case None => metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.file, id))
            .map(JSONLD.jsonMetadataWithContext(_))
        }
        Ok(toJson(listOfMetadata))
      }
      case None => {
        Logger.error("Error getting file  " + id);
        BadRequest(toJson("Error getting file  " + id))
      }
    }
  }

  def removeMetadataJsonLD(id: UUID, extFilter: Option[String]) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    files.get(id) match {
      case Some(file) => {
        val num_removed = extFilter match {
          case Some(f) => metadataService.removeMetadataByAttachToAndExtractor(ResourceRef(ResourceRef.file, id), f)
          case None => metadataService.removeMetadataByAttachTo(ResourceRef(ResourceRef.file, id))
        }
        // send extractor message after attached to resource
        current.plugin[RabbitmqPlugin].foreach { p =>
          val dtkey = s"${p.exchange}.metadata.removed"
          p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, Map[String, Any](
            "resourceType"->ResourceRef.file,
            "resourceId"->id.toString), "", id, ""))
        }
        Ok(toJson(Map("status" -> "success", "count" -> num_removed.toString)))
      }
      case None => {
        Logger.error("Error getting file  " + id);
        BadRequest(toJson("Error getting file  " + id))
      }
    }
  }

  /**
   * Add Versus metadata to file: use by Versus Extractor
   * REST enpoint:POST api/files/:id/versus_metadata
   */
  def addVersusMetadata(id: UUID) =
    PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>

     Logger.trace("INSIDE ADDVersusMetadata=: "+id.toString )
      files.get(id) match {
        case Some(file) => {
          Logger.debug("Adding Versus Metadata to file " + id.toString())
          files.addVersusMetadata(id, request.body)
          Ok("Added Versus Descriptor")
        }
        case None => {
          Logger.error("Error in getting file " + id)
          NotFound
        }
      }
    }

  /**
   * Upload file using multipart form enconding.
   */
  @deprecated
  def upload(showPreviews: String = "DatasetLevel", originalZipFile: String = "", flagsFromPrevious: String = "") = PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
    val uploadedFiles = FileUtils.uploadFilesMultipart(request, showPreviews=showPreviews, originalZipFile=originalZipFile, flagsFromPrevious=flagsFromPrevious)
    uploadedFiles.length match {
      case 0 => BadRequest("No files uploaded")
      case 1 => Ok(Json.obj("id" -> uploadedFiles.head.id))
      case _ => Ok(Json.obj("ids" -> uploadedFiles.toList))
    }
  }

  /**
   * Upload a file to a specific dataset
   */
  def uploadToDataset(dataset_id: UUID, showPreviews: String="DatasetLevel", originalZipFile: String = "", flagsFromPrevious: String = "") = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.multipartFormData) { implicit request =>
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val uploadedFiles = FileUtils.uploadFilesMultipart(request, Some(dataset), showPreviews = showPreviews, originalZipFile = originalZipFile, flagsFromPrevious = flagsFromPrevious)
        uploadedFiles.length match {
          case 0 => BadRequest("No files uploaded")
          case 1 => Ok(Json.obj("id" -> uploadedFiles.head.id))
          case _ => Ok(Json.obj("ids" -> uploadedFiles.toList))
        }
      }
      case None => {
        BadRequest(s"Dataset with id=${dataset_id} does not exist")
      }
    }
  }

  /**
   * Upload intermediate file of extraction chain using multipart form enconding and continue chaining.
   */
  def uploadIntermediate(originalIdAndFlags: String) =
    PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
      val uploadedFiles = FileUtils.uploadFilesMultipart(request, key="File", intermediateUpload=true, flagsFromPrevious=originalIdAndFlags)
      uploadedFiles.length match {
        case 0 => BadRequest("No files uploaded")
        case 1 => Ok(Json.obj("id" -> uploadedFiles.head.id))
        case _ => Ok(Json.obj("ids" -> uploadedFiles.toList))
      }
  }

  /**
   * Reindex a file.
   */
  def reindex(id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    files.get(id) match {
      case Some(file) => {
        current.plugin[ElasticsearchPlugin].foreach {
          _.index(file)
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case None => {
        Logger.error("Error getting file" + id)
        BadRequest(toJson(s"The given file id $id is not a valid ObjectId."))
      }
    }
  }

    /**
   * Send job for file preview(s) generation at a later time.
   */
  def sendJob(file_id: UUID, fileType: String) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.file, file_id))) { implicit request =>
    files.get(file_id) match {
      case Some(theFile) => {
        var nameOfFile = theFile.filename
        var flags = ""
        if (nameOfFile.toLowerCase().endsWith(".ptm")) {
          var thirdSeparatorIndex = nameOfFile.indexOf("__")
          if (thirdSeparatorIndex >= 0) {
            var firstSeparatorIndex = nameOfFile.indexOf("_")
            var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
            flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
            nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
          }
        }

        val showPreviews = theFile.showPreviews

        Logger.debug("(Re)sending job for file " + nameOfFile)

        val id = theFile.id
        if (showPreviews.equals("None"))
          flags = flags + "+nopreviews"

        val key = "unknown." + "file." + fileType.replace("__", ".")

        val host = Utils.baseEventUrl(request)
        val extra = Map("filename" -> theFile.filename)

        // TODO replace null with None
        current.plugin[RabbitmqPlugin].foreach {
          _.extract(ExtractorMessage(id, id, host, key, extra, theFile.length.toString, null, flags))
        }

        Ok(toJson(Map("id" -> id.stringify)))

      }
      case None => {
        BadRequest(toJson("File not found."))
      }
    }
  }

  /**
   * Add preview to file.
   */
  def attachPreview(file_id: UUID, preview_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.file, file_id)))(parse.json) { implicit request =>
    // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        eid
      } else {
        Logger.debug("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        Some("Other")
      }
      request.body match {
        case JsObject(fields) => {
          files.get(file_id) match {
            case Some(file) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToFile(preview_id, file_id, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("File not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("File not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }


  def jsonFile(file: File, serverAdmin: Boolean = false): JsValue = {
    val defaultMap = Map(
      "id" -> file.id.toString,
      "filename" -> file.filename,
      "filedescription" -> file.description,
      "content-type" -> file.contentType,
      "date-created" -> file.uploadDate.toString(),
      "size" -> file.length.toString,
      "authorId" -> file.author.id.stringify,
      "status" -> file.status)

    // Only include filepath if using DiskByte storage and user is serverAdmin
    val jsonMap = file.loader match {
      case "services.filesystem.DiskByteStorageService" => {
        if (serverAdmin)
          Map(
            "id" -> file.id.toString,
            "filename" -> file.filename,
            "filepath" -> file.loader_id,
            "filedescription" -> file.description,
            "content-type" -> file.contentType,
            "date-created" -> file.uploadDate.toString(),
            "size" -> file.length.toString,
            "authorId" -> file.author.id.stringify,
            "status" -> file.status)
        else
          defaultMap
      }
      case _ => defaultMap
    }
    toJson(jsonMap)
  }

  def jsonFileWithThumbnail(file: File): JsValue = {
    var fileThumbnail = "None"
    if (!file.thumbnail_id.isEmpty)
      fileThumbnail = file.thumbnail_id.toString().substring(5, file.thumbnail_id.toString().length - 1)

    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "dateCreated" -> file.uploadDate.toString(), "thumbnail" -> fileThumbnail,
    		"authorId" -> file.author.id.stringify, "status" -> file.status))
  }

  def toDBObject(fields: Seq[(String, JsValue)]): DBObject = {
    fields.map(field =>
      field match {
        // TODO handle jsarray
        //          case (key, JsArray(value: Seq[JsValue])) => MongoDBObject(key -> getValueForSeq(value))
        case (key, jsObject: JsObject) => MongoDBObject(key -> toDBObject(jsObject.fields))
        case (key, jsValue: JsValue) => MongoDBObject(key -> jsValue.as[String])
      }
    ).reduce((left: DBObject, right: DBObject) => left ++ right)
  }

  def filePreviewsList(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
      files.get(id) match {
        case Some(file) => {
          val filePreviews = previews.findByFileId(file.id);
          val list = for (prv <- filePreviews) yield jsonPreview(prv)
          Ok(toJson(list))
        }
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }

  def jsonPreview(preview: Preview): JsValue = {
    toJson(Map("id" -> preview.id.toString, "filename" -> getFilenameOrEmpty(preview), "contentType" -> preview.contentType))
  }

  def getFilenameOrEmpty(preview: Preview): String = {
    preview.filename match {
      case Some(strng) => strng
      case None => ""
    }
  }

  /**
   * Add 3D geometry file to file.
   */
  def attachGeometry(file_id: UUID, geometry_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.file, file_id)))(parse.json) { implicit request =>
      request.body match {
        case JsObject(fields) => {
          files.get(file_id) match {
            case Some(file) => {
              threeD.getGeometry(geometry_id) match {
                case Some(geometry) =>
                  threeD.updateGeometry(file_id, geometry_id, fields)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Geometry file not found"))
              }
            }
            case None => BadRequest(toJson("File not found " + file_id))
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }


  /**
   * Add 3D texture to file.
   */
  def attachTexture(file_id: UUID, texture_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.file, file_id)))(parse.json) { implicit request =>
      request.body match {
        case JsObject(fields) => {
          files.get((file_id)) match {
            case Some(file) => {
              threeD.getTexture(texture_id) match {
                case Some(texture) => {
                  threeD.updateTexture(file_id, texture_id, fields)
                  Ok(toJson(Map("status" -> "success")))
                }
                case None => BadRequest(toJson("Texture file not found"))
              }
            }
            case None => BadRequest(toJson("File not found " + file_id))
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  /**
   * Add thumbnail to file.
   */
  def attachThumbnail(file_id: UUID, thumbnail_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.file, file_id))) { implicit request =>
      files.get(file_id) match {
        case Some(file) => {
          thumbnails.get(thumbnail_id) match {
            case Some(thumbnail) => {
              files.updateThumbnail(file_id, thumbnail_id)
              val datasetList = datasets.findByFileId(file.id)
              for (dataset <- datasetList) {
                if (dataset.thumbnail_id.isEmpty) {
                  datasets.updateThumbnail(dataset.id, thumbnail_id)
                  dataset.collections.foreach(c => {
                    collections.get(c).foreach(col => {
                      if (col.thumbnail_id.isEmpty) {
                        collections.updateThumbnail(col.id, thumbnail_id)
                      }
                    })
                  })
                }
              }
              Ok(toJson(Map("status" -> "success")))
            }
            case None => BadRequest(toJson("Thumbnail not found"))
          }
        }
        case None => BadRequest(toJson("File not found " + file_id))
      }
  }
  
  
  /**
   * Add thumbnail to query file.
   */
  def attachQueryThumbnail(query_id: UUID, thumbnail_id: UUID) = PermissionAction(Permission.AddFile) { implicit request =>
    // TODO should we check here for permission on query?
      queries.get(query_id) match {
        case Some(file) => {
          thumbnails.get(thumbnail_id) match {
            case Some(thumbnail) => {
              queries.updateThumbnail(query_id, thumbnail_id)  
              Ok(toJson(Map("status" -> "success")))
            }
            case None => {
            	Logger.error("Thumbnail not found")
            	BadRequest(toJson("Thumbnail not found"))
            }
          }
        }
        case None => {
          Logger.error("File not found")
          BadRequest(toJson("Query file not found " + query_id))
        }
      }
  }
  

  /**
   * Find geometry file for given 3D file and geometry filename.
   */
  def getGeometry(three_d_file_id: UUID, filename: String) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, three_d_file_id))) { implicit request =>
        threeD.findGeometry(three_d_file_id, filename) match {
          case Some(geometry) => {

            threeD.getGeometryBlob(geometry.id) match {

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
                        import play.api.mvc.{ResponseHeader, SimpleResult}
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
                    //IMPORTANT: Setting CONTENT_LENGTH header here introduces bug!                  
                    Ok.chunked(Enumerator.fromStream(inputStream))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, request.headers.get("user-agent").getOrElse(""))))

                  }
                }
              }
              case None => Logger.error("No geometry file found: " + geometry.id); InternalServerError("No geometry file found")

            }
          }
          case None => Logger.error("Geometry file not found"); InternalServerError
        }
    }
  
  
  

  /**
   * Find texture file for given 3D file and texture filename.
   */
  def getTexture(three_d_file_id: UUID, filename: String) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, three_d_file_id))) { implicit request =>
        threeD.findTexture(three_d_file_id, filename) match {
          case Some(texture) => {

            threeD.getBlob(texture.id) match {

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
                    //IMPORTANT: Setting CONTENT_LENGTH header here introduces bug!
                    Ok.chunked(Enumerator.fromStream(inputStream))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      //.withHeaders(CONTENT_LENGTH -> contentLength.toString)
                      .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, request.headers.get("user-agent").getOrElse(""))))

                  }
                }
              }
              case None => Logger.error("No texture file found: " + texture.id.toString()); InternalServerError("No texture found")

            }
          }
          case None => Logger.error("Texture file not found"); InternalServerError
        }
    }

  /**
    * REST endpoint: PUT: update or change the filename
    * args
    *   id: the UUID associated with this file
    * data
    *   name: String
    */
  def updateFileName(id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, id)))(parse.json) {
    implicit request =>
      implicit val user = request.user
      if (UUID.isValid(id.stringify)) {
        var name: String = null
        val aResult = (request.body \ "name").validate[String]
        aResult match {
          case s: JsSuccess[String] => {
            name = s.get
          }
          case e: JsError => {
            Logger.error("Errors: " + JsError.toFlatJson(e).toString())
            BadRequest(toJson(s"name data is missing"))
          }
        }
        Logger.debug(s"Update title for file with id $id. New name: $name")
        files.renameFile(id, name)
        files.get(id) match {
          case Some(file) => {
            events.addObjectEvent(user, id, file.filename, "update_file_information")
          }

        }
        files.index(id)
        Ok(Json.obj("status" -> "success"))
      }
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  //Update License code
  /**
   * REST endpoint: POST: update the license data associated with a specific File
   * 
   *  Takes one arg, id:
   *  
   *  id, the UUID associated with this file 
   *  
   *  The data contained in the request body will be containe the following key-value pairs:
   *  
   *  licenseType, currently:
   *        license1 - corresponds to Limited 
   *        license2 - corresponds to Creative Commons
   *        license3 - corresponds to Public Domain
   *        
   *  rightsHolder, currently only required if licenseType is license1. Reflects the specific name of the organization or person that holds the rights
   *   
   *  licenseText, currently tied to the licenseType
   *        license1 - Free text that a user can enter to describe the license
   *        license2 - 1 of 6 options (or their abbreviations) that reflects the specific set of 
   *        options associated with the Creative Commons license, these are:
   *            Attribution-NonCommercial-NoDerivs (by-nc-nd)
   *            Attribution-NoDerivs (by-nd)
   *            Attribution-NonCommercial (by-nc)
   *            Attribution-NonCommercial-ShareAlike (by-nc-sa)
   *            Attribution-ShareAlike (by-sa)
   *            Attribution (by)
   *        license3 - Public Domain Dedication
   *        
   *  licenseUrl, free text that a user can enter to go with the licenseText in the case of license1. Fixed URL's for the other 2 cases.
   *  
   *  allowDownload, true or false, whether the file or dataset can be downloaded. Only relevant for license1 type.  
   */
  def updateLicense(id: UUID) = PermissionAction(Permission.EditLicense, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
      if (UUID.isValid(id.stringify)) {

          //Set up the vars we are looking for
          var licenseType: String = null;
          var rightsHolder: String = null;
          var licenseText: String = null;
          var licenseUrl: String = null;
          var allowDownload: String = null;
          
          var aResult: JsResult[String] = (request.body \ "licenseType").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                licenseType = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseType data is missing."))
              }
          }
          
          aResult = (request.body \ "rightsHolder").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                rightsHolder = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"rightsHolder data is missing."))
              }
          }
          
          aResult = (request.body \ "licenseText").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                licenseText = s.get
                                
                //Modify the abbreviations if they were sent in that way
                if (licenseText == "by-nc-nd") {
                    licenseText = "Attribution-NonCommercial-NoDerivs"
                }
                else if (licenseText == "by-nd") {
                    licenseText = "Attribution-NoDerivs"
                }
                else if (licenseText == "by-nc") {
                    licenseText = "Attribution-NonCommercial"
                }
                else if (licenseText == "by-nc-sa") {
                    licenseText = "Attribution-NonCommercial-ShareAlike"
                }
                else if (licenseText == "by-sa") {
                    licenseText = "Attribution-ShareAlike"
                }
                else if (licenseText == "by") {
                    licenseText = "Attribution"
                }
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseText data is missing."))
              }
          }
          
          aResult = (request.body \ "licenseUrl").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                licenseUrl = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseUrl data is missing."))
              }
          }
          
          aResult = (request.body \ "allowDownload").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                allowDownload = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"allowDownload data is missing."))
              }
          }          
          
          Logger.debug(s"updateLicense for file with id  $id. Args are $licenseType, $rightsHolder, $licenseText, $licenseUrl, $allowDownload")
          
          files.updateLicense(id, licenseType, rightsHolder, licenseText, licenseUrl, allowDownload)
          Ok(Json.obj("status" -> "success"))
      } 
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }
  
  
  
  //End, Update License code

  // ---------- Tags related code starts ------------------
  /**
   * REST endpoint: GET: gets the tag data associated with this file.
   */
  def getTags(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
      Logger.debug("Getting tags for file with id " + id)
      /* Found in testing: given an invalid ObjectId, a runtime exception
       * ("IllegalArgumentException: invalid ObjectId") occurs in Services.files.get().
       * So check it first.
       */
      if (UUID.isValid(id.stringify)) {
        files.get(id) match {
          case Some(file) => Ok(Json.obj("id" -> file.id.toString, "filename" -> file.filename,
            "tags" -> Json.toJson(file.tags.map(_.name))))
          case None => {
            Logger.error("The file with id " + id + " is not found.")
            NotFound(toJson("The file with id " + id + " is not found."))
          }
        }
      } else {
        Logger.error("The given id " + id + " is not a valid ObjectId.")
        BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
      }
  }

  /*
   *  Helper function to handle adding and removing tags for files/datasets/sections.
   *  Input parameters:
   *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
   *      op_type:  one of the two strings "add", "remove"
   *      id:       the id in the original addTags call
   *      request:  the request in the original addTags call
   *  Return type:
   *      play.api.mvc.SimpleResult[JsValue]
   *      in the form of Ok, NotFound and BadRequest
   *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
   *      which contains the cause of the error, such as "No 'tags' specified", and
   *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
   */
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): SimpleResult = {

    val (not_found, error_str) = tags.addTagsHelper(obj_type, id, request)
    files.get(id) match {
    case Some(file) =>{
      events.addObjectEvent(request.user, file.id, file.filename, "add_tags_file")
      }
    }
    // Now the real work: adding the tags.
    if ("" == error_str) {
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): SimpleResult = {

    val (not_found, error_str) = tags.removeTagsHelper(obj_type, id, request)
    files.get(id) match {
          case Some(file) =>{
            events.addObjectEvent(request.user, file.id, file.filename, "remove_tags_file")
          }
        }

    if ("" == error_str) {
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

    /**
   * REST endpoint: POST: Adds tags to a file.
   * Tag's (name, userId, extractor_id) tuple is used as a unique key.
   * In other words, the same tag names but diff userId or extractor_id are considered as diff tags,
   * so will be added.
   */
  def addTags(id: UUID) = PermissionAction(Permission.AddTag, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
      val theResponse = addTagsHelper(TagCheck_File, id, request)
  	  files.index(id)
  	  theResponse
  }

  /**
   * REST endpoint: POST: removes tags.
   * Tag's (name, userId, extractor_id) tuple is used as a unique key.
   * In other words, the same tag names but diff userId or extractor_id are considered as diff tags.
   * Current implementation enforces the restriction which only allows the tags to be removed by
   * the same user or extractor.
   */
  def removeTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
      val theResponse = removeTagsHelper(TagCheck_File, id, request)
  	  files.index(id)
  	  theResponse
  }

  /**
   * REST endpoint: POST: removes all tags of a file.
   * This is a big hammer -- it does not check the userId or extractor_id and
   * forcefully remove all tags for this id.  It is mainly intended for testing.
   */
  def removeAllTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
      Logger.debug("Removing all tags for file with id: " + id)
      if (UUID.isValid(id.stringify)) {
        files.get(id) match {
          case Some(file) => {
            files.removeAllTags(id)
            files.index(id)
            Ok(Json.obj("status" -> "success"))
          }
          case None => {
            Logger.error("The file with id " + id + " is not found.")
            NotFound(toJson("The file with id " + id + " is not found."))
          }
        }
      } else {
        Logger.error("The given id " + id + " is not a valid ObjectId.")
        BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
      }
  }

  // ---------- Tags related code ends ------------------
  
  /**
  * REST endpoint: GET  api/files/:id/extracted_metadata 
  * Returns metadata extracted so far for a file with id
  * 
  */
  def extract(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    Logger.debug("Getting extract info for file with id " + id)
    if (UUID.isValid(id.stringify)) {
     files.get(id) match {
        case Some(file) =>
          val jtags = FileOP.extractTags(file)
          val jpreviews = FileOP.extractPreviews(id)
          val vdescriptors=files.getVersusMetadata(id) match {
                  											  case Some(vd)=>api.routes.Files.getVersusMetadataJSON(id).toString
                  										      case None=> ""
                  											}
          Logger.debug("jtags: " + jtags.toString)
          Logger.debug("jpreviews: " + jpreviews.toString)
          Ok(Json.obj("file_id" -> id.toString, "filename" -> file.filename, "tags" -> jtags, "previews" -> jpreviews,"versus descriptors url"->vdescriptors))
        case None => {
          val error_str = "The file with id " + id + " is not found." 
          Logger.error(error_str)
          NotFound(toJson(error_str))
        }
      }
    } else {
      val error_str ="The given id " + id + " is not a valid ObjectId." 
      Logger.error(error_str)
      BadRequest(toJson(error_str))
    }
  }

  def comment(id: UUID) = PermissionAction(Permission.AddComment, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
      request.user match {
        case Some(identity) => {
          (request.body \ "text").asOpt[String] match {
            case Some(text) => {
              val comment = new Comment(identity, text, file_id = Some(id))
              comments.insert(comment)
              files.get(id) match {
              case Some(file) =>{
                events.addSourceEvent(request.user, comment.id, comment.text, file.id, file.filename, "comment_file")
                }
              }
              files.index(id)
              Ok(comment.id.toString)
            }
            case None => {
              Logger.error("no text specified.")
              BadRequest(toJson("no text specified."))
            }
          }
        }
        case None =>
          Logger.error(("No user identity found in the request, request body: " + request.body))
          BadRequest(toJson("No user identity found in the request, request body: " + request.body))
      }
  }


  /**
   * Return whether a file is currently being processed.
   */
  def isBeingProcessed(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
      files.get(id) match {
        case Some(file) => {
          var isActivity = "false"
          extractions.findIfBeingProcessed(file.id) match {
            case false =>
            case true => isActivity = "true"
          }
          Ok(toJson(Map("isBeingProcessed" -> isActivity)))
        }
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }


  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(UUID(prv._1), prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: UUID, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId.stringify, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
        "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId).toString,
        "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString,
        "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId).toString))
    else
      toJson(Map("pv_id" -> pvId.stringify, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }
  

  def getPreviews(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
        files.get(id) match {
          case Some(file) => {

            val previewsFromDB = previews.findByFileId(file.id)
            val previewers = Previewers.findPreviewers
            //Logger.debug("Number of previews " + previews.length);
            val files = List(file)
            //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
            val previewslist = for (f <- files; if (!f.showPreviews.equals("None"))) yield {
              val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
              }
              if (pvf.length > 0) {
                (file -> pvf)
              } else {
                val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                    //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the 
                    //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
                    if (f.licenseData.isDownloadAllowed(request.user) || Permission.checkPermission(request.user, Permission.DownloadFiles, ResourceRef(ResourceRef.file, file.id))) {
                        (file.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(file.id) + "/blob", file.contentType, file.length)
                    }
                    else {
                        (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
                    }
                }
                (file -> ff)
              }
            }

            Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
          }
          case None => {
            Logger.error("Error getting file" + id);
            InternalServerError
          }
        }

    } 


    def getTechnicalMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
        files.get(id) match {
          case Some(file) => {
            val listOfMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.file, id))
              .filter(_.creator.typeOfAgent == "extractor")
              .map(JSONLD.jsonMetadataWithContext(_) \ "content")
            Ok(toJson(listOfMetadata))
          }
          case None => {
            Logger.error("Error finding file" + id);
            InternalServerError
          }
        }
    }

    def getVersusMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
        files.get(id) match {
          case Some(file) => {
             files.getVersusMetadata(id) match {
             		case Some(vd)=>{
             		    Logger.debug("versus Metadata found")
             			Ok(files.getVersusMetadata(id).get)
             		}
             		case None=>{
             		  Logger.debug("No versus Metadata found")
             			Ok("No Versus Metadata Found")
             		}
              }
          }
          case None => {
            Logger.error("Error finding file" + id);
            InternalServerError
          }
        }
    }


  def removeFile(id: UUID) = PermissionAction(Permission.DeleteFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
      files.get(id) match {
        case Some(file) => {
          events.addObjectEvent(request.user, file.id, file.filename, "delete_file")
          // notify rabbitmq
          current.plugin[RabbitmqPlugin].foreach { p =>
            val clowderurl = Utils.baseEventUrl(request)
            datasets.findByFileId(file.id).foreach{ds =>
              val dtkey = s"${p.exchange}.dataset.file.removed"
              p.extract(ExtractorMessage(file.id, file.id, clowderurl, dtkey, Map.empty, file.length.toString, ds.id, ""))
            }
          }

          //this stmt has to be before files.removeFile
          Logger.debug("Deleting file from indexes " + file.filename)
          current.plugin[VersusPlugin].foreach {        
            _.removeFromIndexes(id)        
          }
          Logger.debug("Deleting file: " + file.filename)
          files.removeFile(id)
          appConfig.incrementCount('files, -1)
          appConfig.incrementCount('bytes, -file.length)

          current.plugin[ElasticsearchPlugin].foreach {
            _.delete("data", "file", id.stringify)
          }
          //remove file from RDF triple store if triple store is used
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => {
              if (file.filename.endsWith(".xml")) {
                sqarql.removeFileFromGraphs(id, "rdfXMLGraphName")
              }
              sqarql.removeFileFromGraphs(id, "rdfCommunityGraphName")
            }
            case _ => {}
          }
          current.plugin[AdminsNotifierPlugin].foreach{
            _.sendAdminsNotification(Utils.baseUrl(request), "File","removed",id.stringify, file.filename)}
          Ok(toJson(Map("status"->"success")))
        }
        case None => Ok(toJson(Map("status" -> "success")))
      }
  }


  def updateDescription(id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, id))) (parse.json){ implicit request =>
    files.get(id) match {
      case Some(file) => {
        var description: String = null
        val aResult = (request.body \ "description").validate[String]
        aResult match {
          case s: JsSuccess[String] => {
            description = s.get
            files.updateDescription(file.id,description)
            Ok(toJson(Map("status"->"success")))
          }
          case e: JsError => {
            Logger.error("Errors: " + JsError.toFlatJson(e).toString())
            BadRequest(toJson(s"description data is missing"))
          }
        }

      }
      case None => BadRequest("No file exists with that id")
    }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchFilesUserMetadata = PermissionAction(Permission.ViewFile)(parse.json) { implicit request =>
      Logger.debug("Searching files' user metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = files.searchUserMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")

      val list = for (file <- searchQuery) yield jsonFileWithThumbnail(file)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }


  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchFilesGeneralMetadata = PermissionAction(Permission.ViewFile)(parse.json) { implicit request =>
      Logger.debug("Searching files' metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = files.searchAllMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")

      val list = for (file <- searchQuery) yield jsonFileWithThumbnail(file)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }


  def index(id: UUID) {
    files.get(id) match {
      case Some(file) => {
        current.plugin[ElasticsearchPlugin].foreach {
          _.index(SearchUtils.getElasticsearchObject(file))
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }


  def follow(id: UUID) = AuthenticatedAction {implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          files.get(id) match {
            case Some(file) => {
              events.addObjectEvent(user, id, file.filename, "follow_file")
              files.addFollower(id, loggedInUser.id)
              userService.followFile(loggedInUser.id, id)

              val recommendations = getTopRecommendations(id, loggedInUser)
              recommendations match {
                case x::xs => Ok(Json.obj("status" -> "success", "recommendations" -> recommendations))
                case Nil => Ok(Json.obj("status" -> "success"))
              }
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  def unfollow(id: UUID) = AuthenticatedAction {implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          files.get(id) match {
            case Some(file) => {
              events.addObjectEvent(user, id, file.filename, "unfollow_file")
              files.removeFollower(id, loggedInUser.id)
              userService.unfollowFile(loggedInUser.id, id)
              Ok
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  def getTopRecommendations(followeeUUID: UUID, follower: User): List[MiniEntity] = {
    val followeeModel = files.get(followeeUUID)
    followeeModel match {
      case Some(followeeModel) => {
        val sourceFollowerIDs = followeeModel.followers
        val excludeIDs = follower.followedEntities.map(typedId => typedId.id) ::: List(followeeUUID, follower.id)
        val num = play.api.Play.configuration.getInt("number_of_recommendations").getOrElse(10)
        userService.getTopRecommendations(sourceFollowerIDs, excludeIDs, num)
      }
      case None => {
        List.empty
      }
    }
  }


  def users(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    implicit val user = request.user
    val spaceTitle: String = Messages("space.title")

    var userList: List[User] = List.empty
    files.get(id) match {
      case Some(file) => {
        datasets.findByFileId(id).foreach(dataset => {
          dataset.spaces.foreach { spaceId =>
            spaces.get(spaceId) match {
              case Some(spc) => userList = spaces.getUsersInSpace(spaceId) ::: userList
              case None => NotFound(s"Error: No $spaceTitle found for $id.")
            }
          }
          userList = userList.distinct.sortBy(_.fullName.toLowerCase)
        })

        if (userList.nonEmpty) {
          Ok(Json.toJson(userList.map(user => Json.obj(
            "@context" -> Json.toJson(
              Map(
                "firstName" -> Json.toJson("http://schema.org/Person/givenName"),
                "lastName" -> Json.toJson("http://schema.org/Person/familyName"),
                "email" -> Json.toJson("http://schema.org/Person/email"),
                "affiliation" -> Json.toJson("http://schema.org/Person/affiliation")
              )
            ),
            "id" -> user.id.stringify,
            "firstName" -> user.firstName,
            "lastName" -> user.lastName,
            "fullName" -> user.fullName,
            "email" -> user.email,
            "avatar" -> user.getAvatarUrl(),
            "identityProvider" -> user.format(true)
          ))))
        }
        else NotFound(s"Error: No user found for $id.")
      }
      case None => NotFound(s"File $id not found")
    }
  }
}

object MustBreak extends Exception {}

