package services.mongodb

import play.api.mvc.Request
import services._
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat

import _root_.util.{License, Parsers, SearchUtils}

import scala.collection.mutable.ListBuffer
import Transformation.LidoToCidocConvertion
import java.util.{ArrayList, Calendar}
import java.io._

import org.apache.commons.io.FileUtils
import org.json.JSONObject
import play.api.libs.json.{JsValue, Json}
import com.mongodb.util.JSON
import java.nio.file.{FileSystems, Files}
import java.nio.file.attribute.BasicFileAttributes

import collection.JavaConverters._
import scala.collection.JavaConversions._
import javax.inject.{Inject, Singleton}

import com.mongodb.casbah.WriteConcern
import play.api.Logger

import scala.util.parsing.json.JSONArray
import play.api.libs.json.JsArray
import models.File
import play.api.libs.json.JsObject
import java.util.Date

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play._
import com.mongodb.casbah.Imports._
import models.FileStatus.FileStatus


/**
 * Use mongo for both metadata and blobs.
 *
 *
 */
@Singleton
class MongoDBFileService @Inject() (
  datasets: DatasetService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  thumbnails: ThumbnailService,
  threeD: ThreeDService,
  sparql: RdfSPARQLService,
  storage: ByteStorageService,
  userService: UserService,
  folders: FolderService,
  metadatas: MetadataService,
  events: EventService) extends FileService {

  object MustBreak extends Exception {}

  /**
   * Count all files
   */
  def count(): Long = {
    FileDAO.count(MongoDBObject())
  }

  def statusCount(): Map[FileStatus, Long] = {
    val results = FileDAO.dao.collection.aggregate(MongoDBObject("$group" ->
      MongoDBObject("_id" -> "$status", "count" -> MongoDBObject("$sum" -> 1L))))
    results.results.map(x => FileStatus.withName(x.getAsOrElse[String]("_id", FileStatus.UNKNOWN.toString)) -> x.getAsOrElse[Long]("count", 0L)).toMap
  }

  def bytes(): Long = {
    val results = FileDAO.dao.collection.aggregate(MongoDBObject("$group" ->
      MongoDBObject("_id" -> "size", "total" -> MongoDBObject("$sum" -> "$length"))))
    results.results.find(x => x.containsField("total")) match {
      case Some(x) => x.getAsOrElse[Long]("total", 0L)
      case None => 0L
    }
  }

  def save(file: File): Unit = {
    FileDAO.save(file, WriteConcern.Safe)
  }

  /**
   * List all files.
   */
  def listFiles(): List[File] = {
    (for (file <- FileDAO.find(MongoDBObject())) yield file).toList
  }
  
  /**
   * List all files in the system that are not intermediate result files generated by the extractors.
   */
  def listFilesNotIntermediate(): List[File] = {
    (for (file <- FileDAO.find("isIntermediate" $ne true)) yield file).toList
  }

  /**
   * List files after a specified date.
   */
  def listFilesAfter(date: String, limit: Int): List[File] = {
    val order = MongoDBObject("uploadDate" -> -1)
    if (date == "") {
      FileDAO.find("isIntermediate" $ne true).sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
      Logger.debug("After " + sinceDate)
      FileDAO.find($and("isIntermediate" $ne true, "uploadDate" $lt sinceDate)).sort(order).limit(limit).toList
    }
  }

  /**
   * List files before a specified date.
   */
  def listFilesBefore(date: String, limit: Int): List[File] = {
    var order = MongoDBObject("uploadDate" -> -1)
    if (date == "") {
      FileDAO.find("isIntermediate" $ne true).sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("uploadDate" -> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
      Logger.debug("Before " + sinceDate)
      FileDAO.find($and("isIntermediate" $ne true, "uploadDate" $gt sinceDate)).sort(order).limit(limit).toList.reverse
    }
  }
  
  /**
   * List files specific to a user after a specified date.
   */
  def listUserFilesAfter(date: String, limit: Int, email: String): List[File] = {
    val order = MongoDBObject("uploadDate"-> -1 )
    if (date == "") {
      FileDAO.find(("isIntermediate" $ne true) ++ ("author.email" $eq email)).sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      FileDAO.find(("isIntermediate" $ne true) ++ ("uploadDate" $lt sinceDate) ++ ("author.email" -> email))
        .sort(order).limit(limit).toList
    }
  }

  /**
   * List files specific to a user before a specified date.
   */
  def listUserFilesBefore(date: String, limit: Int, email: String): List[File] = {
    var order = MongoDBObject("uploadDate"-> -1)
    if (date == "") {
      FileDAO.find(("isIntermediate" $ne true) ++ ("author.email" $eq email)).sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("uploadDate"-> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      FileDAO.find(("isIntermediate" $ne true) ++ ("uploadDate" $gt sinceDate) ++ ("author.email" $eq email))
        .sort(order).limit(limit).toList.reverse
    }
  }

  def latest(): Option[File] = {
    val results = FileDAO.find("isIntermediate" $ne true).sort(MongoDBObject("uploadDate" -> -1)).limit(1).toList
    if (results.size > 0)
      Some(results(0))
    else
      None
  }

  def latest(i: Int): List[File] = {
    FileDAO.find(MongoDBObject()).sort(MongoDBObject("uploadDate" -> -1)).limit(i).toList
  }

  def first(): Option[File] = {
    val results = FileDAO.find("isIntermediate" $ne true).sort(MongoDBObject("uploadDate" -> 1)).limit(1).toList
    if (results.size > 0)
      Some(results(0))
    else
      None
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: MiniUser, showPreviews: String = "DatasetLevel"): Option[File] = {
    ByteStorageService.save(inputStream, FileDAO.COLLECTION) match {
      case Some(x) => {
        val file = File(UUID.generate(), x._1, filename, author, new Date(), util.FileUtils.getContentType(filename, contentType), x._3, x._2, showPreviews = showPreviews, licenseData = License.fromAppConfig())
        FileDAO.save(file)
        Some(file)
      }
      case None => None
    }
  }

  /**
   * Get blob.
   */
  def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    get(id).flatMap { x =>
      ByteStorageService.load(x.loader, x.loader_id, FileDAO.COLLECTION).map((_, x.filename, x.contentType, x.length))
    }
  }

  def index(id: Option[UUID]) = {
    id match {
      case Some(fileId) => index(fileId)
      case None => FileDAO.find(MongoDBObject()).foreach(f => index(f.id))
    }
  }

  def index(id: UUID) {
    get(id) match {
      case Some(file) => {
        current.plugin[ElasticsearchPlugin].foreach {
          _.index(file)
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }

  /**
    * Directly insert a file into the db (even with a local path)
    */
  def insert(file: File): Option[String] = {
    FileDAO.insert(file).map(_.toString)
  }

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(user: Option[User]): Map[String, Long] = {
    if(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public"){
      val x = FileDAO.dao.collection.aggregate(MongoDBObject("$unwind" -> "$tags"),
        MongoDBObject("$group" -> MongoDBObject("_id" -> "$tags.name", "count" -> MongoDBObject("$sum" -> 1L))))
      x.results.map(x => (x.getAsOrElse[String]("_id", "??"), x.getAsOrElse[Long]("count", 0L))).toMap
    } else {
      val x = FileDAO.dao.collection.aggregate(MongoDBObject("$match"-> buildTagFilter(user)), MongoDBObject("$unwind" -> "$tags"),
        MongoDBObject("$group" -> MongoDBObject("_id" -> "$tags.name", "count" -> MongoDBObject("$sum" -> 1L))))
      x.results.map(x => (x.getAsOrElse[String]("_id", "??"), x.getAsOrElse[Long]("count", 0L))).toMap
    }
  }


  private def buildTagFilter(user: Option[User]): MongoDBObject = {
    val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]

    user match {
      case Some(u) => {
        orlist += MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
        //Get all datasets you have access to.
        val datasetsList= datasets.listUser(u)
        val foldersList = folders.findByParentDatasetIds(datasetsList.map(x=> x.id))
        val fileIds = datasetsList.map(x=> x.files) ++ foldersList.map(x=> x.files)
        orlist += ("_id" $in fileIds.flatten.map(x=> new ObjectId(x.stringify)))
      }
      case None => Map.empty
    }

    $or(orlist.map(_.asDBObject))
  }


  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in file " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map {
      tag =>
        FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def get(id: UUID): Option[File] = {
    FileDAO.findOneById(new ObjectId(id.stringify)) match {
      case Some(file) => {
        val previewsByFile = previews.findByFileId(file.id)
        val sectionsByFile = sections.findByFileId(file.id)
        val sectionsWithPreviews = sectionsByFile.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        Some(file.copy(sections = sectionsWithPreviews, previews = previewsByFile))
      }
      case None => None
    }
  }

  override def setStatus(id: UUID, status: FileStatus): Unit = {
    FileDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.toString())), $set("status" -> status.toString))
  }


  def listOutsideDataset(dataset_id: UUID): List[File] = {
    datasets.get(dataset_id) match{
      case Some(dataset) => {
        val list = for (file <- FileDAO.findAll(); if(!isInDataset(file,dataset) && !file.isIntermediate)) yield file
        return list.toList
      }
      case None =>{
        return FileDAO.findAll.toList
      }
    }
  }

  def isInDataset(file: File, dataset: Dataset): Boolean = {
    for(dsFile <- dataset.files){
      if(dsFile == file.id)
        return true
    }
    return false
  }


  /** Change the metadataCount field for a file */
  def incrementMetadataCount(id: UUID, count: Long) = {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $inc("metadataCount" -> count), false, false, WriteConcern.Safe)
  }
  
  /**
   *  Add versus descriptors to the Versus.descriptors collection associated to a file
   *
   */
 def addVersusMetadata(id:UUID,json:JsValue){
    val doc = JSON.parse(Json.stringify(json)).asInstanceOf[DBObject].toMap
              .asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
       VersusDAO.insert(new Versus(id,doc),WriteConcern.Safe)
       Logger.debug("--Added versus descriptors in json format received from versus to the metadata field --")
  }
 
