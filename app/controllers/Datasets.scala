package controllers

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import java.io.FileInputStream
import play.api.Play.current
import services._
import java.util.Date
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import models._
import fileutils.FilesUtils
import api.Permission
import javax.inject.Inject
import scala.Some
import services.ExtractorMessage
import api.WithPermission
import securesocial.core.Identity


/**
 * A dataset is a collection of files and streams.
 *
 * @author Luigi Marini
 * @author Constantinos Sophocleous
 *
 */
class Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  comments: CommentService,
  sections: SectionService,
  previewsService: PreviewService,
  extractions: ExtractionService,
  accessRights: UserAccessRightsService,
  sparql: RdfSPARQLService,
  appConfiguration: AppConfigurationService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * New dataset form.
   */
  val datasetForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
      ((name, description) => Dataset(name = name, description = description, created = new Date, author = null))
      ((dataset: Dataset) => Some((dataset.name, dataset.description)))
  )

  def newDataset() = SecuredAction(authorization = WithPermission(Permission.CreateDatasets)) {
    implicit request =>
      implicit val user = request.user
      val filesChecker = services.DI.injector.getInstance(classOf[controllers.Files])
      var filesList: List[(String, String)] = List.empty          
	          user match{
		        case Some(theUser)=>{
		            val rightsForUser = accessRights.get(theUser)
		            filesList = for (file <- files.listFilesNotIntermediate.sortBy(_.filename); if filesChecker.checkAccessForFileUsingRightsList(file, user, "view", rightsForUser)) yield (file.id.toString(), file.filename)
		        }
		        case None=>{
		          filesList = for (file <- files.listFilesNotIntermediate.sortBy(_.filename); if filesChecker.checkAccessForFile(file, user, "view")) yield (file.id.toString(), file.filename)
		        }
		      }
      
      Ok(views.html.newDataset(datasetForm, filesList)).flashing("error" -> "Please select ONE file (upload new or existing)")
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization = WithPermission(Permission.ListDatasets)) {
	    implicit request =>
	      implicit val user = request.user
	      var rightsForUser: Option[models.UserPermissions] = None
	      user match{
			        case Some(theUser)=>{
			            rightsForUser = accessRights.get(theUser)
			        }
			        case None=>{
			        }
	      }
	      
	      var direction = "b"
	      if (when != "") direction = when
	      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
	      var prev, next = ""
	      var datasetList = List.empty[models.Dataset]
	      if (direction == "b") {
	        datasetList = datasets.listDatasetsBefore(date, limit, user)
	      } else if (direction == "a") {
	        datasetList = datasets.listDatasetsAfter(date, limit, user)
	      } else {
	        badRequest
	      }
	      // latest object
	      val latest = datasets.latest(user)
	      // first object
	      val first = datasets.first(user)
	      var firstPage = false
	      var lastPage = false
	      if (latest.size == 1) {
	        firstPage = datasetList.exists(_.id.equals(latest.get.id))
	        lastPage = datasetList.exists(_.id.equals(first.get.id))
	        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
	        Logger.debug("first " + first.get.id + " last page " + lastPage)
	      }
	      if (datasetList.size > 0) {
	        if (date != "" && !firstPage) {
	          // show prev button
	          prev = formatter.format(datasetList.head.created)
	        }
	        if (!lastPage) {
	          // show next button
	          next = formatter.format(datasetList.last.created)
	        }
	      }
	      Ok(views.html.datasetList(datasetList, prev, next, limit, rightsForUser))
	  }

  

  /**
   * Dataset.	
   */
  def dataset(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset), resourceId = Some(id)) {
	    implicit request =>
	      implicit val user = request.user
	      val filesChecker = services.DI.injector.getInstance(classOf[controllers.Files]) 
	      Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
	      datasets.get(id) match {
	        case Some(dataset) => {
	          var filesInDataset: List[File] = List.empty
	          var rightsForUser: Option[models.UserPermissions] = None
	          
	          user match{
		        case Some(theUser)=>{
		            rightsForUser = accessRights.get(theUser)
		        	for(checkedFile <- dataset.files.map(f => files.get(f.id).get)){
			            if(filesChecker.checkAccessForFileUsingRightsList(checkedFile, user, "view", rightsForUser))
			              filesInDataset = filesInDataset :+ checkedFile
			        }
		        }
		        case None=>{
		          for(checkedFile <- dataset.files.map(f => files.get(f.id).get)){
		            if(filesChecker.checkAccessForFile(checkedFile, user, "view"))
		              filesInDataset = filesInDataset :+ checkedFile
		          }
		        }
		      }
	          
	          //Search whether dataset is currently being processed by extractor(s)
	          var isActivity = false
	          try {
	            for (f <- filesInDataset) {
	              extractions.findIfBeingProcessed(f.id) match {
	                case false =>
	                case true => isActivity = true; throw ActivityFound
	              }
	            }
	          } catch {
	            case ActivityFound =>
	          }

	          var filesInDatasetWithSections: List[File] = List.empty
	          for (f <- filesInDataset) {
	        	    val sectionsByFile = sections.findByFileId(UUID(f.id.toString))
			        val sectionsWithPreviews = sectionsByFile.map { s =>
			          val p = previewsService.findBySectionId(s.id)
			          s.copy(preview = Some(p(0)))
			        }
	        	    filesInDatasetWithSections = filesInDatasetWithSections :+ f.copy(sections = sectionsWithPreviews)
	          }

	          val datasetWithFiles = dataset.copy(files = filesInDatasetWithSections)
	          val previewers = Previewers.findPreviewers
	          val previewslist = for (f <- datasetWithFiles.files) yield {
	            val pvf = for (p <- previewers; pv <- f.previews; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield {
	              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
	            }
	            if (pvf.length > 0) {
	              (f -> pvf)
	            } else {
	              val ff = for (p <- previewers; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
	                (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id).toString, f.contentType, f.length)
	              }
	              (f -> ff)
	            }
	          }
	          val previews = Map(previewslist: _*)
	          val metadata = datasets.getMetadata(id)
	          Logger.debug("Metadata: " + metadata)
	          for (md <- metadata) {
	            Logger.debug(md.toString)
	          }
	          val userMetadata = datasets.getUserMetadata(id)
	          Logger.debug("User metadata: " + userMetadata.toString)

	          var collectionsOutside: List[models.Collection]  = List.empty
	          var collectionsInside: List[models.Collection]  = List.empty
	          
	          var filesOutside: List[models.File] = List.empty
	          var collectionsChecker = services.DI.injector.getInstance(classOf[controllers.Collections])
	          user match{
		        case Some(theUser)=>{
		            filesOutside = for(checkedFile <- files.listOutsideDataset(id).sortBy(_.filename); if(filesChecker.checkAccessForFileUsingRightsList(checkedFile, user, "view", rightsForUser))) yield checkedFile
		            collectionsOutside = for(checkedCollection <- collections.listOutsideDataset(id).sortBy(_.name); if(collectionsChecker.checkAccessForCollectionUsingRightsList(checkedCollection, user, "modify", rightsForUser))) yield checkedCollection
		            collectionsInside = for(checkedCollection <- collections.listInsideDataset(id).sortBy(_.name); if(collectionsChecker.checkAccessForCollectionUsingRightsList(checkedCollection, user, "view", rightsForUser))) yield checkedCollection
		        }
		        case None=>{
		          filesOutside = for(checkedFile <- files.listOutsideDataset(id).sortBy(_.filename); if(filesChecker.checkAccessForFile(checkedFile, user, "view"))) yield checkedFile
		          collectionsOutside = for(checkedCollection <- collections.listOutsideDataset(id).sortBy(_.name); if(collectionsChecker.checkAccessForCollection(checkedCollection, user, "modify"))) yield checkedCollection
		          collectionsInside = for(checkedCollection <- collections.listInsideDataset(id).sortBy(_.name); if(collectionsChecker.checkAccessForCollection(checkedCollection, user, "view"))) yield checkedCollection
		        }
		      }

	          var commentsByDataset = comments.findCommentsByDatasetId(id)
	          filesInDataset.map {
	            file =>
	              commentsByDataset ++= comments.findCommentsByFileId(file.id)
	              sections.findByFileId(UUID(file.id.toString)).map { section =>
	                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
	              }
	          }
	          commentsByDataset = commentsByDataset.sortBy(_.posted)

	          val isRDFExportEnabled = current.plugin[RDFExportService].isDefined

	          Ok(views.html.dataset(datasetWithFiles, commentsByDataset, previews, metadata, userMetadata, isActivity, collectionsOutside, collectionsInside, filesOutside, isRDFExportEnabled, rightsForUser))
	        }
	        case None => {
	          Logger.error("Error getting dataset" + id); InternalServerError
	        }
	    }
	  }

  /**
   * 3D Dataset.
   */
  def datasetThreeDim(id: String) = SecuredAction(authorization=WithPermission(Permission.ShowDataset), resourceId = Some(UUID(id))) { implicit request =>
    implicit val user = request.user
    val filesChecker = services.DI.injector.getInstance(classOf[controllers.Files])
    Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
    datasets.get(UUID(id))  match {
      case Some(dataset) => {
        var filesInDataset: List[File] = List.empty
	          var rightsForUser: Option[models.UserPermissions] = None
	          
	          user match{
		        case Some(theUser)=>{
		            rightsForUser = accessRights.get(theUser)
		        	for(checkedFile <- dataset.files.map(f => files.get(f.id).get)){
			            if(filesChecker.checkAccessForFileUsingRightsList(checkedFile, user, "view", rightsForUser))
			              filesInDataset = filesInDataset :+ checkedFile
			        }
		        }
		        case None=>{
		          for(checkedFile <- dataset.files.map(f => files.get(f.id).get)){
		            if(filesChecker.checkAccessForFile(checkedFile, user, "view"))
		              filesInDataset = filesInDataset :+ checkedFile
		          }
		        }
		      }

	          //Search whether dataset is currently being processed by extractor(s)
	          var isActivity = false
	          try {
	            for (f <- filesInDataset) {
	              extractions.findIfBeingProcessed(f.id) match {
	                case false =>
	                case true => isActivity = true; throw ActivityFound
	              }
	            }
	          } catch {
	            case ActivityFound =>
	          }


	          val datasetWithFiles = dataset.copy(files = filesInDataset)
	          val previewers = Previewers.findPreviewers
	          val previewslist = for (f <- datasetWithFiles.files) yield {
	            val pvf = for (p <- previewers; pv <- f.previews; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield {
	              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
	            }
	            if (pvf.length > 0) {
	              (f -> pvf)
	            } else {
	              val ff = for (p <- previewers; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
	                (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id).toString, f.contentType, f.length)
	              }
	              (f -> ff)
	            }
	          }
	          val previews = Map(previewslist: _*)
	          val metadata = datasets.getMetadata(UUID(id))
	          Logger.debug("Metadata: " + metadata)
	          for (md <- metadata) {
	            Logger.debug(md.toString)
	          }
	          val userMetadata = datasets.getUserMetadata(UUID(id))
	          Logger.debug("User metadata: " + userMetadata.toString)

	          var collectionsOutside: List[models.Collection]  = List.empty
	          var collectionsInside: List[models.Collection]  = List.empty
	          
	          var filesOutside: List[models.File] = List.empty
	          var collectionsChecker = services.DI.injector.getInstance(classOf[controllers.Collections])
	          val idUUID = UUID(id)
	          user match{
		        case Some(theUser)=>{
		            filesOutside = for(checkedFile <- files.listOutsideDataset(idUUID).sortBy(_.filename); if(filesChecker.checkAccessForFileUsingRightsList(checkedFile, user, "view", rightsForUser))) yield checkedFile
		            collectionsOutside = for(checkedCollection <- collections.listOutsideDataset(idUUID).sortBy(_.name); if(collectionsChecker.checkAccessForCollectionUsingRightsList(checkedCollection, user, "modify", rightsForUser))) yield checkedCollection
		            collectionsInside = for(checkedCollection <- collections.listInsideDataset(idUUID).sortBy(_.name); if(collectionsChecker.checkAccessForCollectionUsingRightsList(checkedCollection, user, "modify", rightsForUser))) yield checkedCollection
		        }
		        case None=>{
		          filesOutside = for(checkedFile <- files.listOutsideDataset(idUUID).sortBy(_.filename); if(filesChecker.checkAccessForFile(checkedFile, user, "view"))) yield checkedFile
		          collectionsOutside = for(checkedCollection <- collections.listOutsideDataset(idUUID).sortBy(_.name); if(collectionsChecker.checkAccessForCollection(checkedCollection, user, "modify"))) yield checkedCollection
		          collectionsInside = for(checkedCollection <- collections.listInsideDataset(idUUID).sortBy(_.name); if(collectionsChecker.checkAccessForCollection(checkedCollection, user, "modify"))) yield checkedCollection
		        }
		      }

	          var commentsByDataset = comments.findCommentsByDatasetId(UUID(id))
	          filesInDataset.map {
	            file =>
	              commentsByDataset ++= comments.findCommentsByFileId(file.id)
	              sections.findByFileId(UUID(file.id.toString)).map { section =>
	                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
	              }
	          }
	          commentsByDataset = commentsByDataset.sortBy(_.posted)
        
        Ok(views.html.datasetThreeDim(datasetWithFiles, commentsByDataset, previews, metadata, userMetadata, isActivity, collectionsOutside, collectionsInside, filesOutside, rightsForUser))
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
  }
  
  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset)) {
    request =>
      sections.get(section_id) match {
        case Some(section) => {
          datasets.findOneByFileId(section.file_id) match {
            case Some(dataset) => Redirect(routes.Datasets.dataset(dataset.id))
            case None => InternalServerError("Dataset not found")
          }
        }
        case None => InternalServerError("Section not found")
      }
  }

  /**
   * TODO where is this used?
  def upload = Action(parse.temporaryFile) { request =>
    request.body.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }
   */

  /**
   * Upload file.
   */
  def submit() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateDatasets)) { implicit request =>
    implicit val user = request.user
    
    user match {
      case Some(identity) => {
        datasetForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newDataset(errors, for(file <- files.listFilesNotIntermediate.sortBy(_.filename)) yield (file.id.toString(), file.filename))),
	      dataset => {
	           request.body.file("file").map { f =>
	             //Uploaded file selected
	             
	             //Can't have both an uploaded file and a selected existing file
	             request.body.asFormUrlEncoded.get("existingFile").get(0).equals("__nofile") match{
	               case true => {
	            	    var nameOfFile = f.filename
			            var flags = ""
			            if(nameOfFile.toLowerCase().endsWith(".ptm")){
			              var thirdSeparatorIndex = nameOfFile.indexOf("__")
			              if(thirdSeparatorIndex >= 0){
			                var firstSeparatorIndex = nameOfFile.indexOf("_")
			                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
			            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
			            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
			              }
			            }
		
	            	    Logger.debug("Uploading file " + nameOfFile)
				        
				        // store file
	            	    Logger.info("Adding file" + identity)
	            	    val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
	            	    var isFilePublicOption = request.body.asFormUrlEncoded.get("filePrivatePublic")
				        if(!isFilePublicOption.isDefined)
				          isFilePublicOption = Some(List("false"))	        
				        val isFilePublic = isFilePublicOption.get(0).toBoolean
	            	    
	            	    val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews, isFilePublic)
					    Logger.debug("Uploaded file id is " + file.get.id)
					    Logger.debug("Uploaded file type is " + f.contentType)
					    
					    val uploadedFile = f
					    file match {
					      case Some(f) => {
					        					        
					        val id = f.id
					        accessRights.addPermissionLevel(request.user.get, id.stringify, "file", "administrate")
			                if(showPreviews.equals("FileLevel"))
			                	flags = flags + "+filelevelshowpreviews"
			                else if(showPreviews.equals("None"))
			                	flags = flags + "+nopreviews"
					        var fileType = f.contentType
					        FilesUtils.getFilePrioritizedType(nameOfFile) match{
			                  case ""=>{}
			                  case customType=>{
			                    fileType = customType
			                  }
			                } 
					        
					        if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
					          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")			          
					          if(fileType.startsWith("ERROR: ")){
					             Logger.error(fileType.substring(7))
					             InternalServerError(fileType.substring(7))
					          }
					          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
					            if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
					            
					        	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
					              if(thirdSeparatorIndex >= 0){
					                var firstSeparatorIndex = nameOfFile.indexOf("_")
					                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
					            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
					            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
					            	files.renameFile(f.id, nameOfFile)
					              }
					        	  files.setContentType(f.id, fileType)
					          }
					        }
					        else if(nameOfFile.toLowerCase().endsWith(".mov")){
							  fileType = "ambiguous/mov";
					        }
					        
					        if(nameOfFile.startsWith("MEDICI2ZIPPED_")){
					        	nameOfFile = nameOfFile.replaceFirst("MEDICI2ZIPPED_","")
					        	files.renameFile(f.id, nameOfFile)
							}
				            else if(nameOfFile.startsWith("MEDICI2MULTISPECTRAL_")){
								        	nameOfFile = nameOfFile.replaceFirst("MEDICI2MULTISPECTRAL_","")
								        	files.renameFile(f.id, nameOfFile)
							}
					        
					        current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
					        
					    	// TODO RK need to replace unknown with the server name
					    	val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
		//			        val key = "unknown." + "file."+ "application.x-ptm"
					    	
			                // TODO RK : need figure out if we can use https
			                val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
		      				        
					    	var isDatasetPublicOption = request.body.asFormUrlEncoded.get("datasetPrivatePublic")
					        if(!isDatasetPublicOption.isDefined)
					          isDatasetPublicOption = Some(List("false"))	        
					        val isDatasetPublic = isDatasetPublicOption.get(0).toBoolean
					    	
					        // add file to dataset
					        val dt = dataset.copy(files = List(f), author=identity, isPublic=Some(isDatasetPublic))
					        accessRights.addPermissionLevel(request.user.get, dt.id.stringify, "dataset", "administrate")
					        // TODO create a service instead of calling salat directly
				            datasets.update(dt)				            
				            
						    current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dt.id, flags))}
					        
					        val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
					        
					        //for metadata files
							  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
								  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
										  files.addXMLMetadata(f.id, xmlToJSON)
										  datasets.addXMLMetadata(dt.id, f.id, xmlToJSON)
		
										  Logger.debug("xmlmd=" + xmlToJSON)
										  
										  //index the file
										  current.plugin[ElasticsearchPlugin].foreach{
								  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dt.id.toString()),("datasetName",dt.name), ("xmlmetadata", xmlToJSON)))
								  		  }
								  		  // index dataset
								  		  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
								  		  List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
							  }
							  else{
								  //index the file
								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dt.id.toString),("datasetName",dt.name)))}
								  // index dataset
								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
								  List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName","")))}
							  }
					    	// TODO RK need to replace unknown with the server name and dataset type		            
		 			    	val dtkey = "unknown." + "dataset."+ "unknown"
					        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id, dt.id, host, dtkey, Map.empty, "0", dt.id, ""))}
		 			    	
		 			    	//add file to RDF triple store if triple store is used
		 			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
					             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
						             case "yes" => {
						               sparql.addFileToGraph(f.id)
						               sparql.linkFileToDataset(f.id, dt.id)
						             }
						             case _ => {}
					             }
				             }
		 			    	
		 			    	var extractJobId=current.plugin[VersusPlugin].foreach{_.extract(id)} 
		 			    	Logger.debug("Inside File: Extraction Id : "+ extractJobId)
		 			    	
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id))
		//		            Ok(views.html.dataset(dt, Previewers.searchFileSystem))
					      }
					      
					      case None => {
					        Logger.error("Could not retrieve file that was just saved.")
					        // TODO create a service instead of calling salat directly
					        val dt = dataset.copy(author=identity)
				            datasets.update(dt) 
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id))
				            current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Dataset","added",dt.id.toString, dt.name)}
				            Redirect(routes.Datasets.dataset(dt.id))				            
		//		            Ok(views.html.dataset(dt, Previewers.searchFileSystem))
					      }
					    }   	                 
	                 }
	               case false => Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select ONE file (upload new or existing)")	
	               }
	             
	           
	        }.getOrElse{
	          val fileId = request.body.asFormUrlEncoded.get("existingFile").get(0)
	          fileId match{
	            case "__nofile" => Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select ONE file (upload new or existing)")
	            case _ => {
	              //Existing file selected	          
	          
		          // add file to dataset 
		          val theFile = files.get(UUID(fileId))
		          if(theFile.isEmpty)
		            Redirect(routes.Datasets.newDataset()).flashing("error"->"Selected file not found. Maybe it was removed.")		            
		          val theFileGet = theFile.get
		          
		          val thisFileThumbnail: Option[String] = theFileGet.thumbnail_id
		          var thisFileThumbnailString: Option[String] = None
		          if(!thisFileThumbnail.isEmpty)
		            thisFileThumbnailString = Some(thisFileThumbnail.get)
		          
		          var isDatasetPublicOption = request.body.asFormUrlEncoded.get("datasetPrivatePublic")
					        if(!isDatasetPublicOption.isDefined)
					          isDatasetPublicOption = Some(List("false"))	        
					        val isDatasetPublic = isDatasetPublicOption.get(0).toBoolean
				  val dt = dataset.copy(files = List(theFileGet), author=identity, thumbnail_id=thisFileThumbnailString, isPublic=Some(isDatasetPublic))
				  datasets.update(dt)
			      
			      val dateFormat = new SimpleDateFormat("dd/MM/yyyy")

			      accessRights.addPermissionLevel(request.user.get, dt.id.stringify, "dataset", "administrate")

		          if(!theFileGet.xmlMetadata.isEmpty){
		            val xmlToJSON = files.getXMLMetadataJSON(UUID(fileId))
		            datasets.addXMLMetadata(dt.id, UUID(fileId), xmlToJSON)
		            // index dataset
		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
			        List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())),
			            ("fileId",theFileGet.id.toString),("fileName",theFileGet.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
		          }else{
		            // index dataset
		        	  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
			    	   List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())),
			    	       ("fileId",theFileGet.id.toString),("fileName",theFileGet.filename), ("collId",""),("collName","")))}
		          }
		          
		          //reindex file
		          files.index(theFileGet.id)
		          
		          // TODO RK : need figure out if we can use https
		          val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
				  // TODO RK need to replace unknown with the server name and dataset type		            
				  val dtkey = "unknown." + "dataset."+ "unknown"
						  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id, dt.id, host, dtkey, Map.empty, "0", dt.id, ""))}

		          //link file to dataset in RDF triple store if triple store is used
		          if(theFileGet.filename.endsWith(".xml")){
				             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
					             case "yes" => {
					               sparql.linkFileToDataset(UUID(fileId), dt.id)
					             }
					             case _ => {}
				             }
				   }
		          
				  // redirect to dataset page
				  Redirect(routes.Datasets.dataset(dt.id))
				  current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Dataset","added",dt.id.stringify, dt.name)}
				  Redirect(routes.Datasets.dataset(dt.id)) 				  
	            }	            
	          }  
	        }
		  }
		 )
        }
        case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new datasets.")
      }
  }

  def metadataSearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.metadataSearch())
  }

  def generalMetadataSearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.generalMetadataSearch())
  }


  def redirectToImg(imgResource: String)  = SecuredAction(authorization=WithPermission(Permission.Public)) { implicit request =>
  	Redirect(routes.Assets.at("images/"+ imgResource))
  }

  
  
  
  def checkAccessForDataset(dataset: models.Dataset, user: Option[Identity], permissionType: String): Boolean = {
    if(permissionType.equals("view") && (dataset.isPublic.getOrElse(false) || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          appConfiguration.adminExists(theUser.email.getOrElse("")) || dataset.author.identityId.userId.equals(theUser.identityId.userId) || accessRights.checkForPermission(theUser, dataset.id.stringify, "dataset", permissionType)
        }
        case None=>{
          false
        }
      }
    }
  }
  
  def checkAccessForDatasetUsingRightsList(dataset: models.Dataset, user: Option[Identity], permissionType: String, rightsForUser: Option[UserPermissions]): Boolean = {
    if(permissionType.equals("view") && (dataset.isPublic.getOrElse(false) || appConfiguration.getDefault.get.viewNoLoggedIn)){
      true
    }
    else{
      user match{
        case Some(theUser)=>{
          val canAccessWithoutRightsList =  appConfiguration.adminExists(theUser.email.getOrElse("")) || dataset.author.identityId.userId.equals(theUser.identityId.userId)
          rightsForUser match{
	        case Some(userRights)=>{
	        	if(canAccessWithoutRightsList)
	        	  true
	        	else{
	        	  if(permissionType.equals("view")){
			        userRights.datasetsViewOnly.contains(dataset.id.stringify)
			      }else if(permissionType.equals("modify")){
			        userRights.datasetsViewModify.contains(dataset.id.stringify)
			      }else if(permissionType.equals("administrate")){
			        userRights.datasetsAdministrate.contains(dataset.id.stringify)
			      }
			      else{
			        Logger.error("Unknown permission type")
			        false
			      }
	        	}
	        }
	        case None=>{
	          canAccessWithoutRightsList
	        }
	      }
        }
        case None=>{
          false
        }
      }
    }
  }
  

}
