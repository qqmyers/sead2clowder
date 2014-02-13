package models

import org.bson.types.ObjectId
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import services.MongoSalatPlugin
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import services.DI
import services.DatasetService
import services.CollectionService
import java.text.SimpleDateFormat
import services.ElasticsearchPlugin
import play.api.Logger

case class Collection (
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  created: Date, 
  datasets: List[Dataset] = List.empty,
  thumbnail_id: Option[String] = None
)

object Collection extends ModelCompanion[Collection, ObjectId]{

  val datasets: DatasetService =  DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService =  DI.injector.getInstance(classOf[CollectionService])
  
   // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Collection, ObjectId](collection = x.collection("collections")) {}
  }
  
  def findOneByDatasetId(dataset_id: ObjectId): Option[Collection] = {
    dao.findOne(MongoDBObject("datasets._id" -> dataset_id))
  }

     /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: String): List[Collection] =  { 
	datasets.get(datasetId) match{
	  case Some(dataset) =>{
	    val list = for (collection <- collections.listCollections(); if(!isInDataset(dataset,collection))) yield collection
	    return list.reverse
	  }
	  case None => {
	    val list = for (collection <- collections.listCollections()) yield collection
        return list.reverse
	  }
	}	     
  }
  
       /**
   * List all collections inside a dataset.
   */
  def listInsideDataset(datasetId: String): List[Collection] =  { 
	datasets.get(datasetId) match{
	  case Some(dataset) =>{
	    val list = for (collection <- collections.listCollections(); if(isInDataset(dataset,collection))) yield collection
	    return list.reverse
	  }
	  case None => {
	    val list = for (collection <- collections.listCollections()) yield collection
        return list.reverse
	  }
	}	     
  }


  def isInDataset(dataset: Dataset, collection: Collection): Boolean = {
    for(dsColls <- dataset.collections){
      if(dsColls == collection.id.toString())
        return true
    }
    return false
  }
  
  def addDataset(collectionId:String, dataset: Dataset){   
    Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId)), $addToSet("datasets" ->  Dataset.toDBObject(dataset)), false, false, WriteConcern.Safe)   
  }
  
  def removeDataset(collectionId:String, dataset: Dataset){
    Collection.update(MongoDBObject("_id" -> new ObjectId(collectionId)), $pull("datasets" ->  MongoDBObject( "_id" -> dataset.id)), false, false, WriteConcern.Safe)
  }
  
  def index(id: String) {
    dao.findOneById(new ObjectId(id)) match {
      case Some(collection) => {
        
        var dsCollsId = ""
        var dsCollsName = ""
          
        for(dataset <- collection.datasets){
          dsCollsId = dsCollsId + dataset.id.toString + " %%% "
          dsCollsName = dsCollsName + dataset.name + " %%% "
        }
	    
	    val formatter = new SimpleDateFormat("dd/MM/yyyy")

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "collection", id,
            List(("name", collection.name), ("description", collection.description), ("created",formatter.format(collection.created)), ("datasetId",dsCollsId),("datasetName",dsCollsName)))
        }
      }
      case None => Logger.error("Collection not found: " + id)
    }
  }
  
}
