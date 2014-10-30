package controllers

import models.Dataset
import models.Tag

import api.WithPermission
import api.Permission
import scala.collection.mutable.ListBuffer
import play.api.Logger
import scala.collection.mutable.Map
import services.{SectionService, FileService, DatasetService, UserAccessRightsService}
import javax.inject.Inject
import play.api.Logger
import services.{CollectionService, DatasetService, FileService, SectionService}
import play.api.Play.current


/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
class Tags @Inject()(collections: CollectionService, datasets: DatasetService, files: FileService, sections: SectionService, accessRights: UserAccessRightsService) extends SecuredController {

  def search(tag: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")
    val datasetsChecker = services.DI.injector.getInstance(classOf[controllers.Datasets])
    val filesChecker = services.DI.injector.getInstance(classOf[controllers.Files])

    val sectionsByTag = sections.findByTag(tagCleaned)
        
    var taggedDatasets: List[models.Dataset] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	taggedDatasets = for (dataset <- datasets.findByTag(tagCleaned); if(datasetsChecker.checkAccessForDatasetUsingRightsList(dataset, request.user , "view", rightsForUser))) yield dataset
	        }
	        case None=>{
	          taggedDatasets = for (dataset <- datasets.findByTag(tagCleaned); if(datasetsChecker.checkAccessForDataset(dataset, request.user , "view"))) yield dataset
	        }
	 }
    
    var taggedFiles: List[models.File] = List.empty
      request.user match{
	        case Some(theUser)=>{
	        	val rightsForUser = accessRights.get(theUser)
	        	taggedFiles = for (file <- files.findByTag(tagCleaned); if(filesChecker.checkAccessForFileUsingRightsList(file, request.user , "view", rightsForUser))) yield file
	        }
	        case None=>{
	          taggedFiles = for (file <- files.findByTag(tagCleaned); if(filesChecker.checkAccessForFile(file, request.user , "view"))) yield file
	        }
	 }
    
    val sectionsWithFiles = for (s <- sectionsByTag; f <- files.get(s.file_id)) yield (s, f)
    Ok(views.html.searchByTag(tag, taggedDatasets, taggedFiles, sectionsWithFiles))
  }

  def tagCloud() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagCloud(computeTagWeights))
  }

  def tagList() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    val tags = computeTagWeights

    val minFont = current.configuration.getDouble("tag.list.minFont").getOrElse(1.0)
    val maxFont = current.configuration.getDouble("tag.list.maxFont").getOrElse(5.0)
    val maxWeight =  tags.maxBy(_._2)._2
    val minWeight =  tags.minBy(_._2)._2
    val divide = (maxFont - minFont) / (maxWeight - minWeight)

    Ok(views.html.tagList(tags.map{case (k, v) => (k, minFont + (v - minWeight) * divide) }))
  }

  def computeTagWeights() = {
    val weightedTags = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)

    // TODO allow for tags in collections
//    for(collection <- collections.listCollections(); tag <- collection.tags) {
//      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.collection").getOrElse(1)
//    }

    for(dataset <- datasets.listDatasets; tag <- dataset.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.dataset").getOrElse(1)
    }

    for(file <- files.listFiles; tag <- file.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.files").getOrElse(1)
    }

    for(section <- sections.listSections; tag <- section.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.sections").getOrElse(1)
    }

    Logger.debug("thelist: "+ weightedTags.toList.toString)
    weightedTags.toList
  }

}


