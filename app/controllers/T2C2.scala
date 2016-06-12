package controllers

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}

import api.Permission
import models._
import play.api.Logger
import play.api.Play.current
import services.{CollectionService, DatasetService, _}
import util.RequiredFieldsConfig

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

@Singleton
class T2C2 @Inject()(vocabularies : VocabularyService, datasets: DatasetService, collections: CollectionService, previewsService: PreviewService,
                     spaceService: SpaceService, users: UserService, events: EventService) extends SecuredController {

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
          vocabulary = Vocabulary(name = colName(0).toString, keys = colKeys(0).split(',').toList, description = colDesc(0).split(',').toList, created = new Date, author = Some(identity))
        }
        else {
          val stringSpaces = colSpace(0).split(",").toList
          val colSpaces: List[UUID] = stringSpaces.map(aSpace => if (aSpace != "") UUID(aSpace) else None).filter(_ != None).asInstanceOf[List[UUID]]
          vocabulary = Vocabulary(name = colName(0).toString, keys = colKeys(0).split(',').toList, description = colDesc(0).split(',').toList, created = new Date, author = Some(identity),spaces = colSpaces)
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

