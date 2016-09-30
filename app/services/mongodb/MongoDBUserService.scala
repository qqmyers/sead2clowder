package services.mongodb

import com.mongodb.casbah.WriteConcern
import java.util.Date

import com.mongodb.DBObject
import com.mongodb.util.JSON
import com.novus.salat._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models._
import org.bson.types.ObjectId
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import models.Role
import models.UserSpaceAndRole

import scala.collection.mutable.ListBuffer
import play.api.Logger
import services._
import services.mongodb.MongoContext.context
import _root_.util.Direction._
import javax.inject.Inject

/**
 * Wrapper around SecureSocial to get access to the users. There is
 * no save option since all saves should be done through securesocial
 * right now. Eventually this should become a wrapper for
 * securesocial and we use User everywhere.
 *
 *
 */
class MongoDBUserService @Inject() (
  files: FileService,
  datasets: DatasetService,
  collections: CollectionService,
  spaces: SpaceService,
  comments: CommentService,
  events: EventService,
  folders: FolderService,
  metadata: MetadataService,
  curations: CurationService) extends services.UserService {

  // ----------------------------------------------------------------------
  // Code to implement the common CRUD services
  // ----------------------------------------------------------------------

  override def save(user: User): Option[User] = {
    val model = UserDAO.toDBObject(user)

    // If account does not exist, add enabled option
    if (UserDAO.findOneById(new ObjectId(user.id.stringify)).isEmpty) {
      val register = play.Play.application().configuration().getBoolean("registerThroughAdmins", true)
      val admins = play.Play.application().configuration().getString("initialAdmins").split("\\s*,\\s*")
      // enable account. Admins are always enabled.
      if (user.email.exists(p => admins.contains(p))) {
        model.put("active", true)
        model.put("serverAdmin", true)
      } else {
        model.put("active", !register)
        model.put("serverAdmin", false)
      }
      if (user.provider.providerId.equals("userpass")) {
        model.put("termsOfServices", MongoDBObject("accepted" -> true, "acceptedDate" -> new Date, "acceptedVersion" -> AppConfiguration.getTermsOfServicesVersionString))
      }
    }

    UserDAO.dao.save(user, WriteConcern.Safe)
    get(user.id)
  }

  override def get(id: UUID): Option[User] = {
    if (id == User.anonymous.id)
      Some(User.anonymous)
    else
      UserDAO.findOneById(new ObjectId(id.stringify))
  }

  override def delete(id: UUID): Unit = {
    UserDAO.remove(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  override def findLocalAccountByEmail(email: String): Option[User] = findByProvider("userpass", email)

  override def findByProvider(provider: String, id: String): Option[User] = {
    UserDAO.findOne(MongoDBObject("provider.providerId" -> provider, "provider.userId" -> id))
  }

  override def recordLogin(id: UUID): Unit = {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $inc("socialInformation.loginCount" -> 1) ++ $set("socialInformation.lastLogin" -> new Date()))
  }

  override def updateAdmins() {
    play.Play.application().configuration().getString("initialAdmins").trim.split("\\s*,\\s*").filter(_ != "").foreach{e =>
      UserDAO.dao.update(MongoDBObject("email" -> e), $set("serverAdmin" -> true, "active" -> true), upsert=false, multi=true)
    }
  }

  override def getAdmins: List[User] = {
    UserDAO.find(MongoDBObject("serverAdmin" -> true, "active" -> true)).toList
  }

  /**
   * The number of objects that are available based on the filter
   */
  override def count(filter: Option[String]): Long = {
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    UserDAO.count(filterBy)
  }

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   */
  override def list(order: Option[String], direction: Direction, start: Option[String], limit: Integer,
                    filter: Option[String]): List[User] = {
    val startAt = (order, start) match {
      case (Some(o), Some(s)) => {
        direction match {
          case ASC => (o $gte s)
          case DESC => (o $lte s)
        }
      }
      case (_, _) => MongoDBObject()
    }
    // what happens if we sort by user, and a user has uploaded 100 items?
    // how do we know that we need to show page 3 of that user?
    // TODO always sort by date ascending, start is based on user/start combo
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    val raw = UserDAO.find(startAt ++ filterBy)
    val orderedBy = order match {
      case Some(o) => {
        direction match {
          case ASC => raw.sort(MongoDBObject(o -> 1))
          case DESC => raw.sort(MongoDBObject(o -> -1))
        }
      }
      case None => raw
    }
    orderedBy.limit(limit).toList
  }

  override def list(id: Option[String], nextPage: Boolean, limit: Integer): List[User] = {
    val filterDate = id match {
      case Some(d) => {
        if(d == "") {
          MongoDBObject()
        } else if (nextPage) {
          ("_id" $lt new ObjectId(d))
        } else {
          ("_id" $gt new ObjectId(d))
        }
      }
      case None => MongoDBObject()
    }
    val sort = if (id.isDefined && !nextPage) {
      MongoDBObject("_id"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("_id" -> -1) ++ MongoDBObject("name" -> 1)
    }
    if(id.isEmpty || nextPage) {
      UserDAO.find(filterDate).sort(sort).limit(limit).toList
    } else {
      UserDAO.find(filterDate).sort(sort).limit(limit).toList.reverse
    }

  }

  override def updateProfile(id: UUID, profile: Profile) {
    val pson = grater[Profile].asDBObject(profile)
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("profile" -> pson))
  }

  override def updateUserField(id: UUID, field: String, fieldText: Any) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify) ), $set(field -> fieldText))
  }

  override def updateUserFullName(id: UUID, name: String): Unit = {
    collections.updateAuthorFullName(id, name)
    comments.updateAuthorFullName(id, name)
    curations.updateAuthorFullName(id, name)
    datasets.updateAuthorFullName(id, name)
    events.updateAuthorFullName(id, name)
    files.updateAuthorFullName(id, name)
    folders.updateAuthorFullName(id, name)
    metadata.updateAuthorFullName(id, name)
  }

  override def createNewListInUser(email: String, field: String, fieldList: List[Any]) {
    UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldList))
  }

  override def updateRepositoryPreferences(id: UUID, preferences: Map[String, String])  {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("repositoryPreferences" -> preferences))
  }
  /**
   * @see app.services.UserService
   *
   * Implementation of the UserService trait.
   *
   */
  def addUserToSpace(userId: UUID, role: Role, spaceId: UUID): Unit = {
    Logger.debug("add user to space")
    val spaceData = UserSpaceAndRole(spaceId, role)
    val spaceandrole = grater[UserSpaceAndRole].asDBObject(spaceData)
    val result = UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)), $push("spaceandrole" -> spaceandrole))
  }

  /**
   * @see app.services.UserService
   *
   * Implementation of the UserService trait.
   *
   */
  def removeUserFromSpace(userId: UUID, spaceId: UUID): Unit = {
      Logger.debug("remove user from space")
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)),
    		  $pull("spaceandrole" ->  MongoDBObject( "spaceId" -> new ObjectId(spaceId.stringify))), false, false, WriteConcern.Safe)
  }

  /**
   * @see app.services.UserService
   *
   * Implementation of the UserService trait.
   *
   */
  def changeUserRoleInSpace(userId: UUID, role: Role, spaceId: UUID): Unit = {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify), "spaceandrole.spaceId" -> new ObjectId(spaceId.stringify)),
        $set({"spaceandrole.$.role" -> RoleDAO.toDBObject(role)}), false, true, WriteConcern.Safe)
  }

  /**
   * @see app.services.UserService
   *
   * Implementation of the UserService trait.
   *
   */
  def getUserRoleInSpace(userId: UUID, spaceId: UUID): Option[Role] = {
      var retRole: Option[Role] = None
      var found = false

      get(userId) match {
          case Some(aUser) => {
              for (aSpaceAndRole <- aUser.spaceandrole) {
                  if (!found) {
                      if (aSpaceAndRole.spaceId == spaceId) {
                          retRole = Some(aSpaceAndRole.role)
                          found = true
                      }
                  }
              }
          }
          case None => Logger.debug("No user found for getRoleInSpace")
      }

      retRole
  }

  /**
   * @see app.services.UserService
   *
   * Implementation of the UserService trait.
   *
   */
  def listUsersInSpace(spaceId: UUID): List[User] = {
      val retList: ListBuffer[User] = ListBuffer.empty
      for (aUser <- UserDAO.dao.find(MongoDBObject())) {
         for (aSpaceAndRole <- aUser.spaceandrole) {
             if (aSpaceAndRole.spaceId == spaceId) {
                 retList += aUser
             }
         }
      }
      retList.toList
  }

  /**
   * List user roles.
   */
  def listRoles(): List[Role] = {
    RoleDAO.findAll().toList
  }

  /**
   * Add new role.
   */
  def addRole(role: Role): Unit = {
    RoleDAO.insert(role)
  }

  /**
   * Find existing role.
   */
  def findRole(id: String): Option[Role] = {
    RoleDAO.findById(id)
  }

  def findRoleByName(name: String): Option[Role] = {
    RoleDAO.findByName(name)
  }
  /**
   * Delete role.
   */
  def deleteRole(id: String): Unit = {
    RoleDAO.removeById(id)

    // Stored role data in the users table must also be deleted
    // Get only list of users with the updated Role in one of their spaces so we don't fetch them all
    UserDAO.dao.collection.find(MongoDBObject("spaceandrole.role._id" -> new ObjectId(id))).foreach { u =>
      val userid: UUID = u.get("_id") match {
        case i: ObjectId => UUID(i.toString)
        case i: UUID => i
        case None => UUID("")
      }

      // Get list of space+role combination objects for this user
      u.get("spaceandrole") match {
        case sp_roles: BasicDBList => {
          for (sp_role <- sp_roles) {
            sp_role match {
              case s: BasicDBObject => {
                val spaceid: UUID = s.get("spaceId") match {
                  case i: ObjectId => UUID(i.toString)
                  case i: UUID => i
                  case None => UUID("")
                }

                // For each one, check whether this role is the changed one and change if so
                s.get("role") match {
                  case r: BasicDBObject => {
                    val roleid: String = r.get("_id") match {
                      case i: ObjectId => i.toString
                      case i: UUID => i.toString()
                      case None => ""
                    }

                    if (roleid == id) {
                      removeUserFromSpace(userid, spaceid)
                    }

                  }
                  case None => {}
                }
              }
              case None => {}
            }
          }
        }
        case None => {}
      }
    }
  }

  def updateRole(role: Role): Unit = {
    RoleDAO.save(role)

    // Stored role data in the users table must also be updated
    // Get only list of users with the updated Role in one of their spaces so we don't fetch them all
    UserDAO.dao.collection.find(MongoDBObject("spaceandrole.role._id" -> new ObjectId(role.id.stringify))).foreach { u =>
      val userid: UUID = u.get("_id") match {
        case i: ObjectId => UUID(i.toString)
        case i: UUID => i
        case None => UUID("")
      }

      // Get list of space+role combination objects for this user
      u.get("spaceandrole") match {
        case sp_roles: BasicDBList => {
          for (sp_role <- sp_roles) {
            sp_role match {
              case s: BasicDBObject => {
                val spaceid: UUID = s.get("spaceId") match {
                  case i: ObjectId => UUID(i.toString)
                  case i: UUID => i
                  case None => UUID("")
                }

                // For each one, check whether this role is the changed one and change if so
                s.get("role") match {
                  case r: BasicDBObject => {
                    val roleid: UUID = r.get("_id") match {
                      case i: ObjectId => UUID(i.toString)
                      case i: UUID => i
                      case None => UUID("")
                    }

                    if (roleid == role.id)
                      changeUserRoleInSpace(userid, role, spaceid)
                  }
                  case None => {}
                }
              }
              case None => {}
            }
          }
        }
        case None => {}
      }
    }
  }

  override def acceptTermsOfServices(id: UUID): Unit = {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("termsOfServices" -> MongoDBObject("accepted" -> true, "acceptedDate" -> new Date, "acceptedVersion" -> AppConfiguration.getTermsOfServicesVersionString)))
  }

  override def newTermsOfServices(): Unit = {
    UserDAO.dao.update(MongoDBObject("termsOfServices" -> MongoDBObject("$exists" -> 1)), $set("termsOfServices.accepted" -> false), multi=true)
  }

  override def followResource(followerId: UUID, resourceRef: ResourceRef) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
      $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(resourceRef.id, resourceRef.resourceType.toString()))))
  }

  override def unfollowResource(followerId: UUID, resourceRef: ResourceRef) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
      $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(resourceRef.id, resourceRef.resourceType.toString()))))
  }

  override def followFile(followerId: UUID, fileId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(fileId, "file"))))
  }

  override def unfollowFile(followerId: UUID, fileId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(fileId, "file"))))
  }

  override def followDataset(followerId: UUID, datasetId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(datasetId, "dataset"))))
  }

  override def unfollowDataset(followerId: UUID, datasetId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(datasetId, "dataset"))))
  }

  /**
   * Follow a collection.
   */
  override def followCollection(followerId: UUID, collectionId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(collectionId, "collection"))))
  }

  /**
   * Unfollow a collection.
   */
  override def unfollowCollection(followerId: UUID, collectionId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(collectionId, "collection"))))
  }

  /**
   * Follow a user.
   */
  override def followUser(followeeId: UUID, followerId: UUID)
  {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(followeeId, "user"))))
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followeeId.stringify)),
                        $addToSet("followers" -> new ObjectId(followerId.stringify)))
  }

  /**
   * Unfollow a user.
   */
  override def unfollowUser(followeeId: UUID, followerId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(followeeId, "user"))))
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followeeId.stringify)),
                        $pull("followers" -> new ObjectId(followerId.stringify)))
  }

  /**
   * return List of tuples {id, objectType, score}
   *   representing the top N recommendations for an object with followerIDs
   *   This list will also filter out excludeIDs (i.e. items the logged in user already follows)
   */
  override def getTopRecommendations(followerIDs: List[UUID], excludeIDs: List[UUID], num: Int): List[MiniEntity] = {
    val followerIDObjects = followerIDs.map(id => new ObjectId(id.stringify))
    val excludeIDObjects = excludeIDs.map(id => new ObjectId(id.stringify))

    val recs = UserDAO.dao.collection.aggregate(
        MongoDBObject("$match" -> MongoDBObject("_id" -> MongoDBObject("$in" -> followerIDObjects))),
        MongoDBObject("$unwind" -> "$followedEntities"),
        MongoDBObject("$group" -> MongoDBObject(
          "_id" -> "$followedEntities._id",
          "objectType" -> MongoDBObject("$first" -> "$followedEntities.objectType"),
          "score" -> MongoDBObject("$sum" -> 1)
        )),
        MongoDBObject("$match" -> MongoDBObject("_id" -> MongoDBObject("$nin" -> excludeIDObjects))),
        MongoDBObject("$sort" -> MongoDBObject("score" -> -1)),
        MongoDBObject("$limit" -> num)
    )

    recs.results.map(entity => new MiniEntity(
      UUID(entity.as[ObjectId]("_id").toString),
      getEntityName(
        UUID(entity.as[ObjectId]("_id").toString),
        entity.as[String]("objectType")
      ),
      entity.as[String]("objectType"))
    ).toList
  }

  def getEntityName(uuid: UUID, objType: String): String = {
    val default = "Not found"
    objType match {
      case "user" => {
        get(uuid) match {
          case Some(user) => user.fullName
          case None => default
        }
      }
      case "file" => {
        files.get(uuid) match {
          case Some(file) => file.filename
          case None => default
        }
      }
      case "dataset" => {
        datasets.get(uuid) match {
          case Some(dataset) => dataset.name
          case None => default
        }
      }
      case "collection" => {
        collections.get(uuid) match {
          case Some(collection) => collection.name
          case None => default
        }
      }
      case "'space" => {
        spaces.get(uuid) match {
          case Some(space) => space.name
          case None => default
        }
      }
      case _ => default
    }
  }

  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("users")) {}
    }
  }
}


object RoleDAO extends ModelCompanion[Role, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Role, ObjectId](collection = x.collection("roles")) {}
  }

  def findById(id: String): Option[Role] = {
    dao.findOne(MongoDBObject("_id" -> new ObjectId(id)))
  }

  def removeById(id: String) {
    dao.remove(MongoDBObject("_id" -> new ObjectId(id)), WriteConcern.Normal)
  }

  def findByName(name: String): Option[Role] = {
    dao.findOne(MongoDBObject("name" -> name))
  }
}
