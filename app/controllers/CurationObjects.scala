package controllers

import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.Date
import javax.inject.Inject
import api.{UserRequest, Permission}
import com.mongodb.BasicDBList
import models._
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.json.JSONArray
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.JsArray
import play.libs.F.Promise
import services._
import _root_.util.RequiredFieldsConfig
import play.api.Play._
import org.apache.http.client.methods.HttpPost
import scala.concurrent.Future
import scala.concurrent.Await
import play.api.mvc.{Request, Action, AnyContent, Results}
import play.api.libs.ws._
import play.api.libs.ws.WS._
import play.api.libs.functional.syntax._


import scala.concurrent.duration._
import play.api.libs.json.Reads._
import play.api.libs.json.JsPath.readNullable
import java.net.URI

/**
 * Methods for interacting with the curation objects in the staging area.
 */
class CurationObjects @Inject()(
  curations: CurationService,
  datasets: DatasetService,
  collections: CollectionService,
  spaces: SpaceService,
  files: FileService,
  comments: CommentService,
  sections: SectionService,
  events: EventService,
  userService: UserService
  ) extends SecuredController {

  def newCO(datasetId:UUID, spaceId:String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    val (name, desc, spaceByDataset) = datasets.get(datasetId) match {
      case Some(dataset) => (dataset.name, dataset.description, dataset.spaces map( id => spaces.get(id)) filter(_ != None)
        filter (space => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, space.get.id)))map(_.get))
      case None => ("", "", List.empty)
    }
    //default space is the space from which user access to the dataset
    val defaultspace = spaceId match {
      case "" => {
        if(spaceByDataset.length ==1) {
          spaceByDataset.lift(0)
        } else {
          None
        }
      }
      case _ => spaces.get(UUID(spaceId))
    }

    Ok(views.html.curations.newCuration(datasetId, name, desc, defaultspace, spaceByDataset, RequiredFieldsConfig.isNameRequired,
      RequiredFieldsConfig.isDescriptionRequired))
  }

  /**
   * Controller flow to create a new curation object. On success,
   * the browser is redirected to the new Curation page.
   */
  def submit(datasetId:UUID, spaceId:UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.multipartFormData)  { implicit request =>

    //get name, des, space from request
    var COName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)

    implicit val user = request.user
    user match {
      case Some(identity) => {

        datasets.get(datasetId) match {
          case Some(dataset) => {
            // val spaceId = UUID(COSpace(0))
            if (spaces.get(spaceId) != None) {

              //copy file list from FileDAO.
              var newFiles: List[File]= List.empty
              for ( fileId <- dataset.files) {
                files.get(fileId) match {
                  case Some(f) => {
                    newFiles =  f :: newFiles
                  }
                }
              }

              //the model of CO have multiple datasets and collections, here we insert a list containing one dataset
              val newCuration = CurationObject(
                name = COName(0),
                author = identity,
                description = CODesc(0),
                created = new Date,
                submittedDate = None,
                publishedDate= None,
                space = spaceId,
                datasets = List(dataset),
                files = newFiles,
                repository = None,
                status = "In Curation"
              )

              // insert curation
              Logger.debug("create curation object: " + newCuration.id)
              curations.insert(newCuration)

              Redirect(routes.CurationObjects.getCurationObject(newCuration.id))
            }
            else {
              InternalServerError("Space not found")
            }
          }
          case None => InternalServerError("Dataset Not found")
        }
      }
      case None => InternalServerError("User Not found")
    }
  }

  /**
   * Delete curation object.
   */
  def deleteCuration(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) {
    implicit request =>
      implicit val user = request.user

      curations.get(id) match {
        case Some(c) => {
          Logger.debug("delete Curation object: " + c.id)
          val spaceId = c.space
          curations.remove(id)
          //spaces.get(spaceId) is checked in Space.stagingArea
          Redirect(routes.Spaces.stagingArea(spaceId))
        }
        case None => InternalServerError("Curation Object Not found")
      }
  }

  // This function is actually "updateDatasetUserMetadata", it can rewrite the metadata in curation.dataset and add/ modify/ delte
  // is all done in this function. We use addDatasetUserMetadata to keep consistency with live objects
  def addDatasetUserMetadata(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) (parse.json) { implicit request =>
    implicit val user = request.user
    Logger.debug(s"Adding user metadata to curation's dataset $id")



    curations.get(id) match {
      case Some(c) => {
        if (c.status == "In Curation") {
          // write metadata to the collection "curationObjects"
          curations.addDatasetUserMetaData(id, Json.stringify(request.body))
          //add event
          events.addObjectEvent(user, id, c.name, "addMetadata_curation")
        } else {
          InternalServerError("Curation Object already submitted")
        }
      }
    }

    //datasets.index(id)
    //    configuration.getString("userdfSPARQLStore").getOrElse("no") match {
    //      case "yes" => datasets.setUserMetadataWasModified(id, true)
    //      case _ => Logger.debug("userdfSPARQLStore not enabled")
    //    }
    Ok(toJson(Map("status" -> "success")))
  }


  def getFiles(curation: CurationObject, dataset: Dataset): List[File] ={
    curation.files filter (f => dataset.files.contains (f.id))
  }

  def addFileUserMetadata(curationId:UUID, fileId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId)))  (parse.json) { implicit request =>
    implicit val user = request.user

    curations.get(curationId) match {
      case Some(c) => {
        if (c.status == "In Curation") {
          val newFiles: List[File]= c.files
          val index = newFiles.indexWhere(_.id.equals(fileId))
          Logger.debug(s"Adding user metadata to curation's file No." + index )
          // write metadata to curationObjects
          curations.addFileUserMetaData(curationId, index, Json.stringify(request.body))
          //add event
          events.addObjectEvent(user, curationId, c.name, "addMetadata_curation")
        } else {
          InternalServerError("Curation Object already submitted")
        }}
      case None => InternalServerError("Curation Object Not found")
    }

    Ok(toJson(Map("status" -> "success")))
  }


  def getCurationObject(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {    implicit request =>
    implicit val user = request.user
    curations.get(curationId) match {
      case Some(c) => {
        val ds: Dataset = c.datasets(0)
        //dsmetadata is immutable but dsUsrMetadata is mutable
        val dsmetadata = ds.metadata
        val dsUsrMetadata = collection.mutable.Map(ds.userMetadata.toSeq: _*)
        val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
        val fileByDataset = getFiles(c, ds)
        if (c.status != "In Curation") {
          Ok(views.html.spaces.submittedCurationObject(c, ds, fileByDataset))
        } else {
          Ok(views.html.spaces.curationObject(c, dsmetadata, dsUsrMetadata, isRDFExportEnabled, fileByDataset))
        }
      }
      case None => InternalServerError("Curation Object Not found")
    }
  }

  def findMatchingRepositories(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
          curations.get(curationId) match {
            case Some(c) => {
              val propertiesMap: Map[String, List[String]] = Map( "Access" -> List("Open", "Restricted", "Embargo", "Enclave"),
                "License" -> List("Creative Commons", "GPL") , "Cost" -> List("Free", "$XX Fee"),
                "Affiliation" -> List("UMich", "IU", "UIUC"))
              val mmResp = callMatchmaker(c)(request)
              user match {
                case Some(usr) => {
                  val repPreferences = usr.repositoryPreferences.map{ value => value._1 -> value._2.toString().split(",").toList}
                  Ok(views.html.spaces.matchmakerResult(c, propertiesMap, repPreferences, mmResp))
                }
                case None =>Results.Redirect(routes.RedirectUtility.authenticationRequiredMessage("You must be logged in to perform that action.", request.uri ))
              }
            }
            case None => InternalServerError("Curation Object not found")
          }
  }

  def callMatchmaker(c: CurationObject)(implicit request: Request[Any]): List[MatchMakerResponse] = {
    val https = controllers.Utils.https(request)
    val hostUrl = api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "#aggregation"
    val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => pref._1-> Json.toJson(pref._2.toString().split(",").toList))).getOrElse(Map.empty)
    val userPreferences = userPrefMap + ("Repository" -> Json.toJson(c.repository))
    val maxDataset = if (!c.files.isEmpty)  c.files.map(_.length).max else 0
    val totalSize = if (!c.files.isEmpty) c.files.map(_.length).sum else 0
    val metadata = c.datasets(0).metadata ++ c.datasets(0).datasetXmlMetadata.map(metadata => metadata.xmlMetadata) ++ c.datasets(0).userMetadata
    val metadataJson = metadata.map {
      item => item.asInstanceOf[Tuple2[String, BasicDBList]]._1 -> Json.toJson(item.asInstanceOf[Tuple2[String, BasicDBList]]._2.get(0).toString())
    }

    val creator = userService.findByIdentity(c.author).map ( usr => usr.profile match {
      case Some(prof) => prof.orcidID match {
        case Some(oid) => oid
        case None => api.routes.Users.findById(usr.id).absoluteURL(https)
      }
        case None => api.routes.Users.findById(usr.id).absoluteURL(https)

    })
    val aggregation = metadataJson.toMap ++ Map(
      "Identifier" -> Json.toJson(controllers.routes.CurationObjects.getCurationObject(c.id).absoluteURL(https).toString()),
      "@id" -> Json.toJson(hostUrl),
      "Title" -> Json.toJson(c.name),
      "Creator" -> Json.toJson(creator),
      "similarTo" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https).toString())
      )
    val valuetoSend = Json.obj(
      "@context" -> Json.toJson(Seq(
        Json.toJson("https://w3id.org/ore/context"),
        Json.toJson(Map (
          "Identifier" -> Json.toJson("http://www.ietf.org/rfc/rfc4122"),
          "Aggregation Statistics" -> Json.toJson("http://sead-data.net/terms/publicationstatistics"),
          "Data Mimetypes" -> Json.toJson("http://purl.org/dc/elements/1.1/format"),
          "Affiliations" -> Json.toJson("http://sead-data.net/terms/affiliations"),
          "Preferences" -> Json.toJson("http://sead-data.net/terms/publicationpreferences"),
          "Max Collection Depth" -> Json.toJson("http://sead-data.net/terms/maxcollectiondepth"),
          "Total Size" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
          "Max Dataset Size" -> Json.toJson("http://sead-data.net/terms/maxdatasetsize"),
          "Creator" -> Json.toJson("http://purl.org/dc/terms/creator"),
          "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
          "similarTo" -> Json.toJson("http://sead-data.net/terms/similarTo"),
          "Access" -> Json.toJson("http://sead-data.net/terms/access"),
          "License" -> Json.toJson("http://purl.org/dc/terms/license"),
          "Cost" -> Json.toJson("http://sead-data.net/terms/cost"),
          "Creator" -> Json.toJson("http://purl.org/dc/terms/creator"),
          "Alternative title" -> Json.toJson("http://purl.org/dc/terms/alternative"),
          "Contact" -> Json.toJson("http://sead-data.net/terms/contact"),
          "name" -> Json.toJson("http://purl.org/dc/terms/name"),
          "email" -> Json.toJson("http://purl.org/dc/terms/email"),
          "Description" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
          "Audience" -> Json.toJson("http://purl.org/dc/terms/audience"),
          "Abstract" -> Json.toJson("http://purl.org/dc/terms/abstract"),
          "Bibliographic citation" -> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
          "Spatial Reference" ->
            Json.toJson(
              Map(
                "@id" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/gis/hasGeoPoint"),
                "Longitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#long"),
                "Latitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#lat"),
                "Altitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#alt")

              ))
      )))),
      "Aggregation" ->
        Json.toJson(aggregation),
      "Preferences" -> userPreferences ,
      "Aggregation Statistics" ->
        Map(
          "Data Mimetypes" -> Json.toJson(c.files.map(_.contentType).toSet),
          "Max Collection Depth" -> Json.toJson("0"),
          "Max Dataset Size" -> Json.toJson(maxDataset.toString),
          "Total Size" -> Json.toJson(totalSize.toString)
        )
    )
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = play.Play.application().configuration().getString("matchmaker.uri").replaceAll("/$","")
    val futureResponse = WS.url(endpoint).post(valuetoSend)
    Logger.debug("Value to send matchmaker: " + valuetoSend)
    var jsonResponse: play.api.libs.json.JsValue = new JsArray()
    val result = futureResponse.map {
      case response =>
        if(response.status >= 200 && response.status < 300 || response.status == 304) {
          jsonResponse = response.json
        }
        else {
          Logger.error("Error Calling Matchmaker: " + response.getAHCResponse.getResponseBody())
        }
    }

    val rs = Await.result(result, Duration.Inf)

    jsonResponse.as[List[MatchMakerResponse]]
  }

  def compareToRepository(curationId: UUID, repository: String) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user

       curations.get(curationId) match {
         case Some(c) => {
           curations.updateRepository(c.id, repository)
           //TODO: Make some call to C3-PR?
           //  Ok(views.html.spaces.matchmakerReport())

           val mmResp = callMatchmaker(c).filter(_.orgidentifier == repository)

           Ok(views.html.spaces.curationDetailReport( c, mmResp(0), repository))
        }
        case None => InternalServerError("Space not found")
      }
  }

  def sendToRepository(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user

      curations.get(curationId) match {
        case Some(c) =>
          var success = false
          var repository: String = ""
          c.repository match {
            case Some (s) => repository = s
            case None => Ok(views.html.spaces.curationSubmitted( c, "No Repository Provided", success))
          }
          val key = play.api.Play.configuration.getString("commKey").getOrElse("")
          val https = controllers.Utils.https(request)
          val hostUrl = api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "?key=" + key
          val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => pref._1-> Json.toJson(pref._2.toString().split(",").toList))).getOrElse(Map.empty)
          val userPreferences = userPrefMap + ("Repository" -> Json.toJson(repository))
          val maxDataset = if (!c.files.isEmpty)  c.files.map(_.length).max else 0
          val totalSize = if (!c.files.isEmpty) c.files.map(_.length).sum else 0
          val metadata = c.datasets(0).metadata ++ c.datasets(0).datasetXmlMetadata.map(metadata => metadata.xmlMetadata) ++ c.datasets(0).userMetadata
          var metadataJson = metadata.map {
            item => item.asInstanceOf[Tuple2[String, BasicDBList]]._1 -> Json.toJson(item.asInstanceOf[Tuple2[String, BasicDBList]]._2.get(0).toString())
          }
          val creator = Json.toJson(userService.findByIdentity(c.author).map ( usr => usr.profile match {
            case Some(prof) => prof.orcidID match {
              case Some(oid) => oid
              case None => controllers.routes.Profile.viewProfileUUID(usr.id).absoluteURL(https)
            }
            case None => controllers.routes.Profile.viewProfileUUID(usr.id).absoluteURL(https)

          }))
          var metadataToAdd = metadataJson.toMap
          if(metadataJson.toMap.get("Abstract") == None) {
            metadataToAdd = metadataJson.toMap.+("Abstract" -> Json.toJson(c.description))
          }
          val valuetoSend = Json.toJson(
            Map(
              "@context" -> Json.toJson(Seq(
                Json.toJson("https://w3id.org/ore/context"),
                Json.toJson(
                  Map(
                    "Identifier" -> Json.toJson("http://www.ietf.org/rfc/rfc4122"),
                    "Aggregation Statistics" -> Json.toJson("http://sead-data.net/terms/publicationstatistics"),
                    "Data Mimetypes" -> Json.toJson("http://purl.org/dc/elements/1.1/format"),
                    "Affiliations" -> Json.toJson("http://sead-data.net/terms/affiliations"),
                    "Preferences" -> Json.toJson("http://sead-data.net/terms/publicationpreferences"),
                    "Max Collection Depth" -> Json.toJson("http://sead-data.net/terms/maxcollectiondepth"),
                    "Total Size" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
                    "Max Dataset Size" -> Json.toJson("http://sead-data.net/terms/maxdatasetsize"),
                    "Creator" -> Json.toJson("http://purl.org/dc/terms/creator"),
                    "Repository" -> Json.toJson("http://sead-data.net/terms/repository"),
                    "Aggregation" -> Json.toJson("http://sead-data.net/terms/aggregation"),
                    "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
                    "Abstract" -> Json.toJson("http://purl.org/dc/terms/abstract"),
                    "Number of Datasets" -> Json.toJson("http://purl.org/dc/terms/numberdatasets"),
                    "Number of Collections" -> Json.toJson("http://purl.org/dc/terms/numbercollections"),
                    "Publication Callback" -> Json.toJson("http://purl.org/dc/terms/publicationcallback"),
                    "Environment Key" -> Json.toJson("http://sead-data.net/terms/environmentkey"),
                    "Access" -> Json.toJson("http://sead-data.net/terms/access"),
                    "License" -> Json.toJson("http://purl.org/dc/terms/license"),
                    "Cost" -> Json.toJson("http://sead-data.net/terms/cost")
                )
              ))),
                "Repository" -> Json.toJson(repository.toLowerCase()),
                "Preferences" -> Json.toJson(
                  userPreferences
                ),
                "Aggregation" -> Json.toJson( metadataToAdd ++
                  Map(
                    "Identifier" -> Json.toJson("urn:uuid:"+curationId),
                    "@id" -> Json.toJson(hostUrl),
                    "@type" -> Json.toJson("Aggregation"),
                    "Title" -> Json.toJson(c.name),
                    "Creator" -> Json.toJson(creator)
                  )
                ),
                "Aggregation Statistics" -> Json.toJson(
                  Map(
                    "Max Collection Depth" -> Json.toJson("0"),
                    "Data Mimetypes" -> Json.toJson(c.files.map(_.contentType).toSet),
                    "Max Dataset Size" -> Json.toJson(maxDataset.toString),
                    "Total Size" -> Json.toJson(totalSize.toString),
                    "Number of Datasets" -> Json.toJson(c.files.length),
                    "Number of Collections" -> Json.toJson(c.datasets.length)
                  )),
                "Publication Callback" -> Json.toJson(controllers.routes.CurationObjects.savePublishedObject(c.id).absoluteURL(https) +"?key=" + key),
                "Environment Key" -> Json.toJson(play.api.Play.configuration.getString("commKey").getOrElse(""))
              )
            )
          Logger.debug("Submitting request for publication: " + valuetoSend)

          implicit val context = scala.concurrent.ExecutionContext.Implicits.global
          val endpoint =play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$","")
          val futureResponse = WS.url(endpoint).post(valuetoSend)
          var jsonResponse: play.api.libs.json.JsValue = new JsArray()
          val result = futureResponse.map {
            case response =>
              if(response.status >= 200 && response.status < 300 || response.status == 304) {
                curations.setSubmitted(c.id)
                jsonResponse = response.json
                success = true
              }
              else {

                Logger.error("Error Submitting to Repository: " + response.getAHCResponse.getResponseBody())
              }
          }

          val rs = Await.result(result, Duration.Inf)

          Ok(views.html.spaces.curationSubmitted( c, repository, success))
      }
  }

  /**
   * Endpoint for receiving status/ uri from repository.
   */
  def savePublishedObject(id: UUID) = UserAction (parse.json) {
    implicit request =>
      Logger.debug("get infomation from repository")

      curations.get(id) match {

        case Some(c) => {
          c.status match {

            case "In Curation" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object hasn't been submitted yet.")))
            //sead2 receives status once from repository,
            case "Published" | "ERROR" | "Reject" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object already received status from repository.")))
            case "Submitted" => {
              //parse status from request's body
              val statusList = (request.body \ "status").asOpt[String]

              statusList.size match {
                case 0 => {
                  if ((request.body \ "uri").asOpt[String].isEmpty) {
                    BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Receive empty request.")))
                  } else {
                    (request.body \ "uri").asOpt[String].map {
                      externalIdentifier => {
                        //set published when uri is provided
                        curations.setPublished(id)
                        if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")) {
                          val DOI_PREFIX = "http://dx.doi.org/"
                          curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                        } else {
                          curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                        }
                      }
                    }
                    Ok(toJson(Map("status" -> "OK")))
                  }
                }
                case 1 => {
                  statusList.map {
                    status =>
                      if (status.compareToIgnoreCase("Published") == 0 || status.compareToIgnoreCase("Publish") == 0) {
                        curations.setPublished(id)
                      } else {
                        //other status except Published, such as ERROR, Rejected
                        curations.updateStatus(id, status)
                      }
                  }

                  (request.body \ "uri").asOpt[String].map {
                    externalIdentifier => {
                      if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")) {
                        val DOI_PREFIX = "http://dx.doi.org/"
                        curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                      } else {
                        curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                      }
                    }
                  }
                  Ok(toJson(Map("status" -> "OK")))
                }
                //multiple status
                case _ => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object has unrecognized status .")))
              }

            }
          }
        }
        case None => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object not found.")))
      }
  }


  /**
   * Endpoint for getting status from repository.
   */
  def getStatusFromRepository (id: UUID)  = Action.async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    curations.get(id) match {

      case Some(c) => {

        val endpoint = play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$", "") + "/urn:uuid:" +id.toString()
        Logger.debug(endpoint)
        val futureResponse = WS.url(endpoint).get()

        futureResponse.map{
          case response =>
            if(response.status >= 200 && response.status < 300 || response.status == 304) {
              (response.json \ "Status").asOpt[JsValue]
              Ok(response.json)
            } else {
              Logger.error("Error Getting Status: " + response.getAHCResponse.getResponseBody)
              InternalServerError(toJson("Status object not found."))
            }
        }
      }
      case None => Future(InternalServerError(toJson("Curation object not found.")))
    }
  }

}