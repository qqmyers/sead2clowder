package api

import javax.inject.Inject
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{ResourceRef, UUID}
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import services.DatasetService

/**
  * Created by todd_n on 4/21/16.
  */
@Api(value = "/api/t2c2", description = "Controller for t2c2 routes.")
class T2C2 @Inject() (datasets : DatasetService)  extends ApiController{

  @ApiOperation(value = "List all datasets in a collection", notes = "Returns list of datasets and descriptions.", responseClass = "None", httpMethod = "GET")
  def getDatasetsInCollectionWithColId(collectionId : UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))){implicit request=>
    var datasets_incollection = datasets.listCollection(collectionId.stringify)

    var dataset_name_collectionid = for (dataset <- datasets_incollection)
      yield(Json.obj("name"->dataset.name,"collection"->collectionId))
    Ok(toJson(dataset_name_collectionid))
  }

}
