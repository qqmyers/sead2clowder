package api

import javax.inject.Inject
import api.Permission._
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{Collection, ResourceRef, UUID}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.toJson
import services.{CollectionService, DatasetService}

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
    var count : Long  = collections.countAccess(Set[Permission](Permission.AddResourceToCollection),user,true);
    var limit = count.toInt
    val all_collections_list = for (collection <- collections.listAccess(0,Set[Permission](Permission.AddResourceToCollection),request.user,false))
      yield jsonCollection(collection)
    Ok(toJson(all_collections_list))
  }

  def jsonCollection(collection: Collection): JsValue = {
    var datasetsInCollection = datasets.listCollection(collection.id.stringify)
    var datasetIds = for (dataset<-datasetsInCollection)
      yield (dataset.name +":"+ dataset.id)
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
      "created" -> collection.created.toString,"author"-> collection.author.email.toString,
      "child_collection_ids"-> collection.child_collection_ids.toString, "parent_collection_ids" -> collection.parent_collection_ids.toString,
      "dataset_ids"->datasetIds.mkString(","),
      "childCollectionsCount" -> collection.childCollectionsCount.toString, "datasetCount"-> collection.datasetCount.toString, "spaces" -> collection.spaces.toString))
  }

}
