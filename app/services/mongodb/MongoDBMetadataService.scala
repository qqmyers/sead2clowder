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
import scala.language.postfixOps
import java.util.Date
import com.github.jsonldjava.core._
import com.github.jsonldjava.utils._
import java.util.LinkedHashMap

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

  /** Add metadata to the metadata collection and attach to a section /file/dataset/collection */
  def addMetadata(content_ld: JsValue, context: JsValue, attachedTo: ResourceRef, createdAt: Date, creator: Agent, spaceId: Option[UUID]): JsObject = {
    //Update metadata summary doc attached to the right item, update history list with new MetadataEntry(ies)
    /*Using Salat for now - retrieve summary, update its parts and update via Salat
     * expand Metadata content using submitted context,
     * for each submitted key/value: 
     * use predicates to identify existing label, or
     * create a new definition based on the submitted label
     * add an entry
     * create a metadataentry, add it to the relevant map entry in the history
     */
    val json = new JsObject(Seq(("@context", context), ("content_ld", content_ld)))

    val metadoc = JsonUtils.fromInputStream(new java.io.ByteArrayInputStream(Json.stringify(json).getBytes("UTF-8")))
    val ctxt = new Context().parse(metadoc.asInstanceOf[LinkedHashMap[String, Object]].getOrDefault("@context", ""))
    Logger.info("Context" + ctxt.getPrefixes(false).toString())
    var inverseMap = scala.collection.mutable.Map.empty[String, String]
    val entryIter = ctxt.getPrefixes(false).entrySet().iterator()

    while (entryIter.hasNext()) {
      val entry = entryIter.next()
      inverseMap(entry.getValue()) = entry.getKey()
    }
    Logger.info(inverseMap.toString())

    val summary = getMetadataSummary(attachedTo, spaceId)

    var defs = scala.collection.mutable.Map() ++ summary.defs

    var newDefs = scala.collection.mutable.Map.empty[String, String]
    var newMDEntries = scala.collection.mutable.ListBuffer.empty[MetadataEntry]
    var metadataHistoryMap = collection.mutable.Map() ++ summary.history
    var newTerms = scala.collection.mutable.HashSet.empty[String]
    var metadataEntryJson = scala.collection.mutable.Map() ++ summary.entries
    var newMetadataEntryJson = scala.collection.mutable.Map.empty[String, JsValue]
    //compact after expand removes original context              
    val expanded = JsonLdProcessor.compact(JsonLdProcessor.expand(metadoc), null, new JsonLdOptions())

    val exjson = Json.parse(JsonUtils.toString(expanded))
    val excontent = exjson \\ "https://clowder.ncsa.illinois.edu/metadata#content_ld"
    Logger.info(excontent.apply(0).toString())
    excontent.apply(0).as[JsObject].keys.foreach { uri =>
      { //Add new label defs if they don't currently exist - could reject new terms this way if desired
        Logger.info("Here: " + uri)
        val label = inverseMap.apply(uri)
        if (!(defs.contains(inverseMap.apply(uri)))) {
          newDefs(inverseMap.apply(uri)) = uri
        }
        Logger.info("There: " + Json.toJson(creator).as[JsObject])
        //Now create an entry and add it to the list
        Logger.info(uri + " : " + (excontent.apply(0).as[JsObject] \ uri).toString()) // + (x \ y \\ "@value").as[String]) }}
        val valueToStore = excontent.apply(0).as[JsObject] \ uri match {
          case s: JsString => s.as[String] //String without quotes
          case v: JsValue => v.toString() //string representation of object/array/etc.
        }
        val me = MetadataEntry(UUID.generate(), uri, valueToStore, Json.toJson(creator).as[JsObject].toString(), MDAction.Added.toString(), None, createdAt)
        Logger.info("ME: " + me.toString())
        newMDEntries += me

        newTerms.add(label)

      }
    }
    Logger.info("All keys")
    defs = defs ++ newDefs

    for (label <- newTerms) {
      Logger.info("Pred: " + defs.apply(label))
      val filteredList = newMDEntries.filter(_.uri == defs.apply(label))
      Logger.info("Filtered: " + filteredList.toString())
      //val label = defs.apply(pred)
      //Logger.info("Labelval: " + summary.entries.apply(label).toString())
      val existingSize = summary.entries.applyOrElse(label, { label: String => None }) match {
        case x: JsObject => x.keys.size
        case _ => 0
      }

      val newEntries = Json.toJson(filteredList.map { item => { (item.id.stringify) -> item.value } }.toMap)
      Logger.info("New entries: " + newEntries.toString())
      newMetadataEntryJson = newMetadataEntryJson ++ Map(label -> newEntries)
      Logger.info("new entrymap updated for " + label)
      metadataEntryJson(label) = metadataEntryJson.applyOrElse(label, { label: String => JsObject(Seq.empty) }).as[JsObject] ++ newEntries.as[JsObject]
      Logger.info("entrymap updated for " + label)
      //Add history entry

      metadataHistoryMap(label) = filteredList.toList ++ metadataHistoryMap.applyOrElse(label, { label: String => List[MetadataEntry]() })
      Logger.info("History udpated")
    }
    Logger.info("Done processing")
    //Now - update the metadatasummary with new info (entries, possibly defs, and history...
    val rdf = RdfMetadata(summary.id, summary.attachedTo, metadataEntryJson.toMap, defs.toMap, metadataHistoryMap.toMap)
    //Logger.info("Created RdfMetadata: " + rdf.toString())
    MetadataSummaryDAO.update(MongoDBObject("_id" -> new ObjectId(summary.id.stringify)), rdf, false, false, WriteConcern.Safe)
    Logger.info("Updated")
    //Then return the new entries and any new def(s) to the client...
    val defsjson = Json.toJson(newDefs.toMap)
    new JsObject(Seq("entries" -> Json.toJson(newMetadataEntryJson.toMap), "defs" -> defsjson))
    // excontent.as[JsArray]...foreach { x => Logger.info((x \ "@value").toString()) } 

    //val test = expanded.get(0).asInstanceOf[LinkedHashMap[String, Object]].get("https://clowder.ncsa.illinois.edu/metadata#content").asInstanceOf[List[Object]]..get(0)

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

  def updateMetadata(content: JsValue, context: JsValue, attachedTo: ResourceRef, itemId: String, updatedAt: Date, updator: Agent, spaceId: Option[UUID]) = {

    val json = new JsObject(Seq(("@context", context), ("content", content)))

    val metadoc = JsonUtils.fromInputStream(new java.io.ByteArrayInputStream(Json.stringify(json).getBytes("UTF-8")))

    //compact after expand removes original context
    val toCompact = JsonLdProcessor.expand(metadoc)

    val expanded = JsonLdProcessor.compact(toCompact, null, new JsonLdOptions())
    val exjson = Json.parse(JsonUtils.toString(expanded))
    val excontent = (exjson \\ "https://clowder.ncsa.illinois.edu/metadata#content").head.as[JsObject]
    //Todo: support complex types
    //For simple types, content should have one key/value
    val term = excontent.keys.head
    val updatedVal = excontent \ term

    val summary = getMetadataSummary(attachedTo, spaceId)
    //Find the right entry to remove (it may not exists if already deleted/edited)
    var metadataEntryJson = scala.collection.mutable.Map() ++ summary.entries
    val inverseDefs = summary.defs map { _.swap }
    val existingValues = metadataEntryJson.apply(inverseDefs.get(term).get).as[JsObject]
    (existingValues \ itemId) match {
      case v: JsUndefined => {
        //Return JsUndefined to indicate entry not found
        v
      }
      case _ => {
        //Update the entry
        val uuid = UUID.generate()
        metadataEntryJson(inverseDefs.get(term).get) = (existingValues - itemId) + (uuid.stringify, updatedVal)

        //Add a history entry for the delete
        var metadataHistoryMap = collection.mutable.Map() ++ summary.history
        val me = MetadataEntry(uuid, term, updatedVal.as[String], Json.toJson(updator).as[JsObject].toString(), MDAction.Edited.toString(), Some(itemId), updatedAt)
        metadataHistoryMap(inverseDefs.get(term).get) = List(me) ++ metadataHistoryMap.applyOrElse(inverseDefs.get(term).get, { label: String => List[MetadataEntry]() })
        //Store update
        //Now - update the metadatasummary with new info (entries, possibly defs, and history...
        val rdf = RdfMetadata(summary.id, summary.attachedTo, metadataEntryJson.toMap, summary.defs.toMap, metadataHistoryMap.toMap)
        Logger.info("Created RdfMetadata: " + rdf.toString())
        MetadataSummaryDAO.update(MongoDBObject("_id" -> new ObjectId(summary.id.stringify)), rdf, false, false, WriteConcern.Safe)
        Logger.info("Updated")

        //Return the updated entry to the caller as part of success (to support event about update)
        JsObject(Seq("id" -> JsString(uuid.stringify), "value" -> updatedVal))

      }
    }

  }

  /** Remove metadata, if this metadata does not exist, nothing is executed. Return removed metadata */
  def removeMetadataById(id: UUID) = {
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

  /** Remove metadata, if this metadata does not exist, nothing is executed. Return removed metadata */
  def removeMetadata(attachedTo: ResourceRef, term: String, itemId: String, deletedAt: Date, deletor: Agent, spaceId: Option[UUID]) = {
    val summary = getMetadataSummary(attachedTo, spaceId)
    //Find the right entry to remove (it may not exists if already deleted/edited)
    var metadataEntryJson = scala.collection.mutable.Map() ++ summary.entries
    val inverseDefs = summary.defs map { _.swap }
    val existingValues = metadataEntryJson.apply(inverseDefs.get(term).get)
    (existingValues \ itemId) match {
      case v: JsUndefined => {
        //Return JsUndefined to indicate entry not found
        v
      }
      case _ => {

        //Remove it from entries
        val delVal = (existingValues.as[JsObject] \ itemId).as[String]
        val updatedValues = existingValues.as[JsObject] - itemId
        updatedValues.keys.size match {
          case 0 => metadataEntryJson.remove(inverseDefs.get(term).get)
          case _ => metadataEntryJson(inverseDefs.get(term).get) = updatedValues
        }

        //Add a history entry for the delete
        var metadataHistoryMap = collection.mutable.Map() ++ summary.history
        val me = MetadataEntry(UUID.generate(), term, delVal, Json.toJson(deletor).as[JsObject].toString(), MDAction.Deleted.toString(), Some(itemId), deletedAt)
        metadataHistoryMap(inverseDefs.get(term).get) = List(me) ++ metadataHistoryMap.applyOrElse(inverseDefs.get(term).get, { label: String => List[MetadataEntry]() })
        //Store update
        //Now - update the metadatasummary with new info (entries, possibly defs, and history...
        val rdf = RdfMetadata(summary.id, summary.attachedTo, metadataEntryJson.toMap, summary.defs.toMap, metadataHistoryMap.toMap)
        Logger.info("Created RdfMetadata: " + rdf.toString())
        MetadataSummaryDAO.update(MongoDBObject("_id" -> new ObjectId(summary.id.stringify)), rdf, false, false, WriteConcern.Safe)
        Logger.info("Updated")

        //Return the deleted entry to the caller as part of success (to support event about delete)
        JsObject(Seq(term -> JsString(delVal)))
      }
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

    //FixMe - move out of service...
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

  def getMetadataSummary(resourceRef: ResourceRef, space: Option[UUID]): RdfMetadata = {
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
        var newDefs = scala.collection.mutable.Map.empty[String, String]
        var metadataHistoryMap = scala.collection.mutable.Map.empty[String, List[MetadataEntry]]
        var metadataEntryList = scala.collection.mutable.ListBuffer.empty[MetadataEntry]
        var metadataEntryPreds = Set.empty[String]

        //FixMe - skip existing extractor entries

        getMetadataByAttachTo(resourceRef).map {
          item =>
            {
              item.creator.typeOfAgent match {
                case "cat:user" => {
                  val ldItem = JSONLD.jsonMetadataWithContext(item)
                  val json = JsonUtils.fromInputStream(new java.io.ByteArrayInputStream(Json.stringify(ldItem).getBytes("UTF-8")))
                  val ctxt = new Context().parse(json.asInstanceOf[LinkedHashMap[String, Object]].getOrDefault("@context", ""))
                  Logger.debug("Context" + ctxt.getPrefixes(false).toString())
                  val prefixes = ctxt.getPrefixes(false)
                  val entryIter = prefixes.entrySet().iterator()

                  while (entryIter.hasNext()) {
                    val entry = entryIter.next()
                    metadataDefsMap(entry.getValue()) = entry.getKey()
                  }
                  Logger.debug("json: " + json.toString())
                  val fullItem = Json.parse(JsonUtils.toString(JsonLdProcessor.compact(JsonLdProcessor.expand(json), null, new JsonLdOptions())))
                  var excontent = fullItem \\ "https://clowder.ncsa.illinois.edu/metadata#content"

                  //Do a best-effort to assess whether the entry has non json-ld parts
                  val jsonContent = JSONLD.buildMetadataMap(item.content)
                  val parseAsJson = excontent.size match {
                    case 0 => true
                    case _ => {
                      if (excontent.apply(0).as[JsObject].keys.size != jsonContent.size) {
                        true
                      } else {
                        false
                      }
                    }
                  }
                  if (parseAsJson) {
                    //Kludge - some entries may not have a valid jsonld context mapping the content to the term above. In this case, we can just parse the json 
                    for ((label, value) <- jsonContent) {

                      //is prefixes(label) always defined? If not what?
                      val prefix = prefixes.get(label) match {
                        case null => "https://clowder.ncsa.illinois.edu/metadata/undefined#" + label
                        case s: String => s 
                      }
                      metadataEntryList += MetadataEntry(item.id, prefix, value.as[String], Json.toJson((ldItem).validate[Agent].get).toString, MDAction.Added.toString, None, new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy").parse((ldItem \ "created_at").toString().replace("\"", "")))
                      metadataEntryPreds += prefix
                    }
                    Logger.warn("Invalid JSON-LD for Metadata Entry - parsing as mixed Json/ld: " + ldItem.toString())
                  } else {
                    excontent.apply(0).as[JsObject].keys.foreach { uri =>
                      { //Add new label defs if they don't currently exist - could reject new terms this way if desired

                        val label = metadataDefsMap.apply(uri)
                        newDefs(metadataDefsMap.apply(uri)) = uri
                        //Now create an entry and add it to the list
                        Logger.debug(uri + " : " + (excontent.apply(0).as[JsObject] \ uri).toString()) // + (x \ y \\ "@value").as[String]) }}
                        metadataEntryList += MetadataEntry(UUID.generate(), uri, (excontent.apply(0).as[JsObject] \ uri).as[String], Json.toJson((ldItem).validate[Agent].get).toString, MDAction.Added.toString(), None, new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy").parse((ldItem \ "created_at").toString().replace("\"", "")))
                        metadataEntryPreds += uri
                      }
                    }
                  }
                }
                case "cat:extractor" => {
                  //Leave legacy extractor entries as is
                }
              }

            }
        }

        //Fix me - for now, initialize with label/uri pairs from existing metadata. Going forward,
        //these should added to the space as viewable metadata instead and picked up that way.
        //For now, the last def wins (whether from an entry or because its in the space defs) 

        /* Since preds with '.' (such as URLs!) can't be stored as keys in Mongo docs, we can normalize 
     		 * all labels and then store the label/predicate definition maps and the label/values entries
		     * with labels restricted to being Mongo-safe
		     * 
		     */
        Logger.info("Space: " + space)
        for (md <- getDefinitions(space)) {

          metadataDefsMap((md.json \ "uri").asOpt[String].getOrElse("").toString()) = (md.json \ "label").asOpt[String].getOrElse("").toString()

        }
        //The metadataDefsMap now has all 1-1 predicate to label pairs that will be used for a canonical context plus other entries (e.g. from @vocab statements)

        Logger.info("Entries for: " + metadataEntryPreds.toString())
        val metadataEntryJson = scala.collection.mutable.Map.empty[String, JsValue]
        for (pred <- metadataEntryPreds) {
          val filteredList = metadataEntryList.filter(_.uri == pred)
          metadataEntryJson(metadataDefsMap.apply(pred)) = Json.toJson(filteredList.map { item => { (item.id.stringify) -> item.value } }toMap)

          metadataHistoryMap = metadataHistoryMap ++ Map((metadataDefsMap.apply(pred)).toString -> filteredList.toList)
        }
        Logger.info(metadataEntryJson.toString)
        Logger.info(metadataEntryList.toString)

        //For storage, we now need a canonical (1:1) inverseMetadataDefsMap
        val inverseMetadataDefsMap = scala.collection.mutable.Map.empty[String, String] //needed to convert current metadata

        for ((pred, label) <- metadataDefsMap.filterKeys { x => { metadataEntryPreds.contains(x) } }) {
          inverseMetadataDefsMap(label) = pred
        }

        val rdf = RdfMetadata(UUID.generate(), resourceRef, metadataEntryJson.toMap, inverseMetadataDefsMap.toMap, metadataHistoryMap.toMap)
        val mid = MetadataSummaryDAO.insert(rdf, WriteConcern.Safe)

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

  def getDefinitionByUriAndSpace(uri: String, spaceId: Option[String] = None): Option[MetadataDefinition] = {
    spaceId match {
      case Some(s) => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> new ObjectId(s)))
      case None => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> null))
    }
  }

  def removeDefinitionsBySpace(spaceId: UUID) = {
    MetadataDefinitionDAO.remove(MongoDBObject("spaceId" -> new ObjectId(spaceId.stringify)))
  }

  /** Add vocabulary definitions, leaving it unchanged if the update argument is set to false **/
  def addDefinition(definition: MetadataDefinition): Unit = {
    val uri = (definition.json \ "uri").as[String]
    val result = definition.spaceId match {
      case Some(s) => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> new ObjectId(s.stringify)))
      case None => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> null))
    }
    result match {
      case Some(md) => Logger.debug("Leaving existing vocabulary definition unchanged: " + definition)
      case None => {
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
