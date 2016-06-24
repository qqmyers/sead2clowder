package api

import javax.inject.Inject
import api.Permission._
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.toJson
import services.{CollectionService, DatasetService}

import scala.collection.mutable.ListBuffer

/**
  * Created by todd_n on 4/21/16.
  */
@Api(value = "/api/t2c2", description = "Controller for t2c2 routes.")
class T2C2 @Inject() (datasets : DatasetService, collections: CollectionService)  extends ApiController{

  @ApiOperation(value = "List all datasets in a collection", notes = "Returns list of datasets and descriptions.", responseClass = "None", httpMethod = "GET")
  def getDatasetsInCollectionWithColId(collectionId : UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))){implicit request=>
    var datasets_incollection = datasets.listCollection(collectionId.stringify)

    var dataset_name_collectionid = for (dataset <- datasets_incollection)
      yield(Json.obj("id"->dataset.id,"name"->dataset.name,"collection"->collectionId))
    Ok(toJson(dataset_name_collectionid))
  }

  @ApiOperation(value = "Get all collections with dataset ids",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getAllCollectionsWithDatasetIds() = PermissionAction(Permission.ViewCollection) { implicit request =>
    implicit val user = request.user
    val all_collections_list = for (collection <- collections.listAccess(0,Set[Permission](Permission.AddResourceToCollection),request.user,false))
      yield jsonCollection(collection)
    Ok(toJson(all_collections_list))
  }

  def jsonCollection(collection: Collection): JsValue = {
    val datasetsInCollection = datasets.listCollection(collection.id.stringify)
    val datasetIds = for (dataset<-datasetsInCollection)
      yield (dataset.name +":"+ dataset.id)
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString,"author"-> collection.author.email.toString,
      "child_collection_ids"-> collection.child_collection_ids.toString, "parent_collection_ids" -> collection.parent_collection_ids.toString,
      "dataset_ids"->datasetIds.mkString(","),
      "childCollectionsCount" -> collection.childCollectionsCount.toString, "datasetCount"-> collection.datasetCount.toString, "spaces" -> collection.spaces.toString))
  }

  def moveKeysToTermsTemplates() = {
    val allVocabularies : List[Vocabulary] = vocabularies.listAll()
    for (eachVocab <- allVocabularies){
      moveKeysToTerms(eachVocab)
    }
  }

  def moveKeysToTerms(vocabulary : Vocabulary) = {
      val keys : List[String] = vocabulary.keys
      val author = vocabulary.author
      var termsToAdd : ListBuffer[UUID] = ListBuffer.empty[UUID]
      for (key <- keys) {
        val current_term = VocabularyTerm(author = author,key = key, units = "",default_value = "", description = "")
        vocabularyterms.insert(current_term) match {
          case Some(id) => {
            Logger.info("Vocabulary Term inserted")
            termsToAdd += UUID(id)
          }
          case None => Logger.error("Could not insert vocabulary term")
        }
      }
      //edit vocabulary here
      for (term <- termsToAdd){
        vocabularies.addVocabularyTerm(vocabulary.id,term)
      }

  }

  @ApiOperation(value = "Get key values from last dataset",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getKeysValuesFromLastDataset() = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user
    val lastDataset : List[Dataset] = datasets.listAccess(1,Set[Permission](Permission.ViewDataset),user,true)
    val keyValues = getKeyValuePairsFromDataset(lastDataset(0))
    val asMap  = Json.toJson(keyValues)
    //
    Ok(asMap)
  }

  private def getKeyValuePairsFromDataset(dataset : Dataset): Map[String,String] = {
    var key_value_pairs : Map[String,String] = Map.empty[String,String]
    key_value_pairs = key_value_pairs + ("dataset_name" -> dataset.name)
    key_value_pairs = key_value_pairs + ("dataset_id" -> dataset.id.toString())
    val description = dataset.description
    val keyValues = description.split("\n")
    for (pair <- keyValues){
      var currentPair = pair.replace("{","")
      currentPair = currentPair.replace("}","")
      val listPair = currentPair.split(":")
      val first = listPair(0)
      val second = listPair(1)
      key_value_pairs = key_value_pairs + (first -> second)

    }
    return key_value_pairs
  }

  @ApiOperation(value = "Get key values from last dataset",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getKeysValuesFromLastDatasets(limit : Int) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user
    var result : ListBuffer[Map[String,String]] = ListBuffer.empty[Map[String,String]]
    val lastDatasets : List[Dataset] = datasets.listAccess(limit,Set[Permission](Permission.ViewDataset),user,true)
    for (each <- lastDatasets){
      try {
        val currentKeyValues = getKeyValuePairsFromDataset(each)
        result += currentKeyValues
      } catch {
        case e : Exception => Logger.error("could not get key values for " + each.id)
      }
    }
    val asMap  = Json.toJson(result)
    Ok(asMap)
  }

  @ApiOperation(value = "Get key values from dataset id",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getKeysValuesFromDatasetId( id : UUID) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user
    var result : ListBuffer[Map[String,String]] = ListBuffer.empty[Map[String,String]]
    datasets.get(id) match {
      case Some(dataset) => {
        try {
          val currentKeyValues = getKeyValuePairsFromDatasetNoNameId(dataset)
          result += currentKeyValues
        } catch {
          case e : Exception => Logger.error("could not get key values for " + id)
        }
      }
      case None => Logger.error("No dataset found for id " + id)
    }
    val asMap  = Json.toJson(result)
    Ok(asMap)
  }

  private def getKeyValuePairsFromDatasetNoNameId(dataset : Dataset): Map[String,String] = {
    var key_value_pairs : Map[String,String] = Map.empty[String,String]
    val description = dataset.description
    val keyValues = description.split("\n")
    for (pair <- keyValues){
      var currentPair = pair.replace("{","")
      currentPair = currentPair.replace("}","")
      val listPair = currentPair.split(":")
      val first = listPair(0)
      val second = listPair(1)
      key_value_pairs = key_value_pairs + (first -> second)

    }
    return key_value_pairs
  }

  @ApiOperation(value = "Make template public",
    notes = "",
    responseClass = "None", httpMethod = "PUT")
  def makeTemplatePublic(id : UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request=>
    implicit val user = request.user
    vocabularies.get(id) match {
      case Some(vocabulary) => {
        vocabularies.makePublic(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None =>  BadRequest("No template found")
    }
  }

  @ApiOperation(value = "Make template private",
    notes = "",
    responseClass = "None", httpMethod = "PUT")
  def makeTemplatePrivate(id : UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request=>
    implicit val user = request.user
    vocabularies.get(id) match {
      case Some(vocabulary) => {
        vocabularies.makePublic(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None =>  BadRequest("No template found")
    }
  }

  @ApiOperation(value = "get id name from template from tag",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getVocabIdNameFromTag(tag : String) = {
    val tags = List(tag)
    var result : List[JsValue] = List.empty[JsValue]
    val vocabs_with_tag = vocabularies.findByTag(tags,true)
    result  = for (vocab <- vocabs_with_tag)
      yield Json.obj("template_id"->vocab.id,"name"->vocab.name)
    Ok(toJson(result))
  }



}
