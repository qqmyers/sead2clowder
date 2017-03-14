package api

import java.io._
import java.net.URL
import java.security.{DigestInputStream, MessageDigest}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import api.Permission.Permission
import com.wordnik.swagger.annotations.{ApiResponse, ApiResponses, Api, ApiOperation}
import java.util.zip._
import javax.inject.{Inject, Singleton}
import com.wordnik.swagger.annotations.{Api, ApiOperation}
import controllers.{Previewers, Utils}
import jsonutils.JsonUtil
import models.{File, _}
import org.apache.commons.codec.binary.Hex
import org.json.JSONObject
import play.api.Logger
import play.api.Play.{configuration, current}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.mvc.AnyContent
import services._
import _root_.util.{FileUtils, JSONLD, License, SearchUtils}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.ListBuffer

/**
 * Dataset API.
 *
 */
@Api(value = "/datasets", listingPath = "/api-docs.json/datasets", description = "A dataset is a container for files and metadata")
@Singleton
class  Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  extractions: ExtractionService,
  metadataService: MetadataService,
  contextService: ContextLDService,
  rdfsparql: RdfSPARQLService,
  events: EventService,
  spaces: SpaceService,
  folders: FolderService,
  relations: RelationService,
  userService: UserService,
  appConfig: AppConfigurationService) extends ApiController {

  @ApiOperation(value = "Get a specific dataset",
    notes = "This will return a sepcific dataset requested",
    responseClass = "None", multiValueResponse=true, httpMethod = "GET")
  def get(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(d) => Ok(toJson(d))
      case None => BadRequest(toJson(s"Could not find dataset with id [${id.stringify}]"))
    }
  }

  @ApiOperation(value = "List all datasets the user can view",
    notes = "This will check for Permission.ViewDataset",
    responseClass = "None", multiValueResponse=true, httpMethod = "GET")
  def list(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    Ok(toJson(listDatasets(title, date, limit, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode))))
  }

  @ApiOperation(value = "List all datasets the user can edit",
    notes = "This will check for Permission.AddResourceToDataset and Permission.EditDataset",
    responseClass = "None", httpMethod = "GET")
  def listCanEdit(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
      Ok(toJson(listDatasets(title, date, limit, Set[Permission](Permission.AddResourceToDataset, Permission.EditDataset), request.user, request.user.fold(false)(_.superAdminMode))))
  }

  @ApiOperation(value = "List all datasets in the space the user can edit and thus move the file to",
    notes = "This will check for Permission.AddResourceToDataset and Permission.EditDataset",
    responseClass = "None", httpMethod = "GET")
  def listMoveFileToDataset(file_id: UUID, title: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    if (play.Play.application().configuration().getBoolean("datasetFileWithinSpace")) {
      Ok(toJson(listDatasetsInSpace(file_id, title, limit, Set[Permission](Permission.AddResourceToDataset, Permission.EditDataset), request.user, request.user.fold(false)(_.superAdminMode))))
    } else {
      Ok(toJson(listDatasets(title, None, limit, Set[Permission](Permission.AddResourceToDataset, Permission.EditDataset), request.user, request.user.fold(false)(_.superAdminMode))))
    }
  }

  /**
    * Returns list of datasets based on space restrictions and permissions. The spaceId is obtained from the file itself
    */
  private def listDatasetsInSpace(file_id: UUID, title: Option[String], limit: Int, permission: Set[Permission], user: Option[User], superAdmin: Boolean) : List[Dataset] = {
    var datasetAll = List[Dataset]()
    val datasetList = datasets.findByFileId(file_id)
    datasetList match {
      case Nil => {
        val folderList = folders.findByFileId(file_id)
        folderList match {
          case f :: fs => {
            datasets.get(f.parentDatasetId) match {
              case Some(d) => {
                if (d.spaces.isEmpty) {
                  title match {
                    case Some(t) => {
                      datasetAll = datasets.listAccess(limit, t, permission, user, superAdmin, true)
                    }
                    case None => {
                      datasetAll = datasets.listAccess(limit, permission, user, superAdmin, true)
                    }
                  }
                } else {
                  for (sid <- d.spaces) {
                    title match {
                      case Some(t) => {
                        //merge two lists, both with dataset objects from different spaces
                        datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, t, permission, sid.toString(), user, superAdmin, true)
                      }
                      case None => {
                        datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, permission, sid.toString(), user, superAdmin, true)
                      }
                    }
                  }
                }
              }
              case None =>
            }
          }
        }
      }
      case x :: xs => {
        if (x.spaces.isEmpty) {
          title match {
            case Some(t) => {
              datasetAll = datasets.listAccess(limit, t, permission, user, superAdmin, true)
            }
            case None => {
              datasetAll = datasets.listAccess(limit, permission, user, superAdmin, true)
            }
          }
        } else {
          for (sid <- x.spaces) {
            title match {
              case Some(t) => {
                datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, t, permission, sid.toString(), user, superAdmin, true)
              }
              case None => {
                datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, permission, sid.toString(), user, superAdmin, true)
              }
            }
          }
        }
      }
    }
    datasetAll.distinct
  }

  /**
    * Returns list of datasets based on parameters and permissions.
    */
  private def listDatasets(title: Option[String], date: Option[String], limit: Int, permission: Set[Permission], user: Option[User], superAdmin: Boolean) : List[Dataset] = {
    (title, date) match {
      case (Some(t), Some(d)) => {
        datasets.listAccess(d, true, limit, t, permission, user, superAdmin, true)
      }
      case (Some(t), None) => {
        datasets.listAccess(limit, t, permission, user, superAdmin, true)
      }
      case (None, Some(d)) => {
        datasets.listAccess(d, true, limit, permission, user, superAdmin, true)
      }
      case (None, None) => {
        datasets.listAccess(limit, permission, user, superAdmin, true)
      }
    }
  }

  /**
    * List all datasets outside a collection.
    */
  def listOutsideCollection(collectionId: UUID) = PrivateServerAction { implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val list = for (dataset <- datasets.listAccess(0, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), false); if (!datasets.isInCollection(dataset, collection)))
          yield dataset
        Ok(toJson(list))
      }
      case None => {
        val list = datasets.listAccess(0, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), false)
        Ok(toJson(list))
      }
    }
  }

  /**
    * Create new dataset. name, file_id are required, description and space,  are optional. If the space & file_id is wrong, refuse the request
    */
  @ApiOperation(value = "Create new dataset",
    notes = "New dataset containing one existing file, based on values of fields in attached JSON. Returns dataset id as JSON object.",
    responseClass = "None", httpMethod = "POST")
  def createDataset() = PermissionAction(Permission.CreateDataset)(parse.json) { implicit request =>
    Logger.debug("--- API Creating new dataset ----")
    (request.body \ "name").asOpt[String].map { name =>
      val description = (request.body \ "description").asOpt[String].getOrElse("")

      var d : Dataset = null
      implicit val user = request.user
      user match {
        case Some(identity) => {
          (request.body \ "space").asOpt[String] match {
            case None | Some("default") => d = Dataset(name=name,description=description, created=new Date(), author=identity, licenseData = License.fromAppConfig())
            case Some(spaceId) =>
              spaces.get(UUID(spaceId)) match {
                case Some(s) => d = Dataset(name=name,description=description, created=new Date(), author=identity, licenseData = License.fromAppConfig(), spaces = List(UUID(spaceId)))
                case None => BadRequest(toJson("Bad space = " + spaceId))
              }
          }
        }
        case None => InternalServerError("User Not found")
      }
      appConfig.incrementCount('datasets, 1)

      //event will be added whether creation is success.
      events.addObjectEvent(request.user, d.id, d.name, "create_dataset")
      datasets.index(d.id)

      (request.body \ "file_id").asOpt[String] match {
        case Some(file_id) => {
          files.get(UUID(file_id)) match {
            case Some(file) =>
              datasets.insert(d) match {
                case Some(id) => {
                  d.spaces.map( spaceId => spaces.get(spaceId)).flatten.map{ s =>
                    spaces.addDataset(d.id, s.id)
                    events.addSourceEvent(request.user, d.id, d.name, s.id, s.name, "add_dataset_space")
                  }
                  attachExistingFileHelper(UUID(id), file.id, d, file, request.user)
                  files.index(UUID(file_id))
                  if (!file.xmlMetadata.isEmpty) {
                    val xmlToJSON = files.getXMLMetadataJSON(UUID(file_id))
                    datasets.addXMLMetadata(UUID(id), UUID(file_id), xmlToJSON)
                    current.plugin[ElasticsearchPlugin].foreach {
                      _.index(SearchUtils.getElasticsearchObject(d))
                    }
                  } else {
                    current.plugin[ElasticsearchPlugin].foreach {
                      _.index(SearchUtils.getElasticsearchObject(d))
                    }
                  }

                  current.plugin[AdminsNotifierPlugin].foreach {
                    _.sendAdminsNotification(Utils.baseUrl(request), "Dataset", "added", id, name)
                  }


                  Ok(toJson(Map("id" -> id)))
                }
                case None => Ok(toJson(Map("status" -> "error")))
              }
            case None => BadRequest(toJson("Bad file_id = " + file_id))

          }
        }
        case None => BadRequest(toJson("Missing parameter [file_id]"))
      }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  /**
    * Create new dataset with no file required. However if there are comma separated file IDs passed in, add all of those as existing
    * files. This is to facilitate multi-file-uploader usage for new files, as well as to allow multiple existing files to be
    * added as part of dataset creation.
    *
    * A JSON document is the payload for this endpoint. Required elements are name, description, and space. Optional element is
    * existingfiles, which will be a comma separated String of existing file IDs to be added to the new dataset.
    */
  @ApiOperation(value = "Create new dataset with no file",
    notes = "New dataset requiring zero files based on values of fields in attached JSON. Returns dataset id as JSON object. Requires name, description, and space. Optional list of existing file ids to add.",
    responseClass = "None", httpMethod = "POST")
  def createEmptyDataset() = PermissionAction(Permission.CreateDataset)(parse.json) { implicit request =>
    (request.body \ "name").asOpt[String].map { name =>

      val description = (request.body \ "description").asOpt[String].getOrElse("")
      val access =
        if(play.Play.application().configuration().getBoolean("verifySpaces")){
           //verifySpaces == true && access set to trial if not specified otherwise
           (request.body \ "access").asOpt[String].getOrElse(DatasetStatus.TRIAL.toString)
        } else {
          (request.body \ "access").asOpt[String].getOrElse(DatasetStatus.DEFAULT.toString)
        }
        var d : Dataset = null
        implicit val user = request.user
        user match {
          case Some(identity) => {
            (request.body \ "space").asOpt[List[String]] match {
              case None | Some(List("default"))=> {
                d = Dataset(name = name, description = description, created = new Date(), author = identity, licenseData = License.fromAppConfig(), status = access)
              }

              case Some(space) => {
                var spaceList: List[UUID] = List.empty
                space.map {
                  aSpace => if (spaces.get(UUID(aSpace)).isDefined) {
                    spaceList = UUID(aSpace) :: spaceList

                  } else {
                    BadRequest(toJson("Bad space = " + aSpace))
                  }
                }
                d = Dataset(name = name, description = description, created = new Date(), author = identity, licenseData = License.fromAppConfig(), spaces = spaceList, status = access)
              }

            }
          }
          case None => InternalServerError("User Not found")
        }
        events.addObjectEvent(request.user, d.id, d.name, "create_dataset")

        datasets.insert(d) match {
          case Some(id) => {
            //In this case, the dataset has been created and inserted. Now notify the space service and check
            //for the presence of existing files.
            appConfig.incrementCount('datasets, 1)

            datasets.index(d.id)
            Logger.debug("About to call addDataset on spaces service")
            d.spaces.map( spaceId => spaces.get(spaceId)).flatten.map{ s =>
              spaces.addDataset(d.id, s.id)
              events.addSourceEvent(request.user, d.id, d.name, s.id, s.name, "add_dataset_space")

            }
            //Add this dataset to a collection if needed
            (request.body \ "collection").asOpt[List[String]] match {
              case None | Some(List("default"))=>
              case Some(collectionList) => {
                collectionList.map{c => collections.addDataset(UUID(c), d.id)}
              }
            }
            //Below call is not what is needed? That already does what we are doing in the Dataset constructor...
            //Items from space model still missing. New API will be needed to update it most likely.
            (request.body \ "existingfiles").asOpt[String].map { fileString =>
              var idArray = fileString.split(",").map(_.trim())
              for (anId <- idArray) {
                datasets.get(UUID(id)) match {
                  case Some(dataset) => {
                    files.get(UUID(anId)) match {
                      case Some(file) => {
                        attachExistingFileHelper(UUID(id), UUID(anId), dataset, file, request.user)
                        Ok(toJson(Map("status" -> "success")))
                      }
                      case None => {
                        Logger.error("Error getting file" + anId)
                        BadRequest(toJson(s"The given file id $anId is not a valid ObjectId."))
                      }
                    }
                  }
                  case None => {
                    Logger.error("Error getting dataset" + id)
                    BadRequest(toJson(s"The given dataset id $id is not a valid ObjectId."))
                  }
                }
              }
              Ok(toJson(Map("id" -> id)))
            }.getOrElse(Ok(toJson(Map("id" -> id))))
          }
          case None => Ok(toJson(Map("status" -> "error")))
        }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  /**
    * Create new dataset with no file required. However if there are comma separated file IDs passed in, add all of those as existing
    * files. This is to facilitate multi-file-uploader usage for new files, as well as to allow multiple existing files to be
    * added as part of dataset creation.
    *
    * A JSON document is the payload for this endpoint. Required elements are name and description. Optional element is existingfiles,
    * which will be a comma separated String of existing file IDs to be added to the new dataset.
    */
  @ApiOperation(value = "Attach multiple files to an existing dataset",
    notes = "Add multiple files, by ID, to a dataset that is already in the system. Requires file ids and dataset id.",
    responseClass = "None", httpMethod = "POST")
  def attachMultipleFiles() = PermissionAction(Permission.AddResourceToDataset)(parse.json) { implicit request =>
    (request.body \ "datasetid").asOpt[String].map { dsId =>
      (request.body \ "existingfiles").asOpt[String].map { fileString =>
        var idArray = fileString.split(",").map(_.trim())
        for (anId <- idArray) {
          datasets.get(UUID(dsId)) match {
            case Some(dataset) => {
              files.get(UUID(anId)) match {
                case Some(file) => {
                  attachExistingFileHelper(UUID(dsId), UUID(anId), dataset, file, request.user)
                  Ok(toJson(Map("status" -> "success")))
                }
                case None => {
                  Logger.error("Error getting file" + anId)
                  BadRequest(toJson(s"The given file id $anId is not a valid ObjectId."))
                }
              }
            }
            case None => {
              Logger.error("Error getting dataset" + dsId)
              BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
            }
          }
        }
        Ok(toJson(Map("id" -> dsId)))
      }.getOrElse(BadRequest(toJson("Missing parameter [existingfiles]")))
    }.getOrElse(BadRequest(toJson("Missing parameter [datasetid]")))
  }

  /**
    * Reindex the given dataset, if recursive is set to true it will
    * also reindex all files in that dataset.
    */
  @ApiOperation(value = "Reindex a dataset",
    notes = "Reindex the existing dataset, if recursive is set to true if will also reindex all files in that dataset.",
    httpMethod = "GET")
  def reindex(id: UUID, recursive: Boolean) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(ds) => {
        current.plugin[ElasticsearchPlugin].foreach {
          _.index(ds, recursive)
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case None => {
        Logger.error("Error getting dataset" + id)
        BadRequest(toJson(s"The given dataset id $id is not a valid ObjectId."))
      }
    }
  }

  /**
    * Functionality broken out from attachExistingFile, in order to allow the core work of file attachment to be called from
    * multiple API endpoints.
    *
    * @param dsId A UUID that specifies the dataset that will be modified
    * @param fileId A UUID that specifies the file to attach to the dataset
    * @param dataset Reference to the model of the dataset that is specified
    * @param file Reference to the model of the file that is specified
    */
  def attachExistingFileHelper(dsId: UUID, fileId: UUID, dataset: Dataset, file: File, user: Option[User]) = {
    if (!files.isInDataset(file, dataset)) {
      datasets.addFile(dsId, file)
      events.addSourceEvent(user , file.id, file.filename, dataset.id, dataset.name, "attach_file_dataset")
      files.index(fileId)
      if (!file.xmlMetadata.isEmpty){
        datasets.index(dsId)
      }

      if(dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
        datasets.updateThumbnail(dataset.id, UUID(file.thumbnail_id.get))

        for(collectionId <- dataset.collections){
          collections.get(collectionId) match{
            case Some(collection) =>{
              if(collection.thumbnail_id.isEmpty){
                collections.updateThumbnail(collection.id, UUID(file.thumbnail_id.get))
              }
            }
            case None=>Logger.debug(s"No collection found with id $collectionId")
          }
        }
      }
        //add file to RDF triple store if triple store is used
        if (file.filename.endsWith(".xml")) {
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => rdfsparql.linkFileToDataset(fileId, dsId)
            case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
          }
        }
        Logger.debug("----- Adding file to dataset completed")
      } else {
          Logger.debug("File was already in dataset.")
      }
  }

  @ApiOperation(value = "Attach existing file to dataset",
    notes = "If the file is an XML metadata file, the metadata are added to the dataset.",
    responseClass = "None", httpMethod = "POST")
  def attachExistingFile(dsId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dsId))) { implicit request =>
    datasets.get(dsId) match {
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            attachExistingFileHelper(dsId, fileId, dataset, file, request.user)
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
            Logger.error("Error getting file" + fileId)
            BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
          }
        }
      }
      case None => {
        Logger.error("Error getting dataset" + dsId)
        BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
      }
    }
  }

  @ApiOperation(value = "Detach file from dataset",
    notes = "File is not deleted, only separated from the selected dataset. If the file is an XML metadata file, the metadata are removed from the dataset.",
    responseClass = "None", httpMethod = "POST")
  def detachFile(datasetId: UUID, fileId: UUID, ignoreNotFound: String) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    datasets.get(datasetId) match{
      case Some(dataset) => {
        detachFileHelper(datasetId, fileId, dataset, request.user)
      }
      case None => {
        ignoreNotFound match {
          case "True" => Ok(toJson(Map("status" -> "success")))
          case "False" => Logger.error(s"Error getting dataset $datasetId"); InternalServerError
        }
      }
    }
  }

  /**
    * Utility function to consolidate the utility portions of the detach file functionality
    * so that it can be easily called from multiple API operations.
    *
    * @param datasetId The id of the dataset that a file is being detached from
    * @param fileId The id of the file to detach from the dataset
    * @param dataset The reference to the model of the dataset being operated on
    *
    */
  def detachFileHelper(datasetId: UUID, fileId: UUID, dataset: models.Dataset, user: Option[User]) = {
    files.get(fileId) match {
      case Some(file) => {
        if(files.isInDataset(file, dataset)){
          //remove file from dataset
          datasets.removeFile(dataset.id, file.id)
          events.addSourceEvent(user , file.id, file.filename, dataset.id, dataset.name, "detach_file_dataset")
          files.index(fileId)
          if (!file.xmlMetadata.isEmpty)
            datasets.index(datasetId)

          Logger.debug("----- Removing a file from dataset completed")

          if(!dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
            if(dataset.thumbnail_id.get == file.thumbnail_id.get){
              datasets.createThumbnail(dataset.id)

              for(collectionId <- dataset.collections){
                collections.get(collectionId) match{
                  case Some(collection) =>{
                    if(!collection.thumbnail_id.isEmpty){
                      if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
                        collections.createThumbnail(collection.id)
                      }
                    }
                  }
                  case None=>{}
                }
              }
            }
          }

          //remove link between dataset and file from RDF triple store if triple store is used
          if (file.filename.endsWith(".xml")) {
            configuration.getString("userdfSPARQLStore").getOrElse("no") match {
              case "yes" => rdfsparql.detachFileFromDataset(fileId, datasetId)
              case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
            }
          }
        }
        else  Logger.debug("----- File was already out of the dataset.")
        Ok(toJson(Map("status" -> "success")))
      }
      case None => {
        Logger.debug("----- detach helper NONE case")
        Ok(toJson(Map("status" -> "success")))
      }
    }
  }

  @ApiOperation(value = "Attach existing file to a new dataset and delete it from the old one",
    notes = "If the file is an XML metadata file, the metadata are added to the dataset.",
    responseClass = "None", httpMethod = "POST")
  def moveFileBetweenDatasets(datasetId: UUID, toDatasetId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    datasets.get(datasetId) match {
      case Some (dataset) => {
        datasets.get (toDatasetId) match {
          case Some (toDataset) => {
            files.get (fileId) match {
              case Some (file) => {
                attachExistingFileHelper (toDatasetId, fileId, toDataset, file, request.user)
                detachFileHelper(datasetId, fileId, dataset, request.user)
                Logger.debug ("----- Successfully moved File between datasets.")
                Ok (toJson (Map ("status" -> "success") ) )
              }
              case None => {
                Logger.error ("Error getting file" + fileId)
                BadRequest (toJson (s"The given file id $fileId is not a valid ObjectId.") )
              }
            }
          }
          case None => {
            Logger.error ("Error getting dataset" + toDatasetId)
            BadRequest (toJson (s"The given dataset id $toDatasetId is not a valid ObjectId.") )
          }
        }
      }
      case None => {
        Logger.error ("Error getting dataset" + datasetId)
        BadRequest (toJson (s"The given dataset id $datasetId is not a valid ObjectId.") )
      }
    }
  }
  //////////////////

  @ApiOperation(value = "List all datasets in a collection", notes = "Returns list of datasets and descriptions.", responseClass = "None", httpMethod = "GET")
  def listInCollection(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    Ok(toJson(datasets.listCollection(collectionId.stringify, request.user)))
  }

  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    Logger.debug(s"Adding metadata to dataset $id")
    //datasets.addMetadata(id, Json.stringify(request.body))

    datasets.get(id) match {
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
        val attachedTo = ResourceRef(ResourceRef.dataset, id)
        val version = None
        val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
          json, version)

        //add metadata to mongo
        metadataService.addMetadata(metadata)
        val mdMap = metadata.getExtractionSummary

        //send RabbitMQ message
        current.plugin[RabbitmqPlugin].foreach { p =>
          val dtkey = s"${p.exchange}.metadata.added"
          p.extract(ExtractorMessage(UUID(""), UUID(""), controllers.Utils.baseUrl(request), dtkey, mdMap, "", metadata.attachedTo.id, ""))
        }


        datasets.index(id)
        Ok(toJson(Map("status" -> "success")))
      }
      case None => Logger.error(s"Error getting dataset $id"); NotFound
    }
  }

  /**
    * Add metadata in JSON-LD format.
    */
  @ApiOperation(value = "Add JSON-LD metadata to the database.",
    notes = "Metadata in attached JSON-LD object will be added to metadata Mongo db collection.",
    responseClass = "None", httpMethod = "POST")
  def addMetadataJsonLD(id: UUID) =
     PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
        datasets.get(id) match {
          case Some(x) => {
            val json = request.body
            // parse request for JSON-LD model
            var model: RDFModel = null
            json.validate[RDFModel] match {
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatForm(e))
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
    
                    // check if the context is a URL to external endpoint
                    val contextURL: Option[URL] = (json \ "@context").asOpt[String].map(new URL(_))
    
                    // check if context is a JSON-LD document
                    val contextID: Option[UUID] = (json \ "@context").asOpt[JsObject]
                      .map(contextService.addContext(new JsString("context name"), _))
    
                    // when the new metadata is added
                    val createdAt = new Date()
    
                    //parse the rest of the request to create a new models.Metadata object
                    val attachedTo = ResourceRef(ResourceRef.dataset, id)
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
                      p.extract(ExtractorMessage(UUID(""), UUID(""), controllers.Utils.baseUrl(request), dtkey, mdMap, "", metadata.attachedTo.id, ""))
                    }

                    datasets.index(id)
                    Ok(toJson("Metadata successfully added to db"))
    
                  }
                  case e: JsError => {
                    Logger.error("Error getting creator");
                    BadRequest(toJson(s"Creator data is missing or incorrect."))
                  }
                }
              }
            }
          }
          case None => Logger.error(s"Error getting dataset $id"); NotFound
        }
      }


  @ApiOperation(value="Retrieve available metadata definitions for a dataset. It is an aggregation of the metadata that a space belongs to.",
    responseClass="None", httpMethod="GET")
  def getMetadataDefinitions(id: UUID, currentSpace: Option[String]) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val metadataDefinitions = collection.mutable.HashSet[models.MetadataDefinition]()
        var spacesToCheck = List.empty[UUID]
        currentSpace match {
          case Some(spaceId) => {
            spaces.get(UUID(spaceId)) match {
              case Some(space) => spacesToCheck = List(space.id)
              case None => spacesToCheck = dataset.spaces
            }
          }
          case None => {
            spacesToCheck = dataset.spaces
          }
        }
        spacesToCheck.foreach { spaceId =>
          spaces.get(spaceId) match {
            case Some(space) => metadataService.getDefinitions(Some(space.id)).foreach{definition => metadataDefinitions += definition}
            case None =>
          }
        }
        if(dataset.spaces.length == 0) {
          metadataService.getDefinitions().foreach{definition => metadataDefinitions += definition}
        }
        Ok(toJson(metadataDefinitions.toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )))
      }
      case None => BadRequest(toJson("The requested dataset does not exist"))
    }
  }

 @ApiOperation(value = "Retrieve metadata as JSON-LD",
    notes = "Get metadata of the dataset object as JSON-LD.",
    responseClass = "None", httpMethod = "GET")
  def getMetadataJsonLD(id: UUID, extFilter: Option[String]) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        //get metadata and also fetch context information
        val listOfMetadata = extFilter match {
          case Some(f) => metadataService.getExtractedMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id), f)
                                    .map(JSONLD.jsonMetadataWithContext(_))
          case None => metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))
                                    .map(JSONLD.jsonMetadataWithContext(_))
        }
        Ok(toJson(listOfMetadata))
      }
      case None => {
        Logger.error("Error getting dataset  " + id);
        BadRequest(toJson("Error getting dataset  " + id))
      }
    }
  }

  @ApiOperation(value = "Remove JSON-LD metadata, filtered by extractor if necessary",
    notes = "Remove JSON-LD metadata from dataset object",
    responseClass = "None", httpMethod = "GET")
  def removeMetadataJsonLD(id: UUID, extFilter: Option[String]) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val num_removed = extFilter match {
          case Some(f) => metadataService.removeMetadataByAttachToAndExtractor(ResourceRef(ResourceRef.dataset, id), f)
          case None => metadataService.removeMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))
        }

        // send extractor message after attached to resource
        current.plugin[RabbitmqPlugin].foreach { p =>
          val dtkey = s"${p.exchange}.metadata.removed"
          p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, Map[String, Any](
            "resourceType"->ResourceRef.dataset,
            "resourceId"->id.toString), "", id, ""))
        }

        Ok(toJson(Map("status" -> "success", "count" -> num_removed.toString)))
      }
      case None => {
        Logger.error("Error getting dataset  " + id);
        BadRequest(toJson("Error getting dataset  " + id))
      }
    }
  }

  @ApiOperation(value = "Add user-generated metadata to dataset",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addUserMetadata(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    Logger.debug(s"Adding user metadata to dataset $id")
    datasets.addUserMetadata(id, Json.stringify(request.body))

    datasets.get(id) match {
      case Some(dataset) => {
        events.addObjectEvent(user, id, dataset.name, "addMetadata_dataset")
      }
    }

    datasets.index(id)
    configuration.getString("userdfSPARQLStore").getOrElse("no") match {
      case "yes" => datasets.setUserMetadataWasModified(id, true)
      case _ => Logger.debug("userdfSPARQLStore not enabled")
    }
    Ok(toJson(Map("status" -> "success")))
  }

  def datasetFilesGetIdByDatasetAndFilename(datasetId: UUID, filename: String): Option[String] = {
    datasets.get(datasetId) match {
      case Some(dataset) => {
        for (fileId <- dataset.files) {
          files.get(fileId) match {
            case Some(file) => {
              if (file.filename.equals(filename)) {
                return Some(file.id.toString)
              }
            }
            case None =>  Logger.error(s"Error getting file $fileId.")
          }

        }
        Logger.error(s"File does not exist in dataset $datasetId.")
        None
      }
      case None => Logger.error(s"Error getting dataset $datasetId."); None
    }
  }

  @ApiOperation(value = "List files in dataset",
    notes = "Datasets and descriptions.",
    responseClass = "None", httpMethod = "GET")
  def datasetFilesList(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val list: List[JsValue]= dataset.files.map(fileId => files.get(fileId) match {
          case Some(file) => {
            val serveradmin = request.user match {
              case Some(u) => u.serverAdmin
              case None => false
            }
            jsonFile(file, serveradmin)
          }
          case None => Logger.error(s"Error getting File $fileId")
        }).asInstanceOf[List[JsValue]]
        Ok(toJson(list))
      }
      case None => Logger.error("Error getting dataset" + id); InternalServerError
    }
  }

  @ApiOperation(value = "List files in dataset and within folders",
    notes = "Datasets and descriptions.",
    responseClass = "None", httpMethod = "GET")
  def datasetAllFilesList(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val listFiles: List[JsValue]= dataset.files.map(fileId => files.get(fileId) match {
          case Some(file) => {
            val serveradmin = request.user match {
              case Some(u) => u.serverAdmin
              case None => false
            }
            jsonFile(file, serveradmin)
          }
          case None => Logger.error(s"Error getting File $fileId")
        }).asInstanceOf[List[JsValue]]
        val serveradmin = request.user match {
          case Some(u) => u.serverAdmin
          case None => false
        }
        val list = listFiles ++ getFilesWithinFolders(id, serveradmin)
        Ok(toJson(list))
      }
      case None => Logger.error("Error getting dataset" + id); InternalServerError
    }
  }

  @ApiOperation(value = "Upload files and attach to given dataset",
    notes = "This will take a list of url or path objects that point to files that will be ingested and added to this dataset.",
    responseClass = "None", httpMethod = "POST")
  def uploadToDatasetFile(dataset_id: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.multipartFormData) { implicit request =>
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val uploadedFiles = FileUtils.uploadFilesMultipart(request, Some(dataset))
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

  @ApiOperation(value = "Upload files and attach to given dataset",
    notes = "This will take a form of file objects that are added to this dataset. This can also add metadata at the same time.",
    responseClass = "None", httpMethod = "POST")
  def uploadToDatasetJSON(dataset_id: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.json) { implicit request =>
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val uploadedFiles = FileUtils.uploadFilesJSON(request, Some(dataset))
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


  private def getFilesWithinFolders(id: UUID, serveradmin: Boolean = false): List[JsValue] = {
    val output = new ListBuffer[JsValue]()
    datasets.get(id) match {
      case Some(dataset) => {
        val childFolders = folders.findByParentDatasetId(id)
        childFolders.map {
          folder =>
            folder.files.map {
              fileId => files.get(fileId) match {
                case Some(file) => output += jsonFile(file, serveradmin)
                case None => Logger.error(s"Error getting file $fileId")
              }
            }
        }
      }
      case None => Logger.error(s"Error getting dataset $id")
    }
    output.toList.asInstanceOf[List[JsValue]]
  }

  def jsonFile(file: models.File, serverAdmin: Boolean = false): JsValue = {
    val defaultMap = Map(
      "id" -> file.id.toString,
      "filename" -> file.filename,
      "contentType" -> file.contentType,
      "date-created" -> file.uploadDate.toString(),
      "size" -> file.length.toString)

    // Only include filepath if using DiskByte storage and user is serverAdmin
    val jsonMap = file.loader match {
      case "services.filesystem.DiskByteStorageService" => {
        if (serverAdmin)
          Map(
            "id" -> file.id.toString,
            "filename" -> file.filename,
            "filepath" -> file.loader_id,
            "contentType" -> file.contentType,
            "date-created" -> file.uploadDate.toString(),
            "size" -> file.length.toString)
        else
          defaultMap
      }
      case _ => defaultMap
    }
    toJson(jsonMap)
  }

  //Update Dataset Information code starts

  /**
    * REST endpoint: POST: update the administrative information associated with a specific Dataset
    *
    *  Takes one arg, id:
    *
    *  id, the UUID associated with this dataset
    *
    *  The data contained in the request body will contain data to be updated associated by the following String key-value pairs:
    *
    *  description -> The text for the updated description for the dataset
    *  name -> The text for the updated name for this dataset
    *
    *  Currently description and owner are the only fields that can be modified, however this api is extensible enough to add other existing
    *  fields, or new fields, in the future.
    *
    */
  @ApiOperation(value = "Update dataset administrative information",
    notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pairs for name and description.",
    responseClass = "None", httpMethod = "POST")
  def updateInformation(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var description: String = null;
      var name: String = null;

      var aResult: JsResult[String] = (request.body \ "description").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          description = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"description data is missing."))
        }
      }

      aResult = (request.body \ "name").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          name = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"name data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. Args are $description and $name")

      datasets.updateInformation(id, description, name)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  @ApiOperation(value = "Update dataset name",
    notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pair for name.",
    responseClass = "None", httpMethod = "POST")
  def updateName(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var name: String = null

      val aResult = (request.body \ "name").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          name = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"name data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New name is: $name")

      datasets.updateName(id, name)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
          datasets.index(id)
          // file in this dataset need to be indexed as well since dataset name will show in file list
          dataset.files.map(files.index(_))
          folders.findByParentDatasetId(id).map(_.files).flatten.map(files.index(_))
        }
      }
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  @ApiOperation(value = "Update dataset description.",
    notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pair for description.",
    responseClass = "None", httpMethod = "POST")
  def updateDescription(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var description: String = null

      val aResult: JsResult[String] = (request.body \ "description").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          description = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"description data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New description is:  $description ")

      datasets.updateDescription(id, description)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, Update Dataset Information code

    @ApiOperation(value = "Add a creator to the Dataset's list of Creators.",
    notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pair for creator.",
    responseClass = "None", httpMethod = "POST")
  def addCreator(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var creator: String = null

      val aResult: JsResult[String] = (request.body \ "creator").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          creator = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"creator data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New creator is:  $creator ")

      datasets.addCreator(id, creator)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, Update Dataset Information code  
    
    @ApiOperation(value = "Remove a creator from the Dataset's list of Creators.",
    
    notes = "Takes the UUID of the dataset and the entry to delete (a String).",
    responseClass = "None", httpMethod = "DELETE")
  def removeCreator(id: UUID, creator: String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      Logger.debug(s"Remove Creator for dataset with id  $id. :  $creator ")

      datasets.removeCreator(id, creator)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, removeCreator  
    
    @ApiOperation(value = "Move a creator in a Dataset's list of creators.",
  
    notes = "Takes the UUID of the dataset, the creator to move (a String) and the new position of the creator in the overall list of creators.",
    responseClass = "None", httpMethod = "PUT")
  def moveCreator(id: UUID, creator: String, newPos: Int) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {
      
      Logger.debug(s"Move Creator for dataset with id  $id. :  $creator  to $newPos")
      datasets.moveCreator(id, creator, newPos)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, move Creator
  
  
  //Update License code
  /**
    * REST endpoint: POST: update the license data associated with a specific Dataset
    *
    *  Takes one arg, id:
    *
    *  id, the UUID associated with this dataset
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
  @ApiOperation(value = "Update license information to a dataset",
    notes = "Takes four arguments, all Strings. licenseType, rightsHolder, licenseText, licenseUrl",
    responseClass = "None", httpMethod = "POST")
  def updateLicense(id: UUID) = PermissionAction(Permission.EditLicense, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
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

      Logger.debug(s"updateLicense for dataset with id  $id. Args are $licenseType, $rightsHolder, $licenseText, $licenseUrl, $allowDownload")

      datasets.updateLicense(id, licenseType, rightsHolder, licenseText, licenseUrl, allowDownload)
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
    * REST endpoint: GET: get the tag data associated with this section.
    * Returns a JSON object of multiple fields.
    * One returned field is "tags", containing a list of string values.
    */
  @ApiOperation(value = "Get the tags associated with this dataset", notes = "Returns a JSON object of multiple fields", responseClass = "None", httpMethod = "GET")
  def getTags(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    Logger.debug(s"Getting tags for dataset with id  $id.")
    /* Found in testing: given an invalid ObjectId, a runtime exception
     * ("IllegalArgumentException: invalid ObjectId") occurs.  So check it first.
     */
    if (UUID.isValid(id.stringify)) {
      datasets.get(id) match {
        case Some(dataset) =>
          Ok(Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "tags" -> Json.toJson(dataset.tags.map(_.name))))
        case None => {
          Logger.error(s"The dataset with id $id is not found.")
          NotFound(toJson(s"The dataset with id $id is not found."))
        }
      }
    } else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  @ApiOperation(value = "Remove tag of dataset",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def removeTag(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    Logger.debug("Removing tag " + request.body)
    request.body.\("tagId").asOpt[String].map {
      tagId =>
        Logger.debug(s"Removing $tagId from $id.")
        datasets.removeTag(id, UUID(tagId))
        datasets.index(id)
    }
    Ok(toJson(""))
  }

  /**
    * REST endpoint: POST: Add tags to a dataset.
    * Requires that the request body contains a "tags" field of List[String] type.
    */
  @ApiOperation(value = "Add tags to dataset",
    notes = "Requires that the request body contains a 'tags' field of List[String] type.",
    responseClass = "None", httpMethod = "POST")
  def addTags(id: UUID) = PermissionAction(Permission.AddTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    addTagsHelper(TagCheck_Dataset, id, request)
  }

  /**
    * REST endpoint: POST: remove tags.
    * Requires that the request body contains a "tags" field of List[String] type.
    */
  @ApiOperation(value = "Remove tags of dataset",
    notes = "Requires that the request body contains a 'tags' field of List[String] type.",
    responseClass = "None", httpMethod = "POST")
  def removeTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    removeTagsHelper(TagCheck_Dataset, id, request)
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
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: adding the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.addTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => {
          datasets.addTags(id, userOpt, extractorOpt, tagsCleaned)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(request.user, id, dataset.name, "add_tags_dataset")
            }
          }
          datasets.index(id)
        }
        case TagCheck_Section => sections.addTags(id, userOpt, extractorOpt, tagsCleaned)
      }
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

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: removing the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.removeTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => {
          datasets.removeTags(id, userOpt, extractorOpt, tagsCleaned)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(request.user, id, dataset.name, "remove_tags_dataset")
            }
          }
          datasets.index(id)

        }

        case TagCheck_Section => sections.removeTags(id, userOpt, extractorOpt, tagsCleaned)
      }
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

  // TODO: move helper methods to standalone service
  val USERID_ANONYMOUS = "anonymous"

  // Helper class and function to check for error conditions for tags.
  class TagCheck {
    var error_str: String = ""
    var not_found: Boolean = false
    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var tags: Option[List[String]] = None
  }

  /*
  *  Helper function to check for error conditions.
  *  Input parameters:
  *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
  *      id:       the id in the original addTags call
  *      request:  the request in the original addTags call
  *  Returns:
  *      tagCheck: a TagCheck object, containing the error checking results:
  *
  *      If error_str == "", then no error is found;
  *      otherwise, it contains the cause of the error.
  *      not_found is one of the error conditions, meaning the object with
  *      the given id is not found in the DB.
  *      userOpt, extractorOpt and tags are set according to the request's content,
  *      and will remain None if they are not specified in the request.
  *      We change userOpt from its default None value, only if the userId
  *      is not USERID_ANONYMOUS.  The use case for this is the extractors
  *      posting to the REST API -- they'll use the commKey to post, and the original
  *      userId of these posts is USERID_ANONYMOUS -- in this case, we'd like to
  *      record the extractor_id, but omit the userId field, so we leave userOpt as None.
  */
  def checkErrorsForTag(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): TagCheck = {
    val userObj = request.user
    Logger.debug("checkErrorsForTag: user id: " + userObj.get.identityId.userId + ", user.firstName: " + userObj.get.firstName
      + ", user.LastName: " + userObj.get.lastName + ", user.fullName: " + userObj.get.fullName)
    val userId = userObj.get.identityId.userId
    if (USERID_ANONYMOUS == userId) {
      Logger.debug("checkErrorsForTag: The user id is \"anonymous\".")
    }

    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var error_str = ""
    var not_found = false
    val tags = request.body.\("tags").asOpt[List[String]]

    if (tags.isEmpty) {
      error_str = "No \"tags\" specified, request.body: " + request.body.toString
    } else if (!UUID.isValid(id.stringify)) {
      error_str = "The given id " + id + " is not a valid ObjectId."
    } else {
      obj_type match {
        case TagCheck_File => not_found = files.get(id).isEmpty
        case TagCheck_Dataset => not_found = datasets.get(id).isEmpty
        case TagCheck_Section => not_found = sections.get(id).isEmpty
        case _ => error_str = "Only file/dataset/section is supported in checkErrorsForTag()."
      }
      if (not_found) {
        error_str = s"The $obj_type with id $id is not found"
      }
    }
    if ("" == error_str) {
      if (USERID_ANONYMOUS == userId) {
        val eid = request.body.\("extractor_id").asOpt[String]
        eid match {
          case Some(extractor_id) => extractorOpt = eid
          case None => error_str = "No \"extractor_id\" specified, request.body: " + request.body.toString
        }
      } else {
        userOpt = Option(userId)
      }
    }
    val tagCheck = new TagCheck
    tagCheck.error_str = error_str
    tagCheck.not_found = not_found
    tagCheck.userOpt = userOpt
    tagCheck.extractorOpt = extractorOpt
    tagCheck.tags = tags
    tagCheck
  }

  /**
    * REST endpoint: POST: remove all tags.
    */
  @ApiOperation(value = "Remove all tags of dataset",
    notes = "Forcefully remove all tags for this dataset.  It is mainly intended for testing.",
    responseClass = "None", httpMethod = "POST")
  def removeAllTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    Logger.debug(s"Removing all tags for dataset with id: $id.")
    if (UUID.isValid(id.stringify)) {
      datasets.get(id) match {
        case Some(dataset) => {
          datasets.removeAllTags(id)
          datasets.index(id)

          Ok(Json.obj("status" -> "success"))
        }
        case None => {
          val msg = s"The dataset with id $id is not found."
          Logger.error(msg)
          NotFound(toJson(msg))
        }
      }
    } else {
      val msg = s"The given id $id is not a valid ObjectId."
      Logger.error(msg)
      BadRequest(toJson(msg))
    }
  }

  // ---------- Tags related code ends ------------------

  @ApiOperation(value = "Add comment to dataset", notes = "", responseClass = "None", httpMethod = "POST")
  def comment(id: UUID) = PermissionAction(Permission.AddComment, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    request.user match {
      case Some(identity) => {
        request.body.\("text").asOpt[String] match {
          case Some(text) => {
            val comment = new Comment(identity, text, dataset_id = Some(id))
            comments.insert(comment)
            datasets.index(id)
            datasets.get(id) match {
              case Some(dataset) => {
                events.addSourceEvent(request.user, comment.id, comment.text , dataset.id, dataset.name, "add_comment_dataset")
              }
            }
            Ok(comment.id.toString())
          }
          case None => {
            Logger.error("no text specified.")
            BadRequest
          }
        }
      }
      case None => BadRequest
    }
  }

  /**
    * List datasets satisfying a user metadata search tree.
    */
  def searchDatasetsUserMetadata = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
    Logger.debug("Searching datasets' user metadata for search tree.")

    var searchJSON = Json.stringify(request.body)
    Logger.debug("thejsson: " + searchJSON)
    var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

    var searchQuery = datasets.searchUserMetadataFormulateQuery(searchTree)

    //searchQuery = searchQuery.reverse

    Logger.debug("Search completed. Returning datasets list.")

    val list = for (dataset <- searchQuery) yield dataset
    Logger.debug("thelist: " + toJson(list))
    Ok(toJson(list))
  }

  /**
    * List datasets satisfying a general metadata search tree.
    */
  def searchDatasetsGeneralMetadata = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
    Logger.debug("Searching datasets' metadata for search tree.")

    var searchJSON = Json.stringify(request.body)
    Logger.debug("thejsson: " + searchJSON)
    var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

    var searchQuery = datasets.searchAllMetadataFormulateQuery(searchTree)

    //searchQuery = searchQuery.reverse

    Logger.debug("Search completed. Returning datasets list.")

    val list = for (dataset <- searchQuery) yield dataset
    Logger.debug("thelist: " + toJson(list))
    Ok(toJson(list))
  }

  /**
    * Return whether a dataset is currently being processed.
    */
  @ApiOperation(value = "Is being processed",
    notes = "Return whether a dataset is currently being processed by a preprocessor.",
    responseClass = "None", httpMethod = "GET")
  def isBeingProcessed(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val filesInDataset:List[File] = dataset.files.map{f => files.get(f).foreach(file => file)}.asInstanceOf[List[File]]

        var isActivity = "false"
        try {
          for (f <- filesInDataset) {
            extractions.findIfBeingProcessed(f.id) match {
              case false =>
              case true => {
                isActivity = "true"
                throw ActivityFound
              }
            }
          }
        } catch {
          case ActivityFound =>
        }

        Ok(toJson(Map("isBeingProcessed" -> isActivity)))
      }
      case None => {
        Logger.error(s"Error getting dataset $id"); InternalServerError
      }
    }
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: String, pId: String, pPath: String, pMain: String, pvRoute: String, pvContentType: String, pvLength: Long): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId, "p_id" -> pId,
        "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute,
        "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
        "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(UUID(pvId)).toString,
        "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(UUID(pvId)).toString,
        "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(UUID(pvId)).toString))
    else
      toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }

  @ApiOperation(value = "Get dataset previews",
    notes = "Return the currently existing previews of the selected dataset (full description, including paths to preview files, previewer names etc).",
    responseClass = "None", httpMethod = "GET")
  def getPreviews(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val datasetWithFiles = dataset.copy(files = dataset.files)
        val datasetFiles: List[File] = datasetWithFiles.files.flatMap(f => files.get(f))
        val previewers = Previewers.findPreviewers
        //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
        val previewslist = for (f <- datasetFiles; if (f.showPreviews.equals("DatasetLevel"))) yield {
          val pvf = for (p <- previewers; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield {
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
          }
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
            val ff = for (p <- previewers; if (p.contentType.contains(f.contentType))) yield {
              //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the
              //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
              if (f.licenseData.isDownloadAllowed(request.user)) {
                (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id) + "/blob", f.contentType, f.length)
              }
              else {
                (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
              }
            }
            (f -> ff)
          }
        }
        Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
      }
      case None => {
        Logger.error("Error getting dataset" + id); InternalServerError
      }
    }
  }

  //Detach and delete dataset code
  /**
    * REST endpoint: DELETE: detach all files from a dataset and then delete the dataset
    *
    *  Takes one arg, id:
    *
    *  @param id, the UUID associated with the dataset to detach all files from and then delete.
    *
    */
  @ApiOperation(value = "Detach and delete dataset",
    notes = "Detaches all files before proceeding to perform the stanadard delete on the dataset.",
    responseClass = "None", httpMethod="DELETE")
  def detachAndDeleteDataset(id: UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match{
      case Some(dataset) => {
        for (f <- dataset.files) {
          detachFileHelper(dataset.id, f, dataset, request.user)
        }
        deleteDatasetHelper(dataset.id, request)
        Ok(toJson(Map("status" -> "success")))
      }
      case None=> {
        Ok(toJson(Map("status" -> "success")))
      }
    }
  }

  /**
    * Utility function to consolidate the utility portions of the delete dataset functionality
    * so that it can be easily called from multiple API operations.
    *
    * @param id The id of the dataset that a file is being detached from
    * @param request The implicit request parameter which is part of the REST API call
    *
    */
  def deleteDatasetHelper(id: UUID, request: UserRequest[AnyContent]) = {
    datasets.get(id) match {
      case Some(dataset) => {
        //remove dataset from RDF triple store if triple store is used
        configuration.getString("userdfSPARQLStore").getOrElse("no") match {
          case "yes" => rdfsparql.removeDatasetFromGraphs(id)
          case _ => Logger.debug("userdfSPARQLStore not enabled")
        }
        events.addObjectEvent(request.user, dataset.id, dataset.name, "delete_dataset")
        datasets.removeDataset(id)
        appConfig.incrementCount('datasets, -1)

        current.plugin[ElasticsearchPlugin].foreach {
          _.delete("data", "dataset", id.stringify)
        }

        for(file <- dataset.files)
          files.index(file)

        current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Dataset","removed",dataset.id.stringify, dataset.name)}
        Ok(toJson(Map("status"->"success")))
      }
      case None => Ok(toJson(Map("status" -> "success")))
    }
  }

  @ApiOperation(value = "Delete dataset",
    notes = "Cascading action (deletes all previews and metadata of the dataset and all files existing only in the deleted dataset).",
    responseClass = "None", httpMethod = "POST")
  def deleteDataset(id: UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    deleteDatasetHelper(id, request)
  }

  @ApiOperation(value = "Get the user-generated metadata of the selected dataset in an RDF file",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getRDFUserMetadata(id: UUID, mappingNumber: String="1") = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    current.plugin[RDFExportService].isDefined match{
      case true => {
        current.plugin[RDFExportService].get.getRDFUserMetadataDataset(id.toString, mappingNumber) match{
          case Some(resultFile) =>{
            Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
              .withHeaders(CONTENT_TYPE -> "application/rdf+xml")
              .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(resultFile.getName(),request.headers.get("user-agent").getOrElse(""))))
          }
          case None => BadRequest(toJson("Dataset not found " + id))
        }
      }
      case _ => Ok("RDF export plugin not enabled")
    }
  }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    Logger.debug("thexml: " + xml)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }

  @ApiOperation(value = "Get URLs of dataset's RDF metadata exports",
    notes = "URLs of metadata exported as RDF from XML files contained in the dataset, as well as the URL used to export the dataset's user-generated metadata as RDF.",
    responseClass = "None", httpMethod = "GET")
  def getRDFURLsForDataset(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    current.plugin[RDFExportService].isDefined match{
      case true =>{
        current.plugin[RDFExportService].get.getRDFURLsForDataset(id.toString)  match {
          case Some(listJson) => {
            Ok(listJson)
          }
          case None => Logger.error(s"Error getting dataset $id"); InternalServerError
        }
      }
      case false => {
        Ok("RDF export plugin not enabled")
      }
    }
  }

  @ApiOperation(value = "Get technical metadata of the dataset",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTechnicalMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val listOfMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))
          .filter(_.creator.typeOfAgent == "extractor")
          .map(JSONLD.jsonMetadataWithContext(_) \ "content")
        Ok(toJson(listOfMetadata))
      }
      case None => Logger.error("Error finding dataset" + id); InternalServerError
    }
  }


  def getXMLMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getXMLMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}
    }
  }

  def getUserMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getUserMetadataJSON(id))
      }
      case None => {
        Logger.error("Error finding dataset" + id);
        InternalServerError
      }

    }
  }

  def dumpDatasetGroupings = ServerAdminAction { request =>

    val unsuccessfulDumps = datasets.dumpAllDatasetGroupings
    if(unsuccessfulDumps.size == 0)
      Ok("Dumping of dataset file groupings was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of dataset file groupings was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Ok(unsuccessfulMessage)
    }
  }

  def dumpDatasetsMetadata = ServerAdminAction { request =>

    val unsuccessfulDumps = datasets.dumpAllDatasetMetadata
    if(unsuccessfulDumps.size == 0)
      Ok("Dumping of datasets metadata was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of datasets metadata was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Ok(unsuccessfulMessage)
    }
  }

  @ApiOperation(value = "Follow dataset.",
    notes = "Add user to dataset followers and add dataset to user followed datasets.",
    responseClass = "None", httpMethod = "POST")
  def follow(id: UUID) = AuthenticatedAction {
    request =>
      val user = request.user
      user match {
        case Some(loggedInUser) => {
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(user, id, dataset.name, "follow_dataset")
              datasets.addFollower(id, loggedInUser.id)
              userService.followDataset(loggedInUser.id, id)

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

  @ApiOperation(value = "Unfollow dataset.",
    notes = "Remove user from dataset followers and remove dataset from user followed datasets.",
    responseClass = "None", httpMethod = "POST")
  def unfollow(id: UUID) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    user match {
      case Some(loggedInUser) => {
        datasets.get(id) match {
          case Some(dataset) => {
            events.addObjectEvent(user, id, dataset.name, "unfollow_dataset")
            datasets.removeFollower(id, loggedInUser.id)
            userService.unfollowDataset(loggedInUser.id, id)
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
    val followeeModel = datasets.get(followeeUUID)
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


  /**
    * Enumerator to loop over all files in a dataset and return chunks for the result zip file that will be
    * streamed to the client. The zip files are streamed and not stored on disk.
    *
    * @param dataset dataset from which to get teh files
    * @param chunkSize chunk size in memory in which to buffer the stream
    * @param compression java built in compression value. Use 0 for no compression.
    * @return Enumerator to produce array of bytes from a zipped stream containing the bytes of each file
    *         in the dataset
    */
  def enumeratorFromDataset(dataset: Dataset, chunkSize: Int = 1024 * 8, compression: Int = Deflater.DEFAULT_COMPRESSION, bagit: Boolean, user : Option[User])
                           (implicit ec: ExecutionContext): Enumerator[Array[Byte]] = {
    implicit val pec = ec.prepare()
    val dataFolder = if (bagit) "data/" else ""
    val folderNameMap = scala.collection.mutable.Map.empty[UUID, String]
    var inputFilesBuffer = new ListBuffer[File]()
    dataset.files.foreach(f=>files.get(f) match {
      case Some(file) => {
        inputFilesBuffer += file
        folderNameMap(file.id) = dataFolder + file.filename + "_" + file.id.stringify
      }
      case None => Logger.error(s"No file with id $f")
    })

    val md5Files = scala.collection.mutable.HashMap.empty[String, MessageDigest] //for the files
    val md5Bag = scala.collection.mutable.HashMap.empty[String, MessageDigest] //for the bag files

    folders.findByParentDatasetId(dataset.id).foreach{
      folder => folder.files.foreach(f=> files.get(f) match {
        case Some(file) => {
          inputFilesBuffer += file
          var name = folder.displayName
          var f1: Folder = folder
          while(f1.parentType == "folder") {
            folders.get(f1.parentId) match {
              case Some(fparent) => {
                name = fparent.displayName + "/"+ name
                f1 = fparent
              }
              case None =>
            }
          }
          folderNameMap(file.id) = dataFolder + name + "/" + file.filename + "_" + file.id.stringify
        }
        case None => Logger.error(s"No file with id $f")
      })
    }
    val inputFiles = inputFilesBuffer.toList
    // which file we are currently processing

    val byteArrayOutputStream = new ByteArrayOutputStream(chunkSize)
    val zip = new ZipOutputStream(byteArrayOutputStream)
    // zip compression level
    zip.setLevel(compression)

    var totalBytes = 0L
    var level = 0 //dataset,file, bag
    var file_type = 0 //
    var count = 0 //count for files

    /*
     * Explanation for the cases
     *
     * the level can be 0 (file) 1 (dataset) and 2 (bag).
     *
     * when the level is file, the file_type can be 0 (info) 1 (metadata) or 2 (the actual files)
     *
     * when the level is dataset, the file_type can be 0 (info) or 1 (metadata)
     *
     * when the level is bag, the file_type can be
     *
     * 0 - bagit.txt
     * 1 - bag-info.txt
     * 2 - manifest-md5.txt
     * 3 - tagmanifest-md5.txt
     *
     * when the dataset is finished (in either mode) the level = -1 and file_type = -1 and
     * the enumerator is finished
     */

    var is: Option[InputStream] = addDatasetInfoToZip(dataFolder,dataset,zip)
    //digest input stream
    val md5 = MessageDigest.getInstance("MD5")
    md5Files.put(dataFolder+"_info.json",md5)
    is = Some(new DigestInputStream(is.get,md5))
    file_type = 1 //next is metadata


    Enumerator.generateM({
      is match {
        case Some(inputStream) => {
          val buffer = new Array[Byte](chunkSize)
          val bytesRead = scala.concurrent.blocking {
            inputStream.read(buffer)

          }
          val chunk = bytesRead match {
            case -1 => {
              // finished individual file
              zip.closeEntry()
              inputStream.close()

              (level,file_type) match {
                //dataset, info
                case (0,0) => {
                  is = addDatasetInfoToZip(dataFolder,dataset,zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Files.put("_info.json",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  file_type = file_type + 1
                }
                //dataset, metadata
                case (0,1) => {
                  is = addDatasetMetadataToZip(dataFolder,dataset,zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Files.put("_metadata.json",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  level = 1
                  file_type = 0
                }
                //file info
                case (1,0) =>{
                  is = addFileInfoToZip(folderNameMap(inputFiles(count).id), inputFiles(count), zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Files.put(folderNameMap(inputFiles(count).id)+"/_info.json",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  if (count+1 < inputFiles.size ){
                    count +=1
                  } else {
                    count = 0
                    file_type = 1
                  }
                }
                //file metadata
                case (1,1) =>{
                  is = addFileMetadataToZip(folderNameMap(inputFiles(count).id), inputFiles(count), zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Files.put(folderNameMap(inputFiles(count).id)+"/_metadata.json",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  if (count+1 < inputFiles.size ){
                    count +=1
                  } else {
                    count = 0
                    file_type = 2
                  }
                }
                //files
                case (1,2) => {
                  is = addFileToZip(folderNameMap(inputFiles(count).id), inputFiles(count), zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Files.put(folderNameMap(inputFiles(count).id)+"/"+inputFiles(count).filename,md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  if (count+1 < inputFiles.size ){
                    count +=1
                  } else {
                    if (bagit){
                      count = 0
                      level = 2
                      file_type = 0
                    } else {
                      //done
                      level = -1
                      file_type = -1
                    }

                  }
                }
                //bagit.txt
                case (2,0) => {
                  is = addBagItTextToZip(totalBytes,folderNameMap.size,zip,dataset,user)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Bag.put("bagit.txt",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  file_type = 1
                }
                //bag-info.txt
                case (2,1) => {
                  is = addBagInfoToZip(zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Bag.put("bag-info.txt",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  file_type = 2
                }
                //manifest-md5.txt
                case (2,2) => {
                  is = addManifestMD5ToZip(md5Files.toMap[String,MessageDigest],zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Bag.put("manifest-md5.txt",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  file_type = 3
                }
                //tagmanifest-md5.txt
                case (2,3) => {
                  is = addTagManifestMD5ToZip(md5Bag.toMap[String,MessageDigest],zip)
                  val md5 = MessageDigest.getInstance("MD5")
                  md5Bag.put("tagmanifest-md5.txt",md5)
                  is = Some(new DigestInputStream(is.get, md5))
                  level = -1
                  file_type = -1
                }
                //the end, or a bad case
                case (_,_) => {
                  zip.close()
                  is = None
                }
              }
              //this is generated after all the matches
              Some(byteArrayOutputStream.toByteArray)
            }
            case read => {
              zip.write(buffer, 0, read)
              Some(byteArrayOutputStream.toByteArray)
            }
          }
          if (level < 2){
            totalBytes += bytesRead
          }
          // reset temporary byte array
          byteArrayOutputStream.reset()
          Future.successful(chunk)
        }
        case None => {
          Future.successful(None)
        }
      }
    })(pec)
  }


  private def addFileToZip(folderName: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    files.getBytes(file.id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        zip.putNextEntry(new ZipEntry(folderName + "/" + filename))
        Some(inputStream)
      }
      case None => None
    }
  }

  private def addFileMetadataToZip(folderName: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/_metadata.json"))
    val fileMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file.id)).map(JSONLD.jsonMetadataWithContext(_))
    val s : String = Json.prettyPrint(Json.toJson(fileMetadata))
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def getDatasetInfoAsJson(dataset : Dataset) : JsValue = {
    val rightsHolder = {
      val licenseType = dataset.licenseData.m_licenseType
      if (licenseType == "license1") {
        dataset.author.fullName
      } else if (licenseType == "license2") {
        "Creative Commons"
      } else if (licenseType == "license3") {
        "Public Domain Dedication"
      } else {
        "None"
      }
    }

    val spaceNames = for (
      spaceId <- dataset.spaces;
      space <- spaces.get(spaceId)
    ) yield {
      space.name
    }

    val dataset_description = Utils.decodeString(dataset.description)

    val licenseInfo = Json.obj("licenseText"->dataset.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id"->dataset.id,"name"->dataset.name,"author"->dataset.author.email,"description"->dataset_description, "spaces"->spaceNames.mkString(","),"lastModified"->dataset.lastModifiedDate.toString,"license"->licenseInfo)
  }

  private def addDatasetInfoToZip(folderName: String, dataset: models.Dataset, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/_info.json"))
    val infoListMap = Json.prettyPrint(getDatasetInfoAsJson(dataset))
    Some(new ByteArrayInputStream(infoListMap.getBytes("UTF-8")))
  }

  private def getFileInfoAsJson(file : models.File) : JsValue = {
    val rightsHolder = {
      val licenseType = file.licenseData.m_licenseType
      if (licenseType == "license1") {
        file.author.fullName
      } else if (licenseType == "license2") {
        "Creative Commons"
      } else if (licenseType == "license3") {
        "Public Domain Dedication"
      } else {
        "None"
      }

    }
    val licenseInfo = Json.obj("licenseText"->file.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id" -> file.id, "filename" -> file.filename, "author" -> file.author.email, "uploadDate" -> file.uploadDate.toString,"contentType"->file.contentType,"description"->file.description,"license"->licenseInfo)
  }

  private def addFileInfoToZip(folderName: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/_info.json"))
    val fileInfo = getFileInfoAsJson(file)
    val s : String = Json.prettyPrint(fileInfo)
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addDatasetMetadataToZip(folderName: String, dataset : models.Dataset, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "_dataset_metadata.json"))
    val datasetMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id))
      .map(JSONLD.jsonMetadataWithContext(_))
    val s : String = Json.prettyPrint(Json.toJson(datasetMetadata))
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addBagItTextToZip(totalbytes: Long, totalFiles: Long, zip: ZipOutputStream, dataset: models.Dataset, user: Option[models.User]) = {
    zip.putNextEntry(new ZipEntry("bagit.txt"))
    val softwareLine = "Bag-Software-Agent: clowder.ncsa.illinois.edu\n"
    val baggingDate = "Bagging-Date: "+(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime)+"\n"
    val baggingSize = "Bag-Size: " + _root_.util.Formatters.humanReadableByteCount(totalbytes) + "\n"
    val payLoadOxum = "Payload-Oxum: "+ totalbytes + "." + totalFiles +"\n"
    val senderIdentifier="Internal-Sender-Identifier: "+dataset.id+"\n"
    val senderDescription = "Internal-Sender-Description: "+dataset.description+"\n"
    var s:String = ""
    if (user.isDefined) {
      val contactName = "Contact-Name: " + user.get.fullName + "\n"
      val contactEmail = "Contact-Email: " + user.get.email.getOrElse("") + "\n"
      s = softwareLine+baggingDate+baggingSize+payLoadOxum+contactName+contactEmail+senderIdentifier+senderDescription
    } else {
      s = softwareLine+baggingDate+baggingSize+payLoadOxum+senderIdentifier+senderDescription
    }

    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addBagInfoToZip(zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("bag-info.txt"))
    val s : String = "BagIt-Version: 0.97\n"+"Tag-File-Character-Encoding: UTF-8\n"
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addManifestMD5ToZip(md5map : Map[String,MessageDigest] ,zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("manifest-md5.txt"))
    var s : String = ""
    md5map.foreach{
      case (filePath,md) => {
        val current = Hex.encodeHexString(md.digest())+" "+filePath+"\n"
        s = s + current
      }
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addTagManifestMD5ToZip(md5map : Map[String,MessageDigest],zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("tagmanifest-md5.txt"))
    var s : String = ""
    md5map.foreach{
      case (filePath,md) => {
        val current = Hex.encodeHexString(md.digest())+" "+filePath+"\n"
        s = s + current
      }
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  @ApiOperation(value = "Download dataset",
    notes = "Downloads all files contained in a dataset.",
    responseClass = "None", httpMethod = "GET")
  def download(id: UUID, bagit: Boolean,compression: Int) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
        datasets.get(id) match {
          case Some(dataset) => {
            // Use custom enumerator to create the zip file on the fly
            // Use a 1MB in memory byte array
            Ok.chunked(enumeratorFromDataset(dataset,1024*1024, compression,bagit,user)).withHeaders(
              CONTENT_TYPE -> "application/zip",
              CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(dataset.name+ ".zip", request.headers.get("user-agent").getOrElse("")))
            )
          }
          // If the dataset wasn't found by ID
          case None => {
            NotFound
          }
        }
  }

  @ApiOperation(value = "change the access of dataset",
    notes = "Downloads all files contained in a dataset.",
    responseClass = "None", httpMethod = "PUT")
  def updateAccess(id:UUID, access:String) = PermissionAction(Permission.PublicDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        datasets.get(id) match {
          case Some(dataset) if !dataset.isTRIAL => {
            datasets.update(dataset.copy(status = access))
            events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
            Ok(toJson(Map("status" -> "success")))
          }
          // If the dataset wasn't found by ID
          case _ => {
            InternalServerError("Update Access failed")
          }
        }
      }
      case None => {
        Unauthorized
      }
    }
  }

  @ApiOperation(value = "Insert add_file Event",
    notes = "Insert an Event into the Events Collection",
    responseClass = "None", httpMethod = "POST")
  def addFileEvent(id:UUID,  inFolder:Boolean, fileCount: Int ) = AuthenticatedAction {implicit request =>
    datasets.get(id) match{
      case Some(d) =>  {
        var eventType = if (inFolder) "add_file_folder" else "add_file"
        eventType = eventType + "_" + fileCount.toString
        events.addObjectEvent(request.user, id, d.name, eventType)
      }

      // we do not return an internal server error here since this function just add an event and won't influence the
      // following operations.
      case None =>  Logger.error("Dataset not found")
    }
    Ok(toJson("added new event"))
  }
  def copyDatasetToSpace(datasetId: UUID, spaceId: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if (u.id == dataset.author.id) {
              spaces.get(spaceId) match {
                case Some(space) => {
                  val d = Dataset(name = dataset.name, description = dataset.description, created = new Date(), author = dataset.author, licenseData = dataset.licenseData, spaces = List(spaceId))

                  datasets.insert(d) match {
                    case Some(id) => {
                      spaces.addDataset(d.id, spaceId)
                      relations.add(Relation(source = Node(datasetId.stringify, ResourceType.dataset), target = Node(d.id.stringify, ResourceType.dataset)))
                      events.addSourceEvent(request.user, d.id, d.name, space.id, space.name, "add_dataset_space")

                      dataset.folders.map { folder =>
                        copyFolders(folder, datasetId, "dataset", d.id)
                      }

                      dataset.files.map { fileId =>
                        files.get(fileId) match {
                          case Some(file) => {
                            val newFile = File(loader_id = file.loader_id, filename = file.filename, author = file.author,
                              uploadDate = file.uploadDate, contentType = file.contentType, length = file.length,
                              loader = file.loader, showPreviews = file.showPreviews, previews = file.previews, thumbnail_id = file.thumbnail_id,
                              description = file.description, licenseData = file.licenseData, status = file.status)
                            files.save(newFile)
                            datasets.addFile(UUID(id), newFile)
                            relations.add(Relation(source = Node(file.id.stringify, ResourceType.file), target = Node(newFile.id.stringify, ResourceType.file)))
                          }
                          case None => Logger.error("Unable to copy file with id: " + fileId.stringify + " to dataset with id: " + id)
                        }
                      }
                      Ok(toJson(Map("newDatasetId" -> d.id.stringify)))
                    }
                    case None => BadRequest(s"Unable to copy the dataset with id $datasetId to space with id: $spaceId")
                  }
                }
                case None => BadRequest(s"No space found with id: + $spaceId.")
              }
            } else {
              BadRequest("You don't have permission to copy the dataset.")
            }
          }
          case None => BadRequest(s"No dataset  found with id: $datasetId")
        }
      }
      case None => BadRequest("You need to be logged in to copy a dataset to a space.")
    }

  }

  private def copyFolders(id: UUID, parentId: UUID, parentType: String, datasetId: UUID) {
    folders.get(id) match {
      case Some(folder) => {
        val newFiles = ListBuffer[UUID]()
        for (fileId <- folder.files)   {
          files.get(fileId) match {
            case Some(file) => {
              val newFile = File(loader_id = file.loader_id, filename = file.filename, author = file.author,
                uploadDate = file.uploadDate, contentType = file.contentType, length = file.length,
                loader = file.loader, showPreviews = file.showPreviews, previews = file.previews, thumbnail_id = file.thumbnail_id,
                description = file.description, licenseData = file.licenseData, status = file.status)
              files.save(newFile)
              relations.add(Relation(source = Node(file.id.stringify, ResourceType.file), target = Node(newFile.id.stringify, ResourceType.file)))
              newFiles += newFile.id
            }
            case None =>
          }
        }
        val newFolder = Folder(author = folder.author, created = new Date(), name = folder.name, displayName = folder.displayName,
          files = newFiles.toList, folders = List.empty, parentId = parentId, parentType = parentType.toLowerCase(), parentDatasetId = datasetId)
        folders.insert(newFolder)
        if(parentType == "dataset"){
          datasets.addFolder(datasetId, newFolder.id)
        } else {
          folders.addSubFolder(parentId, newFolder.id)
        }

        relations.add(Relation(source= Node(folder.id.stringify, ResourceType.folder), target = Node(newFolder.id.stringify, ResourceType.folder)))
        folder.folders.map( f => copyFolders(f, newFolder.id, "folder", datasetId))
      }

    }
  }
}

object ActivityFound extends Exception {}
