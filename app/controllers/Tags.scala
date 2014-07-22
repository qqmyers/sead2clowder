package controllers

import models.Tag

import api.WithPermission
import api.Permission
import scala.collection.mutable.ListBuffer
import play.api.Logger
import scala.collection.mutable.Map
import services.{SectionService, FileService, DatasetService, UserAccessRightsService}
import javax.inject.Inject

/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
class Tags @Inject()(datasets: DatasetService, files: FileService, sections: SectionService, accessRights: UserAccessRightsService) extends SecuredController {

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
	  var weightedTags: Map[String, Integer] = Map()
      
      for(dataset <- datasets.listDatasets){
        for(tag <- dataset.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 4
          else
              weightedTags += ((tagName, 4))
        }
      }
      for(file <- files.listFiles){
        for(tag <- file.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 2
          else
              weightedTags += ((tagName, 2))
        }
      }
      for(section <- sections.listSections){
        for(tag <- section.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 1
          else
              weightedTags += ((tagName, 1))
        }
      }
      
      Logger.debug("thelist: "+ weightedTags.toList.toString)

      Ok(views.html.tagCloud(weightedTags.toList))
  }
  
}

