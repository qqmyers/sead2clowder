package controllers

import javax.inject.{Inject, Singleton}

import api.Permission
import api.Permission._
import play.api.{Logger, Routes}
import play.api.mvc.{Results, Action}
import services._
import models.{UUID, User, Event}
import play.api.Logger

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future


@Singleton
class Dashboard  @Inject() (files: FileService, collections: CollectionService, datasets: DatasetService,
                             spaces: SpaceService, events: EventService, comments: CommentService,
                             sections: SectionService, users: UserService) extends SecuredController {


  def index = UserAction(needActive = false) { implicit request =>
  	implicit val user = request.user
  	val latestFiles = files.latest(5)
    val datasetsCount = datasets.count()
    val datasetsCountAccess = datasets.countAccess(Set[Permission](Permission.ViewDataset), user, request.user.fold(false)(_.superAdminMode))
    val filesCount = files.count()
    val filesBytes = files.bytes()
    val collectionsCount = collections.count()
    val collectionsCountAccess = collections.countAccess(Set[Permission](Permission.ViewCollection), user, request.user.fold(false)(_.superAdminMode))
    val spacesCount = spaces.count()
    val spacesCountAccess = spaces.countAccess(Set[Permission](Permission.ViewSpace), user, request.user.fold(false)(_.superAdminMode))
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
        val spacesUser = spaces.listUser(4, Some(clowderUser),request.user.fold(false)(_.superAdminMode), clowderUser)
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
            val followedSpace = spaces.get(tidObject.id)
            followedSpace match {
              case Some(fspace) => {
                followedSpaces = followedSpaces.++(List((fspace.id, fspace.name, fspace.description.substring(0, Math.min(maxDescLength, fspace.description.length())))))
              }
              case None => {}
            }
          }
        }
        Ok(views.html.dashboard(AppConfiguration.getDisplayName, newsfeedEvents, clowderUser, datasetsUser, datasetcommentMap, decodedCollections.toList, spacesUser, true, followers, followedUsers.take(3),
       followedFiles.take(3), followedDatasets.take(3), followedCollections.take(3),followedSpaces.take(3), Some(true)))
      }
      case _ => (Results.Redirect(securesocial.controllers.routes.LoginPage.login).flashing("error" -> "You must be logged in to access this page."))
        /*
        Ok(views.html.index(latestFiles, datasetsCount, datasetsCountAccess, filesCount, filesBytes, collectionsCount, collectionsCountAccess,
        spacesCount, spacesCountAccess, usersCount, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
        */

    }
  }

}