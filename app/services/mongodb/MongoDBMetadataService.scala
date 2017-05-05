package services.mongodb
import com.mongodb.util.JSON
import org.elasticsearch.action.search.SearchResponse
import play.api.Logger
import play.api.Play._
import play.api.libs.json._
import play.api.libs.json.Json._
import models._
import com.novus.salat.dao.{ ModelCompanion, SalatDAO }
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.libs.json.JsValue
import javax.inject.{ Inject, Singleton }
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import _root_.util.JSONLD
import java.text.SimpleDateFormat

import services.{ ContextLDService, DatasetService, FileService, FolderService, ExtractorMessage, RabbitmqPlugin, MetadataService, ElasticsearchPlugin, CurationService }
import api.{ UserRequest, Permission }

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataService @Inject() (contextService: ContextLDService, datasets: DatasetService, files: FileService, folders: FolderService, curations: CurationService) extends MetadataService {

  /**
   * Add metadata to the metadata collection and attach to a section /file/dataset/collection
   */
  def addMetadata(metadata: Metadata): UUID = {
    // TODO: Update context
    val mid = MetadataDAO.insert(metadata, WriteConcern.Safe)
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin")
      case Some(x) => x.collection(metadata.attachedTo) match {
        case Some(c) => {
          c.update(MongoDBObject("_id" -> new ObjectId(metadata.attachedTo.id.stringify)), $inc("metadataCount" -> +1))
        }
        case None => {
          Logger.error(s"Could not increase counter for ${metadata.attachedTo}")
        }
      }
    }
    UUID(mid.get.toString())
  }

  def getMetadataById(id: UUID): Option[Metadata] = {
    MetadataDAO.findOneById(new ObjectId(id.stringify)) match {
      case Some(metadata) => {
        //TODO link to context based on context id
        Some(metadata)
      }
      case None => None
    }
  }

  /** Get Metadata based on Id of an element (section/file/dataset/collection) */
  def getMetadataByAttachTo(resourceRef: ResourceRef): List[Metadata] = {
    val order = MongoDBObject("createdAt" -> -1)
    MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).sort(order).toList
  }

  /** Get Extractor metadata by attachTo, from a specific extractor if given */
  def getExtractedMetadataByAttachTo(resourceRef: ResourceRef, extractor: String): List[Metadata] = {
    val regex = ".*extractors/" + extractor

    val order = MongoDBObject("createdAt" -> -1)
    MetadataDAO.find(MongoDBObject(
      "attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify),
      // Get only extractors metadata even if specific extractor not given
      "creator.extractorId" -> (regex).r)).sort(order).toList
  }

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(resourceRef: ResourceRef, typeofAgent: String): List[Metadata] = {
    val order = MongoDBObject("createdAt" -> -1)
    val metadata = MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).sort(order)

    for (md <- metadata.toList; if (md.creator.typeOfAgent == typeofAgent)) yield md
  }

  /**
   * Update metadata
   * TODO: implement
   *
   * @param itemId - entry the metadata is attached to
   * @param metadataId
   * @param json
   */
  def updateMetadata(itemId: UUID, metadataId: UUID, json: JsValue) = {}

  /** Remove metadata, if this metadata does not exist, nothing is executed. Return removed metadata */
  def removeMetadata(id: UUID) = {
    getMetadataById(id) match {
      case Some(md) => {
        md.contextId.foreach { cid =>
          if (getMetadataBycontextId(cid).length == 1) {
            contextService.removeContext(cid)
          }
        }
        MetadataDAO.remove(md, WriteConcern.Safe)

        // send extractor message after removed from resource
        val mdMap = Map("metadata" -> md.content,
          "resourceType" -> md.attachedTo.resourceType.name,
          "resourceId" -> md.attachedTo.id.toString)

        //update metadata count for resource
        current.plugin[MongoSalatPlugin] match {
          case None => throw new RuntimeException("No MongoSalatPlugin")
          case Some(x) => x.collection(md.attachedTo) match {
            case Some(c) => {
              c.update(MongoDBObject("_id" -> new ObjectId(md.attachedTo.id.stringify)), $inc("metadataCount" -> -1))
            }
            case None => {
              Logger.error(s"Could not decrease counter for ${md.attachedTo}")
            }
          }
        }
      }
      case None => Logger.debug("No metadata found to remove with UUID " + id.toString)
    }
  }

  def getMetadataBycontextId(contextId: UUID): List[Metadata] = {
    MetadataDAO.find(MongoDBObject("contextId" -> new ObjectId(contextId.toString()))).toList
  }

  def removeMetadataByAttachTo(resourceRef: ResourceRef): Long = {
    val result = MetadataDAO.remove(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify)), WriteConcern.Safe)
    val num_removed = result.getField("n").toString.toLong

    //update metadata count for resource
    resourceRef.resourceType.name match {
      case "dataset" => datasets.incrementMetadataCount(resourceRef.id, (-1 * num_removed))
      case "file" => files.incrementMetadataCount(resourceRef.id, (-1 * num_removed))
      case "curationObject" => curations.incrementMetadataCount(resourceRef.id, (-1 * num_removed))
      case _ => Logger.error(s"Could not decrease metadata counter for ${resourceRef}")
    }

    // send extractor message after attached to resource
    current.plugin[RabbitmqPlugin].foreach { p =>
      val dtkey = s"${p.exchange}.metadata.removed"
      p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, Map[String, Any](
        "resourceType" -> resourceRef.resourceType.name,
        "resourceId" -> resourceRef.id.toString), "", resourceRef.id, ""))
    }

    return num_removed
  }

  /** Remove metadata by attached ID and extractor name **/
  def removeMetadataByAttachToAndExtractor(resourceRef: ResourceRef, extractorName: String): Long = {
    val regex = ".*extractors/" + (extractorName.trim)

    val result = MetadataDAO.remove(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify),
      "creator.extractorId" -> (regex.r)), WriteConcern.Safe)
    val num_removed = result.getField("n").toString.toLong

    //update metadata count for resource
    resourceRef.resourceType.name match {
      case "dataset" => datasets.incrementMetadataCount(resourceRef.id, (-1 * num_removed))
      case "file" => files.incrementMetadataCount(resourceRef.id, (-1 * num_removed))
      case "curationObject" => curations.incrementMetadataCount(resourceRef.id, (-1 * num_removed))
      case _ => Logger.error(s"Could not decrease metadata counter for ${resourceRef}")
    }

    // send extractor message after attached to resource
    current.plugin[RabbitmqPlugin].foreach { p =>
      val dtkey = s"${p.exchange}.metadata.removed"
      p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, Map[String, Any](
        "resourceType" -> resourceRef.resourceType.name,
        "resourceId" -> resourceRef.id.toString), "", resourceRef.id, ""))
    }

    return num_removed
  }

  def getMetadataSummary(resourceRef: ResourceRef, spaceId: Option[UUID]): RdfMetadata = {
    //RDF MD

    //Try to get a cached copy
    MetadataSummaryDAO.findOne(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))) match {
      case Some(x) => {

        /* Since Salat doesn't serialize collections reversibly, we need to parse and rebuild the 
         * history collection manually (or change the design to store each history entry 
         * (e.g. one per term per annotated item) as a separate doc)
         */
        var metadataHistoryMap = scala.collection.mutable.Map.empty[String, List[MetadataEntry]]
        //Get the doc as nested DBObjects/DBLists
        val y = MetadataSummaryDAO.toDBObject(x);
        //Get the history element (an object with a List of MetadataEntries associated with each key
        val z: BasicDBObject = y.getAsOrElse[BasicDBObject]("history", new BasicDBObject())
        
        for (label <- z.keys) {
          // For each entry in the list, corresponding to entries for a given term, 
          // retrieve the list entries and convert them to MetadataEntries
          val list = z.getAsOrElse(label, DBList.empty)
          val newList = list.map(_ match {
            case x: BasicDBList => Some(MetadataEntry(x))
            case _ => None
          }).flatten
          //Add the list for the latest term to the history map
          metadataHistoryMap = metadataHistoryMap ++ Map(label -> newList.toList)
        }
/*
        for ((label: String, list: MongoDBList) <- (MongoDBList)(y.get("history"))) {
          Logger.info(label);
          Logger.info(": " + x.history.apply(label).toList.toString());
          metadataHistoryMap = metadataHistoryMap ++ Map(label -> x.history.apply(label).toList)
        }
        */
        //replace the history entry and return the updated summary
        val rdf = RdfMetadata(x.id, x.attachedTo, x.entries, x.defs, metadataHistoryMap.toMap)
        rdf
      }
      case None => {
        //Otherwise calculate and store a new summary

        val metadataDefsMap = scala.collection.mutable.Map.empty[String, String]
        val inverseMetadataDefsMap = scala.collection.mutable.Map.empty[String, String] //needed to convert current metadata

        var metadataHistoryMap = scala.collection.mutable.Map.empty[String, List[MetadataEntry]]

        var metadataEntryList = scala.collection.mutable.ListBuffer.empty[MetadataEntry]
        var metadataEntryPreds = Set.empty[String]
        getMetadataByAttachTo(resourceRef).map {
          item =>
            {
              val ldItem = JSONLD.jsonMetadataWithContext(item)
              Logger.info((ldItem \ ("@context")).toString())
              (ldItem \ ("@context")) match {
                case a: JsArray => {
                  (a.as[List[JsValue]]).foreach(j => {
                    j match {
                      case o: JsObject => {
                        for ((label, pred) <- o.value) {
                          inverseMetadataDefsMap(label.toString()) = pred.as[String]
                        }
                      }
                      case _ => {
                        Logger.info("Found context entry in array: " + j.toString())
                      }
                    }
                  })
                }
                case o: JsObject => {
                  for ((label, pred) <- o.value) {
                    inverseMetadataDefsMap(label.toString()) = pred.as[String]
                  }
                }
                case _ => {
                  Logger.info("Found context entry: " + (ldItem \ ("@context")).toString())
                }
              }

              for ((label, value) <- JSONLD.buildMetadataMap(item.content)) {
                Logger.info(ldItem.toString)
                Logger.info((ldItem \ "created_at").toString())
                Logger.info((ldItem \ "agent").toString())
                //Logger.info(Json.toJson((ldItem).validate[Agent].get).toString)

                //metadataEntryList += MetadataEntry(item.id, inverseMetadataDefsMap.apply(label), value.as[String], (ldItem).validate[Agent].get, MDAction.Added.toString, new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy").parse((ldItem \ "created_at").toString().replace("\"", "")))
                metadataEntryList += MetadataEntry(item.id, inverseMetadataDefsMap.apply(label), value.as[String], Json.toJson((ldItem).validate[Agent].get).toString, MDAction.Added.toString, new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy").parse((ldItem \ "created_at").toString().replace("\"", "")))
                //metadataEntryList += MetadataEntry(item.id, inverseMetadataDefsMap.apply(label), value.as[String], (ldItem).validate[Agent].get, MDAction.Added.toString, new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy").parse((ldItem \ "created_at").toString().replace("\"", "")))

                metadataEntryPreds += inverseMetadataDefsMap.apply(label)
              }
            }
        }
        //The inverse map has all labels that were used to map to predicates - may be many to one
        Logger.info("Inverse Context: " + inverseMetadataDefsMap.toString())

        //Fix me - for now, initialize with label/uri pairs from existing metadata. Going forward,
        //these should added to the space as viewable metadata instead and picked up that way.
        //For now, the last def wins (whether from an entry or because its in the space defs) 

        /* Since preds with '.' (such as URLs!) can't be stored as keys in Mongo docs, we can normalize 
     * all labels and then store the label/predicate definition maps and the label/values entries
     * with labels restricted to being Mongo-safe
     * 
     */

        for ((label, pred) <- inverseMetadataDefsMap) {
          metadataDefsMap(pred) = label
        }

        for (md <- getDefinitions(spaceId)) {
          metadataDefsMap((md.json \ "uri").asOpt[String].getOrElse("").toString()) = (md.json \ "label").asOpt[String].getOrElse("").toString()

        }
        //The metadataDefsMap now has all 1-1 predicate to label pairs that will be used for a canonical context
        Logger.info("Context: " + metadataDefsMap.toString())

        var metadataEntryJson = scala.collection.mutable.Map.empty[String, JsValue]
        for (pred <- metadataEntryPreds) {
          var current = 0;
          metadataEntryJson = metadataEntryJson ++ Map(metadataDefsMap.apply(pred) -> Json.toJson(metadataEntryList.filter(_.uri == pred).map { item => { current += 1; (current.toString + "_" + item.value.hashCode.toString) -> item.value } }toMap))

          metadataHistoryMap = metadataHistoryMap ++ Map((metadataDefsMap.apply(pred)).toString -> metadataEntryList.filter(_.uri == pred).toList)
        }
        Logger.info(metadataEntryJson.toString)
        Logger.info(metadataEntryList.toString)

        //For storage, we now need a canonical (1:1) inverseMetadataDefsMap
        inverseMetadataDefsMap.clear

        for ((pred, label) <- metadataDefsMap) {
          inverseMetadataDefsMap(label) = pred
        }

        val rdf = RdfMetadata(UUID.generate(), resourceRef, metadataEntryJson.toMap, inverseMetadataDefsMap.toMap, metadataHistoryMap.toMap)
        val mid = MetadataSummaryDAO.insert(rdf, WriteConcern.Safe)
        /*   val me = metadataEntryList.head
    val med = MetadataEntryDAO
    Logger.info("haveDao: " + me.toString())
   val mid = med.insert(me, WriteConcern.Safe)
   */
        current.plugin[MongoSalatPlugin] match {
          case None => throw new RuntimeException("No MongoSalatPlugin")
          case Some(x) =>
            Logger.info(s"Looking good ${mid.toString}")

        }

        rdf
      }
    }
  }

  /** Get metadata context if available  **/
  def getMetadataContext(metadataId: UUID): Option[JsValue] = {
    val md = getMetadataById(metadataId)
    md match {
      case Some(m) => {
        val contextId = m.contextId
        contextId match {
          case Some(id) => contextService.getContextById(id)
          case None => None
        }
      }
      case None => None
    }
  }

  /** Vocabulary definitions for user fields **/
  def getDefinitions(spaceId: Option[UUID] = None): List[MetadataDefinition] = {
    spaceId match {
      case None => MetadataDefinitionDAO.find(MongoDBObject("spaceId" -> null)).toList.sortWith(_.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse(""))
      case Some(s) => MetadataDefinitionDAO.find(MongoDBObject("spaceId" -> new ObjectId(s.stringify))).toList.sortWith(_.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse(""))
    }

  }

  def getDefinitionsDistinctName(user: Option[User]): List[MetadataDefinition] = {
    val filterAccess = if (configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public") {
      MongoDBObject()
    } else {
      val orlist = scala.collection.mutable.ListBuffer.empty[MongoDBObject]
      orlist += MongoDBObject("spaceId" -> null)
      //TODO: Add public space check.
      user match {
        case Some(u) => {
          val okspaces = u.spaceandrole.filter(_.role.permissions.intersect(Set(Permission.ViewMetadata.toString())).nonEmpty)
          if (okspaces.nonEmpty) {
            orlist += ("spaceId" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
          }
          $or(orlist.map(_.asDBObject))
        }
        case None => MongoDBObject()
      }
    }
    MetadataDefinitionDAO.find(filterAccess).toList.groupBy(_.json).map(_._2.head).toList.sortWith(_.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse(""))
  }

  def getDefinition(id: UUID): Option[MetadataDefinition] = {
    MetadataDefinitionDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  def getDefinitionByUri(uri: String): Option[MetadataDefinition] = {
    MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri))
  }

  def getDefinitionByUriAndSpace(uri: String, spaceId: Option[String]): Option[MetadataDefinition] = {
    spaceId match {
      case Some(s) => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> new ObjectId(s)))
      case None => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> null))
    }
  }

  def removeDefinitionsBySpace(spaceId: UUID) = {
    MetadataDefinitionDAO.remove(MongoDBObject("spaceId" -> new ObjectId(spaceId.stringify)))
  }

  /** Add vocabulary definitions, leaving it unchanged if the update argument is set to false **/
  def addDefinition(definition: MetadataDefinition, update: Boolean = true): Unit = {
    val uri = (definition.json \ "uri").as[String]
    MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri)) match {
      case Some(md) => {
        if (update) {
          if (md.spaceId == definition.spaceId) {
            Logger.debug("Updating existing vocabulary definition: " + definition)
            // make sure to use the same id as the old value
            val writeResult = MetadataDefinitionDAO.update(MongoDBObject("json.uri" -> uri), definition.copy(id = md.id),
              false, false, WriteConcern.Normal)
          } else {
            Logger.debug("Adding existing vocabulary definition to a different space" + definition)
            MetadataDefinitionDAO.save(definition)
          }

        } else {
          Logger.debug("Leaving existing vocabulary definition unchanged: " + definition)
        }
      }
      case None => {
        Logger.debug("Adding new vocabulary definition " + definition)
        MetadataDefinitionDAO.save(definition)
      }
    }
  }

  def editDefinition(id: UUID, json: JsValue) = {
    MetadataDefinitionDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("json" -> JSON.parse(json.toString()).asInstanceOf[DBObject]), false, false, WriteConcern.Safe)
  }

  def deleteDefinition(id: UUID): Unit = {
    MetadataDefinitionDAO.remove(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  def searchbyKeyInDataset(key: String, datasetId: UUID): List[Metadata] = {
    val field = "content." + key.trim
    MetadataDAO.find((field $exists true) ++ MongoDBObject("attachedTo.resourceType" -> "dataset") ++ MongoDBObject("attachedTo._id" -> new ObjectId(datasetId.stringify))).toList
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    MetadataDAO.update(MongoDBObject("creator._id" -> new ObjectId(userId.stringify), "creator.typeOfAgent" -> "cat:user"),
      $set("creator.user.fullName" -> fullName, "creator.fullName" -> fullName), false, true, WriteConcern.Safe)
  }
}

object MetadataDAO extends ModelCompanion[Metadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[Metadata, ObjectId](collection = x.collection("metadata")) {}
  }
}

object MetadataSummaryDAO extends ModelCompanion[RdfMetadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[RdfMetadata, ObjectId](collection = x.collection("metadatasummary")) {}
  }
}

object MetadataDefinitionDAO extends ModelCompanion[MetadataDefinition, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[MetadataDefinition, ObjectId](collection = x.collection("metadata.definitions")) {}
  }
}
