package controllers

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}

import api.Permission
import api.Permission
import api.Permission.Permission
import models._
import play.api.Logger
import play.api.Play.current
import services.{CollectionService, DatasetService, _}
import util.RequiredFieldsConfig

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

@Singleton
class T2C2 @Inject()(comments : CommentService, sections : SectionService, vocabularies : VocabularyService, datasets: DatasetService, collections: CollectionService, previewsService: PreviewService,
                     spaceService: SpaceService, users: UserService, events: EventService, files : FileService) extends SecuredController {

  def newTemplate(space: Option[String]) = PermissionAction(Permission.CreateCollection) { implicit request =>
    implicit val user = request.user
    val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_))
    var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
    for (aSpace <- spacesList) {
      //For each space in the list, check if the user has permission to add something to it, if so
      //decode it and add it to the list to pass back to the view.
      if (Permission.checkPermission(Permission.AddResourceToSpace, ResourceRef(ResourceRef.space, aSpace.id))) {
        decodedSpaceList += Utils.decodeSpaceElements(aSpace)
      }
    }
    space match {
      case Some(spaceId) => {
        spaceService.get(UUID(spaceId)) match {
          case Some(s) => Ok(views.html.newTemplate(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, Some(spaceId)))
          case None => Ok(views.html.newTemplate(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None))
        }
      }
      case None =>  Ok(views.html.newTemplate(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None))
    }

  }

  def index = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user
    val latestFiles = files.latest(5)
    val datasetsCount = datasets.count()
    val datasetsCountAccess = datasets.countAccess(Set[Permission](Permission.ViewDataset), user, request.user.fold(false)(_.superAdminMode))
    val filesCount = files.count()
    val filesBytes = files.bytes()
    val collectionsCount = collections.count()
    val collectionsCountAccess = collections.countAccess(Set[Permission](Permission.ViewCollection), user, request.user.fold(false)(_.superAdminMode))
    val spacesCount = spaceService.count()
    val spacesCountAccess = spaceService.countAccess(Set[Permission](Permission.ViewSpace), user, request.user.fold(false)(_.superAdminMode))
    val usersCount = users.count()
    //newsfeedEvents is the combination of followedEntities and requestevents, then take the most recent 20 of them.
    var newsfeedEvents = user.fold(List.empty[Event])(u => events.getEvents(u.followedEntities, Some(20)).sorted(Ordering.by((_: Event).created).reverse))
    newsfeedEvents =  (newsfeedEvents ::: events.getRequestEvents(user, Some(20)))
      .sorted(Ordering.by((_: Event).created).reverse).take(20)
    user match {
      case Some(clowderUser) if !clowderUser.active => {
        Redirect(routes.Error.notActivated())
      }
      case Some(clowderUser) if clowderUser.active => {
        val datasetsUser = datasets.listUser(4, Some(clowderUser), request.user.fold(false)(_.superAdminMode), clowderUser)
        val datasetcommentMap = datasetsUser.map { dataset =>
          var allComments = comments.findCommentsByDatasetId(dataset.id)
          dataset.files.map { file =>
            allComments ++= comments.findCommentsByFileId(file)
            sections.findByFileId(file).map { section =>
              allComments ++= comments.findCommentsBySectionId(section.id)
            }
          }
          dataset.id -> allComments.size
        }.toMap
        val collectionList = collections.listUser(4, Some(clowderUser), request.user.fold(false)(_.superAdminMode), clowderUser)
        var collectionsWithThumbnails = collectionList.map {c =>
          if (c.thumbnail_id.isDefined) {
            c
          } else {
            val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
            c.copy(thumbnail_id = collectionThumbnail)
          }
        }

        //Modifications to decode HTML entities that were stored in an encoded fashion as part
        //of the collection's names or descriptions
        val decodedCollections = ListBuffer.empty[models.Collection]
        for (aCollection <- collectionsWithThumbnails) {
          decodedCollections += Utils.decodeCollectionElements(aCollection)
        }
        val spacesUser = spaceService.listUser(4, Some(clowderUser),request.user.fold(false)(_.superAdminMode), clowderUser)
        var followers: List[(UUID, String, String, String)] = List.empty
        for (followerID <- clowderUser.followers.take(3)) {
          var userFollower = users.findById(followerID)
          userFollower match {
            case Some(uFollower) => {
              var ufEmail = uFollower.email.getOrElse("")
              followers = followers.++(List((uFollower.id, uFollower.fullName, ufEmail, uFollower.getAvatarUrl())))
            }
            case None =>
          }
        }
        var followedUsers: List[(UUID, String, String, String)] = List.empty
        var followedFiles: List[(UUID, String, String)] = List.empty
        var followedDatasets: List[(UUID, String, String)] = List.empty
        var followedCollections: List[(UUID, String, String)] = List.empty
        var followedSpaces: List[(UUID, String, String)] = List.empty
        val maxDescLength = 50
        for (tidObject <- clowderUser.followedEntities) {
          if (tidObject.objectType == "user") {
            val followedUser = users.get(tidObject.id)
            followedUser match {
              case Some(fuser) => {
                followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.getOrElse(""), fuser.getAvatarUrl())))
              }
              case None =>
            }
          } else if (tidObject.objectType == "file") {
            val followedFile = files.get(tidObject.id)
            followedFile match {
              case Some(ffile) => {
                followedFiles = followedFiles.++(List((ffile.id, ffile.filename, ffile.contentType)))
              }
              case None =>
            }
          } else if (tidObject.objectType == "dataset") {
            val followedDataset = datasets.get(tidObject.id)
            followedDataset match {
              case Some(fdset) => {
                followedDatasets = followedDatasets.++(List((fdset.id, fdset.name, fdset.description.substring(0, Math.min(maxDescLength, fdset.description.length())))))
              }
              case None =>
            }
          } else if (tidObject.objectType == "collection") {
            val followedCollection = collections.get(tidObject.id)
            followedCollection match {
              case Some(fcoll) => {
                followedCollections = followedCollections.++(List((fcoll.id, fcoll.name, fcoll.description.substring(0, Math.min(maxDescLength, fcoll.description.length())))))
              }
              case None =>
            }
          } else if (tidObject.objectType == "'space") {
            val followedSpace = spaceService.get(tidObject.id)
            followedSpace match {
              case Some(fspace) => {
                followedSpaces = followedSpaces.++(List((fspace.id, fspace.name, fspace.description.substring(0, Math.min(maxDescLength, fspace.description.length())))))
              }
              case None => {}
            }
          }
        }
        Ok(views.html.t2c2dashboard(AppConfiguration.getDisplayName, newsfeedEvents, clowderUser, datasetsUser, datasetcommentMap, decodedCollections.toList, spacesUser, true, followers, followedUsers.take(3),
          followedFiles.take(3), followedDatasets.take(3), followedCollections.take(3),followedSpaces.take(3), Some(true)))
      }
      case _ => Ok(views.html.index(latestFiles, datasetsCount, datasetsCountAccess, filesCount, filesBytes, collectionsCount, collectionsCountAccess,
        spacesCount, spacesCountAccess, usersCount, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
    }
  }

  def submit() = PermissionAction(Permission.CreateVocabulary)(parse.multipartFormData) { implicit request =>
    Logger.debug("------- in Collections.submit ---------")
    val colName = request.body.asFormUrlEncoded.getOrElse("name", null)
    val colKeys = request.body.asFormUrlEncoded.getOrElse("keys", null)
    val colDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
    val colSpace = request.body.asFormUrlEncoded.getOrElse("space", List.empty)

    implicit val user = request.user
    user match {
      case Some(identity) => {
        if (colName == null || colKeys == null || colDesc == null || colSpace == null) {
          val spacesList = spaceService.list()
          var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
          for (aSpace <- spacesList) {
            decodedSpaceList += Utils.decodeSpaceElements(aSpace)
          }
          //This case shouldn't happen as it is validated on the client.
          BadRequest(views.html.newVocabulary("Name, Description, or Space was missing during vocabulary creation.", decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None))
        }

        var vocabulary: Vocabulary = null
        if (colSpace.isEmpty || colSpace(0) == "default" || colSpace(0) == "") {
          vocabulary = Vocabulary(name = colName(0).toString, keys = colKeys(0).split(',').toList, description = colDesc(0), created = new Date, author = Some(identity))
        }
        else {
          val stringSpaces = colSpace(0).split(",").toList
          val colSpaces: List[UUID] = stringSpaces.map(aSpace => if (aSpace != "") UUID(aSpace) else None).filter(_ != None).asInstanceOf[List[UUID]]
          vocabulary = Vocabulary(name = colName(0).toString, keys = colKeys(0).split(',').toList, description = colDesc(0), created = new Date, author = Some(identity),spaces = colSpaces)
        }

        Logger.debug("Saving vocabulary " + vocabulary.name)
        vocabularies.insert(vocabulary)
        vocabulary.spaces.map {
          sp => spaceService.get(sp) match {
            case Some(s) => {
              vocabularies.addToSpace(vocabulary.id, s.id)
              //events.addSourceEvent(request.user, collection.id, collection.name, s.id, s.name, "add_collection_space")
            }
            case None => Logger.error(s"space with id $sp on collection $vocabulary.id doesn't exist.")
          }
        }

        //index collection
        val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
        //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "vocabulary", vocabulary.id,
        //List(("name",vocabulary.name), ("description", vocabulary.description), ("created",dateFormat.format(new Date())))}

        //Add to Events Table
        val option_user = users.findByIdentity(identity)
        events.addObjectEvent(option_user, vocabulary.id, vocabulary.name, "create_vocabulary")

        // redirect to collection page
        current.plugin[AdminsNotifierPlugin].foreach {
          _.sendAdminsNotification(Utils.baseUrl(request), "Collection", "added", vocabulary.id.toString, vocabulary.name)
        }
        Ok("not finished yet")
      }
      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
    }
  }


  /**
   * Vocabulary.
   */
  def vocabulary(id: UUID) = PermissionAction(Permission.ViewVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) {implicit request =>
    implicit val user = request.user
    vocabularies.get(id) match {
      case Some(vocabulary)=> {
        Ok("found vocabulary")
      }
      case None=> BadRequest("No such vocabulary")
    }
  }



}

