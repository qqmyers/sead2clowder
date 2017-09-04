package api

import javax.inject.Inject

import api.Permission.{Permission, files}
import models.{Collection, Dataset, UUID, User}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.toJson
import services.{CollectionService, DatasetService, EventService, UserService}

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

class TreeView  @Inject()(users: UserService,
                          collections: CollectionService,
                          datasets: DatasetService) extends ApiController {

  def getLevelOfTree(currentId : Option[String], currentType : String) = PermissionAction(Permission.ViewCollection) { implicit request =>
    request.user match {
      case Some(usr) => {
        //val result = getChildrenOfNode(currentId, currentType, request.user)
        Ok(toJson("Not implemented"))

      }
      case None => BadRequest("No user supplied")
    }
  }

  def getChildrenOfNode(nodeId : Option[String], nodeType : String, user : Option[User]) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    nodeId match{
      case Some(nId) => {
        if (nodeType == "collection"){
          collections.get(UUID(nId)) match  {
            case Some(col) => {
              if ((col.author.id == user.get.id)){
                var col_children = getChildrenOfCollection(col,user)
                col_children
              } else {
                children.toList
              }
            }
            case None => {
              children.toList
            }
          }
        } else if (nodeType == "dataset"){
          datasets.get(UUID(nodeId.get)) match {
            case Some(ds) => {
              if (ds.author.id == user.get.id){
                var ds_children = getChildrenOfDataset(ds,user)
                ds_children
              } else{
                children.toList
              }
            }
            case None => {
              children.toList
            }
          }
        } else {
          children.toList
        }
      }
      case None =>{
        val result = getCurrentLevelOfTree(nodeId,nodeType, user)
        result
      }
    }
  }

  def getChildrenOfCollection(collection : Collection, user : Option[User]) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    val datasetsInCollection = (datasets.listCollection(collection.id.stringify, user)).filter(( d : models.Dataset) => ((d.author.id == user.get.id) && (!d.isTrash)))
    for (ds <- datasetsInCollection){
      val hasChildren = if (ds.files.size > 0){
        true
      } else {
        false
      }
      var data = Json.obj("thumbnail_id"->ds.thumbnail_id)
      var currentJson = Json.obj("id"->ds.id,"text"->ds.name,"type"->"dataset","children"->hasChildren,"icon"->"glyphicon glyphicon-briefcase","data"->data)
      children += currentJson
    }
    for (child_id <- collection.child_collection_ids){
      collections.get(child_id) match {
        case Some(child) => {
          if ((child.author.id == user.get.id)){
            val hasChildren = if (!child.child_collection_ids.isEmpty || child.datasetCount > 0){
              true
            } else {
              false
            }
            var data = Json.obj("thumbnail_id"->child.thumbnail_id)
            var currentJson = Json.obj("id"->child.id,"text"->child.name,"type"->"collection", "children"->hasChildren,"data"->data)
            children += currentJson
          }
        }
      }
    }
    children.toList
  }

  def getChildrenOfDataset(dataset : Dataset, user  : Option[User]) : List[JsValue] = {
    var children : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    var files_inside = dataset.files
    for (f <- files_inside){
      files.get(f) match {
        case Some(file) => {
          file.thumbnail_id match {
            case Some(thumbnail) =>{
              var data = Json.obj("thumbnail_id"->file.thumbnail_id)
              var currentJson = Json.obj("id"->file.id,"text"->file.filename,"type"->"file","icon" -> "glyphicon glyphicon-file","data"->data)
              children += currentJson
            }
            case None => {
              var data = Json.obj("thumbnail_id"->file.thumbnail_id)
              var currentJson = Json.obj("id"->file.id,"text"->file.filename,"type"->"file","icon" -> "glyphicon glyphicon-file","data"->data)
              children += currentJson
            }
          }

        }
      }
    }
    children.toList
  }

  def getCurrentLevelOfTree(currentId : Option[String], currentType : String, user : Option[User]) : List[JsValue] = {
    var level : ListBuffer[JsValue] = ListBuffer.empty[JsValue]
    if (currentType == "collection"){
      currentId match {
        //not root level
        case Some(col_id) => {
          collections.get(UUID(col_id)) match {
            case Some(col) => {
              if ((col.author.id == user.get.id) && (!col.isTrash)){
                val children = getChildrenOfCollection( col,user)
                val hasChildren = if (children.size > 0){
                  true
                } else {
                  false
                }
                val currentType = if (col.parent_collection_ids.isEmpty){
                  "root"
                } else {
                  "file"
                }
                var data = Json.obj("data"->col.thumbnail_id)
                val current = Json.obj("id"->col.id,"text"->col.name,"type"->"collection","children"->hasChildren,"data"->data)
                level += current
                level.toList
              } else {
                val current = Json.obj("status"->"error")
                level += current
                level.toList
              }

            }
            case None => {
              val current = Json.obj("status"->"error")
              level += current
              level.toList
            }
          }
        }
        //root level
        case None => {
          for (collection <- ((collections.listAllCollections(user.get, false, 0))).filter((c : models.Collection)=> ( c.author.id == user.get.id))) {
            if (collectionIsRootHasNoParentBelongingToMe(collection,user.get)) {
              val hasChildren = if (collection.child_collection_ids.size > 0 || collection.datasetCount > 0){
                true
              } else {
                false
              }
              var data = Json.obj("thumbnail_id"->collection.thumbnail_id)
              val current = Json.obj("id"->collection.id,"text"->collection.name,"type"->"collection","children"->hasChildren,"data"->data)
              level += current
            }
          }
          //TODO fix this
          val orphanDatasets = getOrphanDatasetsMine(user.get)
          for (orphan <- orphanDatasets){
            var hasChildren = false
            if (orphan.files.size > 0 ){
              hasChildren = true
            }
            var data = Json.obj("thumbnail_id"->orphan.thumbnail_id)
            var currentJson = Json.obj("id"->orphan.id,"text"->orphan.name,"type"->"dataset","children"->hasChildren,"icon"->"glyphicon glyphicon-briefcase","data"->data)
            level += currentJson
          }
          level.toList
        }
      }
    } else if (currentType == "dataset"){
      currentId match {
        case Some(ds_id) => {
          datasets.get(UUID(ds_id)) match {
            case Some(ds) =>{
              if ((ds.author.id == user.get.id) && (!ds.isTrash)){
                val children = getChildrenOfDataset( ds, user)
                val hasChildren = if (children.size > 0){
                  true
                } else {
                  false
                }
                val current = Json.obj("id"->ds.id,"text"->ds.name,"type"->"file","children"->hasChildren,"data"->"test")
                level += current
                level.toList
              } else {
                val current = Json.obj("status"->"error")
                level += current
                level.toList
              }
            }
            case None =>
              val current = Json.obj("status"->"error")
              level += current
              level.toList
          }

        }
        case None => {
          val current = Json.obj("status"->"error")
          level += current
          level.toList
        }
      }
    } else {
      val current = Json.obj("status"->"error")
      level += current
      level.toList
    }
  }

  def collectionIsRootHasNoParentBelongingToMe(collection : Collection, user : User): Boolean ={
    var isRootHasNoParentBelongingToMe = false
    if (collections.hasRoot(collection)){
      isRootHasNoParentBelongingToMe = true
      var parent_ids = collection.parent_collection_ids
      for (each <- parent_ids){
        collections.get(each) match {
          case Some(parent) => {
            if (parent.author.id == user.id){
              if (parent.isTrash){
                isRootHasNoParentBelongingToMe = true
              } else {
                isRootHasNoParentBelongingToMe = false
              }
            }
          }
          case None => {

          }
        }
      }
    } else {
      var parent_ids = collection.parent_collection_ids
      var allParentsTrash = true
      for (each <- parent_ids){
        collections.get(each) match {
          case Some(parent) => {
            if (parent.author.id == user.id){
              if (!parent.isTrash){
                allParentsTrash = false
              }
            }
          }
          case None => {

          }
        }
      }
      if (allParentsTrash){
        isRootHasNoParentBelongingToMe = true
      }
    }
    return isRootHasNoParentBelongingToMe
  }

  def getOrphanDatasetsMine(user : User) : List[models.Dataset] = {
    var orphans : ListBuffer[Dataset] = ListBuffer.empty[Dataset]

    val myDatasets = datasets.listUser(0,Some(user),false,user)
    for (dataset <- myDatasets){
      if (!datasetHasParentCreatedByMe(dataset,user)){
        orphans+=dataset
      }
    }
    orphans.toList
  }

  def datasetHasParentCreatedByMe(dataset : models.Dataset, user : User) : Boolean = {
    var hasParentCreatedByMe = false
    val collectionsOfDataset = dataset.collections
    for (col_id <- collectionsOfDataset){
      collections.get(col_id) match {
        case Some(col) => {
          if (col.author.id == user.id){
            val trash = col.trash
            if (col.trash){
              hasParentCreatedByMe = false
            } else {
              hasParentCreatedByMe = true
            }
          }
        }
        case None => {
          Logger.info("Did not find collection")
          datasets.removeCollection(dataset.id,col_id)
        }
      }
    }

    return hasParentCreatedByMe
  }
}