/**
 * Get Versus descriptors as Json Array for a file
 */
  def getVersusMetadata(id: UUID): Option[JsValue] = {
    val versusDescriptors = VersusDAO.find(MongoDBObject("fileId" -> new ObjectId(id.stringify)))
    var vdArray = new JsArray()
    for (vd <- versusDescriptors) {
      var x = com.mongodb.util.JSON.serialize(vd.asInstanceOf[Versus].descriptors("versus_descriptors"))
      vdArray = vdArray :+ Json.parse(x)
      Logger.debug("array=" + vdArray.toString)
    }
    Some(vdArray)
  } 
   /*convert list of JsObject to JsArray*/
  def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }

  def findByTag(tag: String, user: Option[User]): List[File] = {
    FileDAO.find(buildTagFilter(user) ++ MongoDBObject("tags.name" -> tag)).toList
  }

  def findByTag(tag: String, start: String, limit: Integer, reverse: Boolean, user: Option[User]): List[File] = {

    var filter = if (start == "") {
      MongoDBObject("tags.name" -> tag)
    } else {
      if (reverse) {
        MongoDBObject("tags.name" -> tag) ++ ("uploadDate" $gte Parsers.fromISO8601(start))
      } else {
        MongoDBObject("tags.name" -> tag) ++ ("uploadDate" $lte Parsers.fromISO8601(start))
      }
    }
    if(!(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public")) {
      filter = buildTagFilter(user) ++ filter
    }
    val order = if (reverse) {
      MongoDBObject("uploadDate" -> 1, "filename" -> 1)
    } else {
      MongoDBObject("uploadDate" -> -1, "filename" -> 1)
    }
    FileDAO.dao.find(filter).sort(order).limit(limit).toList
  }

  def findIntermediates(): List[File] = {
    FileDAO.find(MongoDBObject("isIntermediate" -> true)).toList
  }
  
  /**
   * Implementation of updateLicenseing defined in services/FileService.scala.
   */
  def updateLicense(id: UUID, licenseType: String, rightsHolder: String, licenseText: String, licenseUrl: String,
    allowDownload: String) {
      val licenseData = models.LicenseData(m_licenseType = licenseType, m_rightsHolder = rightsHolder,
        m_licenseText = licenseText, m_licenseUrl = licenseUrl, m_allowDownload = allowDownload.toBoolean)
      val result = FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), 
          $set("licenseData" -> LicenseData.toDBObject(licenseData)), 
          false, false, WriteConcern.Safe);      
  }

  // ---------- Tags related code starts ------------------
  // Input validation is done in api.Files, so no need to check again.
  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Adding tags to file " + id + " : " + tags)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    val maxTagLength = play.api.Play.configuration.getInt("clowder.tagLength").getOrElse(100)
    tags.foreach(tag => {
      val shortTag = if (tag.length > maxTagLength) {
        Logger.error("Tag is truncated to " + maxTagLength + " chars : " + tag)
        tag.substring(0, maxTagLength)
      } else {
        tag
      }
      // Only add tags with new values.
      if (!existingTags.contains(shortTag)) {
        val tagObj = models.Tag(name = shortTag, userId = userIdStr, extractor_id = eid, created = createdDate)
        FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
  }

  def removeAllTags(id: UUID) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("tags" -> List()), false, false,
      WriteConcern.Safe)
  }
  // ---------- Tags related code ends ------------------

  def comment(id: UUID, comment: Comment) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("comments" -> Comment.toDBObject(comment)),
      false, false, WriteConcern.Safe)
  }
  
  def setIntermediate(id: UUID){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("isIntermediate" -> Some(true)), false, false,
      WriteConcern.Safe)
  }

  def renameFile(id: UUID, newName: String){
    events.updateObjectName(id, newName)
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("filename" -> newName), false, false,
      WriteConcern.Safe)
  }

  def setContentType(id: UUID, newType: String){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("contentType" -> newType), false, false,
      WriteConcern.Safe)
  }

  def setUserMetadataWasModified(id: UUID, wasModified: Boolean){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("userMetadataWasModified" -> Some(wasModified)),
      false, false, WriteConcern.Safe)
  }

  def removeFile(id: UUID){
    get(id) match{
      case Some(file) => {
        if(!file.isIntermediate){
          val fileDatasets = datasets.findByFileId(file.id)
          for(fileDataset <- fileDatasets){
            datasets.removeFile(fileDataset.id, id)

            if(!file.thumbnail_id.isEmpty && !fileDataset.thumbnail_id.isEmpty){            
              if(file.thumbnail_id.get.equals(fileDataset.thumbnail_id.get)){ 
                datasets.newThumbnail(fileDataset.id)
                	for(collectionId <- fileDataset.collections){
                    collections.get(collectionId) match{
                      case Some(collection) =>{
                        if(!collection.thumbnail_id.isEmpty){
                          if(collection.thumbnail_id.get.equals(fileDataset.thumbnail_id.get)){
                            collections.createThumbnail(collection.id)
                          }
                        }
                      }
                      case None=>Logger.debug(s"Could not find collection $collectionId")
                    }
                  }
		          }
            }
                     
          }
          val fileFolders = folders.findByFileId(file.id)
          for(fileFolder <- fileFolders) {
            folders.removeFile(fileFolder.id, file.id)
          }
          for(section <- sections.findByFileId(file.id)){
            sections.removeSection(section)
          }
          for(preview <- previews.findByFileId(file.id)){
            previews.removePreview(preview)
          }
          for(comment <- comments.findCommentsByFileId(id)){
            comments.removeComment(comment)
          }
          for(texture <- threeD.findTexturesByFileId(file.id)){
            ThreeDTextureDAO.removeById(new ObjectId(texture.id.stringify))
          }
          for (follower <- file.followers) {
            userService.unfollowFile(follower, id)
          }
          if(!file.thumbnail_id.isEmpty)
            thumbnails.remove(UUID(file.thumbnail_id.get))
          metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.file, id))
        }

        // finally delete the actual file
        if(isLastPointingToLoader(file.loader, file.loader_id)) {
          ByteStorageService.delete(file.loader, file.loader_id, FileDAO.COLLECTION)
        }

        FileDAO.remove(file)
      }
      case None => Logger.debug("File not found")
    }
  }

  def isLastPointingToLoader(loader: String, loader_id: String): Boolean = {
    val result = FileDAO.find(MongoDBObject("loader" -> loader, "loader_id" -> loader_id))
    result.size == 1
  }
  def removeTemporaries(){
    val cal = Calendar.getInstance()
    val timeDiff = play.Play.application().configuration().getInt("rdfTempCleanup.removeAfter")
    cal.add(Calendar.MINUTE, -timeDiff)
    val oldDate = cal.getTime()

    val tmpDir = System.getProperty("java.io.tmpdir")
    val filesep = System.getProperty("file.separator")
    val rdfTmpDir = new java.io.File(tmpDir + filesep + "clowder__rdfdumptemporaryfiles")
    if(!rdfTmpDir.exists()){
      rdfTmpDir.mkdir()
    }

    val listOfFiles = rdfTmpDir.listFiles()
    for(currFileDir <- listOfFiles){
      val currFile = currFileDir.listFiles()(0)
      val attrs = Files.readAttributes(FileSystems.getDefault().getPath(currFile.getAbsolutePath()),  classOf[BasicFileAttributes])
      val timeCreated = new Date(attrs.creationTime().toMillis())
      if(timeCreated.compareTo(oldDate) < 0){
        currFile.delete()
        currFileDir.delete()
      }
    }
  }

  def findMetadataChangedFiles(): List[File] = {
    FileDAO.find(MongoDBObject("userMetadataWasModified" -> true)).toList
  }

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[File] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "all")
    Logger.debug("thequery: "+theQuery.toString)
    FileDAO.find(theQuery).toList
  }


  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[File] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "userMetadata")
    Logger.debug("thequery: "+theQuery.toString)
    FileDAO.find(theQuery).toList
  }

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String,Any], root: String): MongoDBObject = {
    Logger.debug("req: "+ requestedMap)
    var queryMap = MongoDBList()
    var builder = MongoDBList()
    var orFound = false
    for((reqKey, reqValue) <- requestedMap){
      val keyTrimmed = reqKey.replaceAll("__[0-9]+$","")

      if(keyTrimmed.equals("OR")){
        queryMap.add(MongoDBObject("$and" ->  builder))
        builder = MongoDBList()
        orFound = true
      }
      else{
        var actualKey = keyTrimmed
        if(keyTrimmed.endsWith("__not")){
          actualKey = actualKey.substring(0, actualKey.length()-5)
        }

        if(!root.equals("all")){

          if(!root.equals(""))
            actualKey = root + "." + actualKey

          if(reqValue.isInstanceOf[String]){
            val currValue = reqValue.asInstanceOf[String]
                        
            if(keyTrimmed.endsWith("__not")){
              if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");                
                if(!currValue.contains(" ANYWHERE")){
                  realValue = "^"+realValue+"$";
                }
                if(currValue.contains(" IGNORE CASE")){
                  realValue = "(?i)"+realValue;
                }
                builder += MongoDBObject(actualKey -> MongoDBObject("$not" ->  realValue.r))
              }
              else{
                builder += MongoDBObject(actualKey -> MongoDBObject("$ne" ->  currValue))
              }
            }
            else{
              if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");                
                if(!currValue.contains(" ANYWHERE")){
                  realValue = "^"+realValue+"$";
                }
                if(currValue.contains(" IGNORE CASE")){
                  realValue = "(?i)"+realValue;
                }
                builder += MongoDBObject(actualKey -> realValue.r)
              }
              else{
                builder += MongoDBObject(actualKey -> currValue)
              }
            }
          }else{
            //recursive
            if(root.equals("userMetadata")){
              val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], "")
              val elemMatch = actualKey $elemMatch currValue
              builder.add(elemMatch)
            }
            else{
              val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], actualKey)
              builder += currValue
            }
          }
        } else {
          var objectForEach = MongoDBList()
          val allRoots = Map(1 -> "userMetadata", 2 -> "metadata", 3 -> "xmlMetadata")
          allRoots.keys.foreach{ i =>
            var tempActualKey = allRoots(i) + "." + actualKey

            if(reqValue.isInstanceOf[String]){
              val currValue = reqValue.asInstanceOf[String]
              if(keyTrimmed.endsWith("__not")){
                if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
	                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");                
	                if(!currValue.contains(" ANYWHERE")){
	                  realValue = "^"+realValue+"$";
	                }
	                if(currValue.contains(" IGNORE CASE")){
	                  realValue = "(?i)"+realValue;
	                }
	                objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$not" ->  realValue.r))
                }
                else{
                	objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$ne" ->  currValue))
                }
              }
              else{
                if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
	                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");                
	                if(!currValue.contains(" ANYWHERE")){
	                  realValue = "^"+realValue+"$";
	                }
	                if(currValue.contains(" IGNORE CASE")){
	                  realValue = "(?i)"+realValue;
	                }
	                objectForEach += MongoDBObject(tempActualKey -> realValue.r)
                }
                else{
                	objectForEach += MongoDBObject(tempActualKey -> currValue)
                }
              }
            }else{
              //recursive
              if(allRoots(i).equals("userMetadata")){
                val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], "")
                val elemMatch = tempActualKey $elemMatch currValue
                objectForEach.add(elemMatch)
              }
              else{
                val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], tempActualKey)
                objectForEach += currValue
              }
            }
          }

          builder.add(MongoDBObject("$or" ->  objectForEach))

        }
      }
    }

    if(orFound){
      queryMap.add(MongoDBObject("$and" ->  builder))
      return MongoDBObject("$or" ->  queryMap)
    }
    else if(!builder.isEmpty)  {
      return MongoDBObject("$and" ->  builder)
    }
    else if(!root.equals("")){
      return (root $exists true)
    }
    else{
      return new MongoDBObject()
    }
  }

  def removeOldIntermediates(){
    val cal = Calendar.getInstance()
    val timeDiff = play.Play.application().configuration().getInt("intermediateCleanup.removeAfter")
    cal.add(Calendar.HOUR, -timeDiff)
    val oldDate = cal.getTime()
    val fileList = FileDAO.find($and("isIntermediate" $eq true, "uploadDate" $lt oldDate)).toList
    for(file <- fileList)
      removeFile(file.id)
  }

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(fileId: UUID, thumbnailId: UUID) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(fileId.stringify)),
      $set("thumbnail_id" -> thumbnailId.stringify), false, false, WriteConcern.Safe)
  }

  def addFollower(id: UUID, userId: UUID) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
                    $addToSet("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeFollower(id: UUID, userId: UUID) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
                    $pull("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  def updateDescription(id: UUID, description: String) {
    val result = FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("description" -> description),
      false, false, WriteConcern.Safe)

  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    FileDAO.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
  }
}

object FileDAO extends ModelCompanion[File, ObjectId] {
  val COLLECTION = "uploads"

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[File, ObjectId](collection = x.collection(COLLECTION)) {}
  }
}

object VersusDAO extends ModelCompanion[Versus,ObjectId]{
    val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Versus, ObjectId](collection = x.collection("versus.descriptors")) {}
  }
}

