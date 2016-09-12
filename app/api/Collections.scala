package api

import java.io.{ByteArrayInputStream, InputStream, ByteArrayOutputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.text.SimpleDateFormat
import java.util.zip.{ZipEntry, ZipOutputStream, Deflater}

import _root_.util.JSONLD
import api.Permission.Permission
import org.apache.commons.codec.binary.Hex
import play.api.Logger
import play.api.Play.current
import models._
import play.api.libs.iteratee.Enumerator
import services._
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.concurrent.Execution.Implicits._
import scala.util.parsing.json.JSONArray
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.{Calendar, Date}
import controllers.Utils


/**
 * Manipulate collections.
 */
@Api(value = "/collections", listingPath = "/api-docs.json/collections", description = "Collections are groupings of datasets")
@Singleton
class Collections @Inject() (folders : FolderService, files: FileService, metadataService : MetadataService, datasets: DatasetService, collections: CollectionService, previews: PreviewService, userService: UserService, events: EventService, spaces:SpaceService) extends ApiController {

  @ApiOperation(value = "Create a collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def createCollection() = PermissionAction(Permission.CreateCollection) (parse.json) { implicit request =>
    Logger.debug("Creating new collection")
    (request.body \ "name").asOpt[String].map { name =>

      var c : Collection = null
      implicit val user = request.user
      user match {
        case Some(identity) => {
          val description = (request.body \ "description").asOpt[String].getOrElse("")
          (request.body \ "space").asOpt[String] match {
            case None | Some("default") => c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, author = identity)
            case Some(space) =>  if (spaces.get(UUID(space)).isDefined) {

              c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, author = identity, spaces = List(UUID(space)))
            } else {
              BadRequest(toJson("Bad space = " + space))
            }
          }

          collections.insert(c) match {
            case Some(id) => {
              c.spaces.map(spaceId => spaces.get(spaceId)).flatten.map{ s =>
                spaces.addCollection(c.id, s.id, user)
                collections.addToRootSpaces(c.id, s.id)
                events.addSourceEvent(request.user, c.id, c.name, s.id, s.name, "add_collection_space")
              }
              Ok(toJson(Map("id" -> id)))
            }
            case None => Ok(toJson(Map("status" -> "error")))
          }
        }
        case None => InternalServerError("User Not found")
      }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }


  @ApiOperation(value = "Add dataset to collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachDataset(collectionId: UUID, datasetId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    // TODO this needs to be cleaned up when do permissions for adding to a resource
    if (!Permission.checkOwner(request.user, ResourceRef(ResourceRef.dataset, datasetId))) {
      Forbidden(toJson(s"You are not the owner of the dataset"))
    } else {
      collections.addDataset(collectionId, datasetId) match {
        case Success(_) => {
          var datasetsInCollection = 0
          collections.get(collectionId) match {
            case Some(collection) => {
              datasets.get(datasetId) match {
                case Some(dataset) => {
                  if (play.Play.application().configuration().getBoolean("addDatasetToCollectionSpace")){
                    collections.addDatasetToCollectionSpaces(collection.id,dataset.id, request.user)
                  }
                  events.addSourceEvent(request.user , dataset.id, dataset.name, collection.id, collection.name, "attach_dataset_collection")
                }
                case None =>
              }
              datasetsInCollection = collection.datasetCount
            }
            case None =>
          }
          //datasetsInCollection is the number of datasets in this collection
          Ok(Json.obj("datasetsInCollection" -> Json.toJson(datasetsInCollection) ))
        }
        case Failure(t) => InternalServerError
      }
    }
  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  @ApiOperation(value = "Reindex a collection",
    notes = "Reindex the existing collection, if recursive is set to true it will also reindex all datasets and files.",
    httpMethod = "GET")
  def reindex(id: UUID, recursive: Boolean) = PermissionAction(Permission.CreateCollection, Some(ResourceRef(ResourceRef.collection, id))) {  implicit request =>
      collections.get(id) match {
        case Some(coll) => {
          current.plugin[ElasticsearchPlugin].foreach {
            _.index(coll, recursive)
          }
          Ok(toJson(Map("status" -> "success")))
        }
        case None => {
          Logger.error("Error getting collection" + id)
          BadRequest(toJson(s"The given collection id $id is not a valid ObjectId."))
        }
      }
  }

  @ApiOperation(value = "Remove dataset from collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => {

        collections.get(collectionId) match {
        case Some(collection) => {
          datasets.get(datasetId) match {
            case Some(dataset) => {
              events.addSourceEvent(request.user , dataset.id, dataset.name, collection.id, collection.name, "remove_dataset_collection")
            }
            case None =>
          }
        }
        case None =>
      }
      Ok(toJson(Map("status" -> "success")))
    }
    case Failure(t) => {
      Logger.error("Error: " + t)
      InternalServerError
    }
    }
  }

  @ApiOperation(value = "Remove collection",
      notes = "Does not delete the individual datasets in the collection.",
      responseClass = "None", httpMethod = "DELETE")
  def removeCollection(collectionId: UUID) = PermissionAction(Permission.DeleteCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        events.addObjectEvent(request.user , collection.id, collection.name, "delete_collection")
        collections.delete(collectionId)
        current.plugin[AdminsNotifierPlugin].foreach {
          _.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",collection.id.stringify, collection.name)
        }
      }
    }
    //Success anyway, as if collection is not found it is most probably deleted already
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "List all collections the user can view",
    notes = "This will check for Permission.ViewCollection",
    responseClass = "None", multiValueResponse=true, httpMethod = "GET")
  def list(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    Ok(toJson(lisCollections(title, date, limit, Set[Permission](Permission.ViewCollection), false, request.user, request.user.fold(false)(_.superAdminMode))))
  }
  @ApiOperation(value = "List all collections the user can edit",
    notes = "This will check for Permission.AddResourceToCollection and Permission.EditCollection",
    responseClass = "None", httpMethod = "GET")
  def listCanEdit(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    Ok(toJson(lisCollections(title, date, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.user.fold(false)(_.superAdminMode))))
  }

  def addDatasetToCollectionOptions(datasetId: UUID, title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    var listAll = false
    var collectionList: List[Collection] = List.empty
    if(play.api.Play.current.plugin[services.SpaceSharingPlugin].isDefined) {
      listAll = true
    } else {
      datasets.get(datasetId) match {
        case Some(dataset) => {
          if(dataset.spaces.length > 0) {
            collectionList = collections.listInSpaceList(title, date, limit, dataset.spaces, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), user)
          } else {
            listAll = true
          }

        }
        case None => Logger.debug("The dataset was not found")
      }
    }
    if(listAll) {
      collectionList = lisCollections(title, date, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.user.fold(false)(_.superAdminMode))
    }
    Ok(toJson(collectionList))
  }

  private def getNextLevelCollections(current_collections : List[Collection]) : List[Collection] = {
    val next_level_collections : ListBuffer[Collection] = ListBuffer.empty[Collection]
    for (current_collection : Collection <- current_collections){
      for (child_id <- current_collection.child_collection_ids){
        collections.get(child_id) match {
          case Some(child_col) => next_level_collections += child_col
          case None =>
        }
      }
    }
    next_level_collections.toList
  }



  @ApiOperation(value = "List all collections the user can edit except itself and its parent collections",
    notes = "This will check for Permission.AddResourceToCollection and Permission.EditCollection",
    responseClass = "None", httpMethod = "GET")
  def listPossibleParents(currentCollectionId : String, title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    val selfAndAncestors = collections.getSelfAndAncestors(UUID(currentCollectionId))
    val descendants = collections.getAllDescendants(UUID(currentCollectionId)).toList
    val allCollections = lisCollections(None, None, limit, Set[Permission](Permission.AddResourceToCollection, Permission.EditCollection), false, request.user, request.user.fold(false)(_.superAdminMode))
    val possibleNewParents = allCollections.filter((c: Collection) =>
      if(play.api.Play.current.plugin[services.SpaceSharingPlugin].isDefined) {
        (!selfAndAncestors.contains(c) && !descendants.contains(c))
      } else {
            collections.get(UUID(currentCollectionId)) match {
              case Some(coll) => {
                if(coll.spaces.length == 0) {
                   (!selfAndAncestors.contains(c) && !descendants.contains(c))

                } else {
                   (!selfAndAncestors.contains(c) && !descendants.contains(c) && c.spaces.intersect(coll.spaces).length > 0)
                }
              }
              case None => (!selfAndAncestors.contains(c) && !descendants.contains(c))
            }
      }
    )
    Ok(toJson(possibleNewParents))
  }



  /**
   * Returns list of collections based on parameters and permissions.
   * TODO this needs to be cleaned up when do permissions for adding to a resource
   */
  private def lisCollections(title: Option[String], date: Option[String], limit: Int, permission: Set[Permission], mine: Boolean, user: Option[User], superAdmin: Boolean) : List[Collection] = {
    if (mine && user.isEmpty) return List.empty[Collection]

    (title, date) match {
      case (Some(t), Some(d)) => {
        if (mine)
          collections.listUser(d, true, limit, t, user, superAdmin, user.get)
        else
          collections.listAccess(d, true, limit, t, permission, user, superAdmin, true)
      }
      case (Some(t), None) => {
        if (mine)
          collections.listUser(limit, t, user, superAdmin, user.get)
        else
          collections.listAccess(limit, t, permission, user, superAdmin, true)
      }
      case (None, Some(d)) => {
        if (mine)
          collections.listUser(d, true, limit, user, superAdmin, user.get)
        else
          collections.listAccess(d, true, limit, permission, user, superAdmin, true)
      }
       case (None, None) => {
        if (mine)
          collections.listUser(limit, user, superAdmin, user.get)
        else
          collections.listAccess(limit, permission, user, superAdmin, true)
      }
    }
  }

  @ApiOperation(value = "Get a specific collection",
    responseClass = "Collection", httpMethod = "GET")
  def getCollection(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.get(collectionId) match {
      case Some(x) => Ok(jsonCollection(x))
      case None => BadRequest(toJson("collection not found"))
    }
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString,"author"-> collection.author.toString, "root_flag" -> collections.hasRoot(collection).toString,
      "child_collection_ids"-> collection.child_collection_ids.toString, "parent_collection_ids" -> collection.parent_collection_ids.toString,
    "childCollectionsCount" -> collection.childCollectionsCount.toString, "datasetCount"-> collection.datasetCount.toString, "spaces" -> collection.spaces.toString))
  }

  @ApiOperation(value = "Update a collection name",
  notes= "Takes one argument, a UUID of the collection. Request body takes a key-value pair for the name",
  responseClass = "None", httpMethod = "PUT")
  def updateCollectionName(id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, id)))(parse.json) {
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
        Logger.debug(s"Update title for collection with id $id. New name: $name")
        collections.updateName(id, name)
        collections.get(id) match {
          case Some(collection) => {
            events.addObjectEvent(user, id, collection.name, "update_collection_information")
          }

        }
        Ok(Json.obj("status" -> "success"))
      }
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  @ApiOperation(value = "Update collection description",
  notes = "Takes one argument, a UUID of the collection. Request body takes key-value pair for the description",
  responseClass = "None", httpMethod = "PUT")
  def updateCollectionDescription(id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, id)))(parse.json) {
    implicit request =>
      implicit val user = request.user
      if (UUID.isValid(id.stringify)) {
        var description: String = null
        val aResult = (request.body \ "description").validate[String]
        aResult match {
          case s: JsSuccess[String] => {
            description = s.get
          }
          case e: JsError => {
            Logger.error("Errors: " + JsError.toFlatJson(e).toString())
            BadRequest(toJson(s"description data is missing"))
          }
        }
        Logger.debug(s"Update description for collection with id $id. New description: $description")
        collections.updateDescription(id, description)
        collections.get(id) match {
          case Some(collection) => {
            events.addObjectEvent(user, id, collection.name, "update_collection_information")
          }

        }
        Ok(Json.obj("status" -> "success"))
      }
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }

  }
  /**
   * Add preview to file.
   */
  @ApiOperation(value = "Attach existing preview to collection",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def attachPreview(collection_id: UUID, preview_id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, collection_id)))(parse.json) { implicit request =>
      // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        eid
      } else {
        Logger.debug("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        Some("Other")
      }
      val preview_type = (request.body \ "preview_type").asOpt[String].getOrElse("")
      request.body match {
        case JsObject(fields) => {
          collections.get(collection_id) match {
            case Some(collection) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToCollection(preview_id, collection_id, preview_type, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("Collection not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("Collection not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  @ApiOperation(value = "Follow collection.",
    notes = "Add user to collection followers and add collection to user followed collections.",
    responseClass = "None", httpMethod = "POST")
  def follow(id: UUID) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              events.addObjectEvent(user, id, collection.name, "follow_collection")
              collections.addFollower(id, loggedInUser.id)
              userService.followCollection(loggedInUser.id, id)

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

  @ApiOperation(value = "Unfollow collection.",
    notes = "Remove user from collection followers and remove collection from user followed collections.",
    responseClass = "None", httpMethod = "POST")
  def unfollow(id: UUID) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              events.addObjectEvent(user, id, collection.name, "unfollow_collection")
              collections.removeFollower(id, loggedInUser.id)
              userService.unfollowCollection(loggedInUser.id, id)
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
    val followeeModel = collections.get(followeeUUID)
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


  @ApiOperation(value = "Add subcollection to collection",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def attachSubCollection(collectionId: UUID, subCollectionId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    collections.addSubCollection(collectionId, subCollectionId, request.user) match {
      case Success(_) => {
        collections.get(collectionId) match {
          case Some(collection) => {
            collections.get(subCollectionId) match {
              case Some(sub_collection) => {
                events.addSourceEvent(request.user, sub_collection.id, sub_collection.name, collection.id, collection.name, "add_sub_collection")
                Ok(jsonCollection(collection))
              }
            }
          }
        }
      }
      case Failure(t) => InternalServerError
    }
  }



  @ApiOperation(value = "Create a collection with parent",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def createCollectionWithParent() = PermissionAction(Permission.CreateCollection) (parse.json) { implicit request =>
    (request.body \ "name").asOpt[String].map{ name =>
      var c : Collection = null
      implicit val user = request.user

      user match {
        case Some(identity) => {
          val description = (request.body \ "description").asOpt[String].getOrElse("")
          (request.body \ "space").asOpt[String] match {
            case None | Some("default") =>  c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, childCollectionsCount = 0, author = identity, root_spaces = List.empty)
            case Some(space) => if (spaces.get(UUID(space)).isDefined) {
              c = Collection(name = name, description = description, created = new Date(), datasetCount = 0, author = identity, spaces = List(UUID(space)), root_spaces = List(UUID(space)))
            } else {
              BadRequest(toJson("Bad space = " + space))
            }
          }

          collections.insert(c) match {
            case Some(id) => {
              c.spaces.map{ spaceId =>
                spaces.get(spaceId)}.flatten.map{ s =>
                  spaces.addCollection(c.id, s.id, request.user)
                  collections.addToRootSpaces(c.id, s.id)
                  events.addSourceEvent(request.user, c.id, c.name, s.id, s.name, "add_collection_space")
              }

              //do stuff with parent here
              (request.body \"parentId").asOpt[String] match {
                case Some(parentId) => {
                  collections.get(UUID(parentId)) match {
                    case Some(parentCollection) => {
                      collections.addSubCollection(UUID(parentId), UUID(id), user) match {
                        case Success(_) => {
                          Ok(toJson(Map("id" -> id)))
                        }
                      }
                    }
                    case None => {
                      Ok(toJson("No collection with parentId found"))
                    }
                  }
                }
                case None => {
                  Ok(toJson("No parentId supplied"))
                }

              }
              Ok(toJson(Map("id" -> id)))
            }
            case None => Ok(toJson(Map("status" -> "error")))
          }
        }
        case None => InternalServerError("User Not found")
      }

    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  @ApiOperation(value = "Remove subcollection from collection",
    notes="",
    responseClass = "None", httpMethod = "POST")
  def removeSubCollection(collectionId: UUID, subCollectionId: UUID, ignoreNotFound: String) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>

    collections.removeSubCollection(collectionId, subCollectionId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => {

        collections.get(collectionId) match {
          case Some(collection) => {
            collections.get(subCollectionId) match {
              case Some(sub_collection) => {
                events.addSourceEvent(request.user, sub_collection.id, sub_collection.name, collection.id, collection.name, "remove_subcollection")
              }
            }
          }
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case Failure(t) => InternalServerError
    }
  }

  def isCollectionRootOrHasNoParent(collectionId: UUID): Unit = {
    collections.get(collectionId) match {
      case Some(collection) => {
        if (collections.hasRoot(collection) || collection.parent_collection_ids.isEmpty) {
          return true
        } else
          return false

      }
      case None =>
        Ok("no collection with id : " + collectionId)
    }
  }


  /**
    * Adds a Root flag for a collection in a space
    */
  @ApiOperation(value = "Add root flags for a collection in space",
    notes = "",
    responseClass = "None",httpMethod = "POST")
  def setRootSpace(collectionId: UUID, spaceId: UUID)  = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    Logger.debug("changing the value of the root flag")
    (collections.get(collectionId), spaces.get(spaceId)) match {
      case (Some(collection), Some(space)) => {
        spaces.addCollection(collectionId, spaceId, request.user)
        collections.addToRootSpaces(collectionId, spaceId)
        events.addSourceEvent(request.user, collection.id, collection.name, space.id, space.name, "add_collection_space")
        Ok(jsonCollection(collection))
      } case (None, _) => {
        Logger.error("Error getting collection  " + collectionId)
        BadRequest(toJson(s"The given collection id $collectionId is not a valid ObjectId."))
      }
      case _ => {
        Logger.error("Error getting space  " + spaceId)
        BadRequest(toJson(s"The given space id $spaceId is not a valid ObjectId."))
      }
    }
  }

  /**
    * Remove root flag from a collection in a space
    */
  @ApiOperation(value = "Removes root flag from a collection in  a space",
    notes = "",
    responseClass = "None",httpMethod = "POST")
  def unsetRootSpace(collectionId: UUID, spaceId: UUID)  = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    Logger.debug("changing the value of the root flag")
    collections.get(collectionId) match {
      case Some(collection) => {
        collections.removeFromRootSpaces(collectionId, spaceId)
        Ok(jsonCollection(collection))
      } case None => {
        Logger.error("Error getting collection  " + collectionId)
        BadRequest(toJson(s"The given collection id $collectionId is not a valid ObjectId."))
      }
    }
  }

  @ApiOperation(value = "Get all root collections",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getRootCollections() = PermissionAction(Permission.ViewCollection) { implicit request =>
    val root_collections_list = for (collection <- collections.listAccess(100,Set[Permission](Permission.ViewCollection),request.user,true, true); if collections.hasRoot(collection)  )
      yield jsonCollection(collection)

    Ok(toJson(root_collections_list))
  }

  @ApiOperation(value = "Get all collections",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getAllCollections(limit : Int, showAll: Boolean) = PermissionAction(Permission.ViewCollection) { implicit request =>
    val all_collections_list = request.user match {
      case Some(usr) => {
        for (collection <- collections.listAllCollections(usr, showAll, limit))
          yield jsonCollection(collection)
      }
      case None => List.empty
    }
    Ok(toJson(all_collections_list))
  }



  @ApiOperation(value = "Get all root collections or collections that do not have a parent",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getTopLevelCollections() = PermissionAction(Permission.ViewCollection){ implicit request =>
    implicit val user = request.user
    val count = collections.countAccess(Set[Permission](Permission.ViewCollection),user,true)
    val limit = count.toInt
    val top_level_collections = for (collection <- collections.listAccess(limit,Set[Permission](Permission.ViewCollection),request.user,true, true); if (collections.hasRoot(collection) || collection.parent_collection_ids.isEmpty))
      yield jsonCollection(collection)
    Ok(toJson(top_level_collections))
  }

  @ApiOperation(value = "Get child collection ids in collection",
    responseClass = "None", httpMethod = "GET")
  def getChildCollectionIds(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val childCollectionIds = collection.child_collection_ids
        Ok(toJson(childCollectionIds))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }

  @ApiOperation(value = "Get parent collection ids in collection",
    responseClass = "None", httpMethod = "GET")
  def getParentCollectionIds(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val parentCollectionIds = collection.parent_collection_ids
        Ok(toJson(parentCollectionIds))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }



  @ApiOperation(value = "Get child collections in collection",
    responseClass = "None", httpMethod = "GET")
  def getChildCollections(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val childCollections = ListBuffer.empty[JsValue]
        val childCollectionIds = collection.child_collection_ids
        for (childCollectionId <- childCollectionIds) {
          collections.get(childCollectionId) match {
            case Some(child_collection) => {
              childCollections += jsonCollection(child_collection )
            }
            case None =>
              Logger.debug("No child collection with id : " + childCollectionId)
              collections.removeSubCollectionId(childCollectionId,collection)
          }
        }

        Ok(toJson(childCollections))
      }
      case None => BadRequest(toJson("collection not found"))
    }
  }

  @ApiOperation(value = "Get parent collections for collection",
    responseClass = "None", httpMethod = "GET")
  def getParentCollections(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection,collectionId))){implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val parentCollections = ListBuffer.empty[JsValue]
        val parentCollectionIds = collection.parent_collection_ids
        for (parentCollectionId <- parentCollectionIds) {
          collections.get(parentCollectionId) match {
            case Some(parent_collection) => {
              parentCollections += jsonCollection(parent_collection )
            }
            case None =>
              Logger.debug("No parent collection with id : " + parentCollectionId)
              collections.removeParentCollectionId(parentCollectionId,collection)
          }
        }

        Ok(toJson(parentCollections))
      }

      case None => BadRequest(toJson("collection not found"))
    }
  }

  @ApiOperation(value = "Checks if we can remove a collection from a space",
    responseClass = "None", httpMethod = "GET")
  def removeFromSpaceAllowed(collectionId: UUID , spaceId : UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    val user = request.user
    user match {
      case Some(identity) => {
        val hasParentInSpace = collections.hasParentInSpace(collectionId, spaceId)
        Ok(toJson(!(hasParentInSpace)))
      }
      case None => Ok(toJson(false))
    }
  }

  @ApiOperation(value = "Download dataset",
    notes = "Downloads all files contained in a dataset.",
    responseClass = "None", httpMethod = "GET")
  def download(id: UUID, bagit: Boolean,compression: Int) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {
        // Use custom enumerator to create the zip file on the fly
        // Use a 1MB in memory byte array
        Ok.chunked(enumeratorFromCollection(collection,1024*1024, compression,bagit,user)).withHeaders(
          "Content-Type" -> "application/zip",
          "Content-Disposition" -> ("attachment; filename=\"" + collection.name+ ".zip\"")
        )
      }
      // If the dataset wasn't found by ID
      case None => {
        NotFound
      }
    }
  }


  def enumeratorFromCollection(collection: Collection, chunkSize: Int = 1024 * 8, compression: Int = Deflater.DEFAULT_COMPRESSION, bagit: Boolean, user : Option[User])
                              (implicit ec: ExecutionContext): Enumerator[Array[Byte]] = {

    implicit val pec = ec.prepare()
    var enumerator : Enumerator[Array[Byte]] = Enumerator.empty[Array[Byte]]
    var datasetsInCollection = datasets.listCollection(collection.id.stringify, user)
    val rootFolderName = collection.name

    val firstName = datasetsInCollection(0).name
    val secondName = datasetsInCollection(1).name

    var dataset_enumerator : Enumerator[Array[Byte]] = enumeratorFromDataset(rootFolderName+"/"+firstName+"/",datasetsInCollection(0),1024*1024, compression,bagit,user)(ec)
    var other_dataset_enumerator  : Enumerator[Array[Byte]] = enumeratorFromDataset(rootFolderName+"/"+secondName+"/",datasetsInCollection(1),1024*1024, compression,bagit,user)(ec)

    var foo : Option[Enumerator[Array[Byte]]] = None


    val combined = dataset_enumerator.andThen(other_dataset_enumerator)
    combined

  }

  /**
    * Iterator for streams for a file
    *
    */
  def streamIteratorForFile(pathToFile : String, file : models.File, md5Files : scala.collection.mutable.HashMap[String, MessageDigest], zip : ZipOutputStream ) : Iterator[Option[InputStream]] = {
    val info_is = addFileToZip(pathToFile, file, zip)
    val info_md5 = MessageDigest.getInstance("MD5")
    md5Files.put(file.filename+"_info.json",info_md5)
    val fileInfoStream = Some(new DigestInputStream(info_is.get, info_md5))

    val metadata_is = addFileInfoToZip(pathToFile,file,zip)
    val metadata_md5 = MessageDigest.getInstance("MD5")
    md5Files.put(file.filename+"_metadata.json",metadata_md5)
    val fileMetadataStream = Some(new DigestInputStream(metadata_is.get, metadata_md5))

    val file_is = addFileToZip(pathToFile,file,zip)
    val file_md5 = MessageDigest.getInstance("MD5")
    md5Files.put(file.filename,metadata_md5)
    val fileStream = Some(new DigestInputStream(file_is.get,file_md5))
    Iterator(fileInfoStream,fileMetadataStream,fileStream)
  }

  class FileIterator(pathToFile : String, file : models.File,zip : ZipOutputStream) extends Iterator[Option[InputStream]] {
    var file_type : Int = 0

    def hasNext() = {
      if ( file_type > 3){
        true
      }
      else
        false
    }

    def next() = {
      file_type match {
        case 0 => {
          file_type +=1
          addFileToZip(pathToFile, file, zip)
        }
        case 1 => {
          file_type+=1
          addFileMetadataToZip(pathToFile,file,zip)
        }
        case 2 => {
          file_type+=1
          addFileToZip(pathToFile,file,zip)
        }
        case _ => None
      }
    }
  }

  def getDatasetsInCollection(collection : models.Collection) : List[Dataset] = {
    var datasetsInCollection : ListBuffer[Dataset] = ListBuffer.empty[Dataset]
    datasetsInCollection.toList
  }

  def getNextGenerationCollections(currentCollections : List[Collection]) : List[Collection] = {
    var nextGenerationCollections : ListBuffer[Collection] = ListBuffer.empty[Collection]
    for (currentCollection <- currentCollections){
      val child_ids = currentCollection.child_collection_ids
      for (child_id <- child_ids){
        collections.get(child_id) match {
          case Some(child_col) => nextGenerationCollections += child_col
          case None => None
        }
      }
    }
    nextGenerationCollections.toList
  }

  private def addCollectionInfoToZip(folderName: String, collection: models.Collection, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/"+collection.name+"_info.json"))
    val infoListMap = Json.prettyPrint(jsonCollection(collection))
    Some(new ByteArrayInputStream(infoListMap.getBytes("UTF-8")))
  }

  private def addCollectionMetadataToZip(folderName : String , collection : models.Collection, zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName+"/"+collection.name+"_metadata.json"))
    val collectionMetadata = getCollectionInfoAsJson(collection)
    val metadataMap = Json.prettyPrint(collectionMetadata)
    Some(new ByteArrayInputStream(metadataMap.getBytes("UTF-8")))
  }

  def getCollectionInfoAsJson(collection : models.Collection) : JsValue = {
    val author = collection.author.fullName
    Json.obj("description"->collection.description,"created"->collection.created.toString)
  }

  def getCollectionMetadataAsJson(collection : models.Collection) : JsValue = {
    val collectionMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.collection, collection.id)).map(JSONLD.jsonMetadataWithContext(_))
    Json.obj("metadata"->collectionMetadata)
  }


  def getOutputStreamForCollection(dataFolder : String,zip : ZipOutputStream, md5Files : scala.collection.mutable.HashMap[String, MessageDigest], collection : Collection, level : Int, file_type : Int ) : Option[InputStream] = {
    (file_type) match {
      case 0 => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(dataFolder+"_info.json",md5)
        val is = addCollectionInfoToZip(dataFolder, collection,zip)
        Some(new DigestInputStream(is.get, md5))
      }
      case 1 => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(dataFolder+"_metadata.json",md5)
        val is = addCollectionMetadataToZip(dataFolder, collection,zip)
        Some(new DigestInputStream(is.get, md5))
      }
    }
  }


  def getOutputStreamForDataset(dataFolder: String, zip : ZipOutputStream, md5Files : scala.collection.mutable.HashMap[String, MessageDigest], dataset : Dataset, level : Int, file_type : Int) : Option[InputStream] = {
    (level, file_type) match {
      //dataset info
      case (0,0) => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(dataFolder+"_info.json",md5)
        val is = addDatasetInfoToZip(dataFolder, dataset,zip)
        Some(new DigestInputStream(is.get, md5))
      }
      //dataset metadata
      case (0,1) => {
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put("_metadata.json",md5)
        val is = addDatasetMetadataToZip(dataFolder, dataset,zip)
        Some(new DigestInputStream(is.get, md5))
      }
      //files
    }
  }

  def getOutputStreamForFile(dataFolder: String, zip : ZipOutputStream, md5Files : scala.collection.mutable.HashMap[String, MessageDigest], dataset : Dataset, file : models.File, level : Int, file_type : Int) : Option[InputStream] = {
    file_type match {
      case 0 => {
        val is = addFileInfoToZip(dataFolder,file,zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.id+"/_info.json",md5)
        Some(new DigestInputStream(is.get, md5))
      }
      case 1 => {
        val is = addFileMetadataToZip(dataFolder, file, zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.id+"/_metadata.json",md5)
        Some(new DigestInputStream(is.get, md5))
      }
      case 2 => {
        val is = addFileToZip(dataFolder, file, zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Files.put(file.filename,md5)
        Some(new DigestInputStream(is.get, md5))
      }
      case _ => None

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
  def enumeratorFromDataset(pathToFolder : String , dataset: Dataset, chunkSize: Int = 1024 * 8, compression: Int = Deflater.DEFAULT_COMPRESSION, bagit: Boolean, user : Option[User])
                           (implicit ec: ExecutionContext): Enumerator[Array[Byte]] = {
    implicit val pec = ec.prepare()
    val dataFolder = if (bagit) pathToFolder+"/data/" else pathToFolder+"/"
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

    val licenseInfo = Json.obj("licenseText"->dataset.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id"->dataset.id,"name"->dataset.name,"author"->dataset.author.email,"description"->dataset.description, "spaces"->spaceNames.mkString(","),"lastModified"->dataset.lastModifiedDate.toString,"license"->licenseInfo)
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
    val baggingSize = "Bag-Size: " + _root_.util.FileUtils.humanReadableByteCount(totalbytes) + "\n"
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

}

