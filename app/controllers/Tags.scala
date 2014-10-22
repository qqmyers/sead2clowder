package controllers

import api.WithPermission
import api.Permission
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
}
