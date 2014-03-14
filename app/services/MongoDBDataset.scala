/**
 *
 */
package services
import models.Dataset
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.api.Logger
import java.text.SimpleDateFormat
import models.Collection
import Transformation.LidoToCidocConvertion
import play.api.Play.current
import java.util.ArrayList
import java.io.BufferedReader
import java.io.FileReader
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.FileInputStream
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import scala.collection.mutable.ListBuffer
import java.io.PrintStream

/**
 * Implementation of DatasetService using Mongodb.
 * 
 * @author Luigi Marini
 *
 */

object MustBreak extends Exception { }

trait MongoDBDataset {
  
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  
  /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset] = {   
    (for (dataset <- Dataset.find(MongoDBObject())) yield dataset).toList
  }
  
  /**
   * List all datasets in the system in reverse chronological order.
   */
  def listDatasetsChronoReverse(): List[Dataset] = {
     val order = MongoDBObject("created"-> -1)
     Dataset.findAll.sort(order).toList
  }
  
  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset] = {
    val order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
      Logger.info("After " + sinceDate)
      Dataset.find("created" $lt sinceDate).sort(order).limit(limit).toList
    }
  }
  
  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset] = {
    var order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("created"-> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(date)
      Logger.info("Before " + sinceDate)
      var datasetList = Dataset.find("created" $gt sinceDate).sort(order).limit(limit + 1).toList.reverse
      datasetList = datasetList.filter(_ != datasetList.last)
      datasetList
    }
  }
  
  /**
   * List all datasets inside a collection.
   */
  def listInsideCollection(collectionId: String) : List[Dataset] =  { 
      Collection.findOneById(new ObjectId(collectionId)) match{
        case Some(collection) => {
          val list = for (dataset <- listDatasetsChronoReverse; if(isInCollection(dataset,collection))) yield dataset
          return list
        }
        case None =>{
          return List.empty	 	  
        } 
      }
  } 
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }
  
  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset] = {
    Dataset.findOneById(new ObjectId(id))
  }
  
  /**
   * 
   */
  def getFileId(datasetId: String, filename: String): Option[String] = {
    get(datasetId) match {
      case Some(dataset) => {	  
        for (file <- dataset.files) {
          if (file.filename.equals(filename)) {
            return Some(file.id.toString)
          }
        }
        Logger.error("File does not exist in dataset" + datasetId); return None
      }
      case None => { Logger.error("Error getting dataset" + datasetId); return None }
    }
  }
  
  def modifyRDFOfMetadataChangedDatasets(){    
    val changedDatasets = Dataset.findMetadataChangedDatasets()
    for(changedDataset <- changedDatasets){
      modifyRDFUserMetadata(changedDataset.id.toString)
    }
  }
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1") = {
    services.Services.rdfSPARQLService.removeDatasetFromUserGraphs(id)
    get(id) match { 
	            case Some(dataset) => {
	              val theJSON = Dataset.getUserMetadataJSON(id)
	              val fileSep = System.getProperty("file.separator")
	              val tmpDir = System.getProperty("java.io.tmpdir")
		          var resultDir = tmpDir + fileSep + "medici__rdfuploadtemporaryfiles" + fileSep + new ObjectId().toString
		          val resultDirFile = new java.io.File(resultDir)
		          resultDirFile.mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(play.api.Play.configuration.getString("datasetsxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")
	              
	              //Connecting RDF metadata with the entity describing the original file
					val rootNodes = new ArrayList[String]()
					val rootNodesFile = play.api.Play.configuration.getString("datasetRootNodesFile").getOrElse("")
					Logger.debug(rootNodesFile)
					if(!rootNodesFile.equals("*")){
						val rootNodesReader = new BufferedReader(new FileReader(new java.io.File(rootNodesFile)))						
						var line = rootNodesReader.readLine()  
						while (line != null){
						    Logger.debug((line == null).toString() ) 
							rootNodes.add(line.trim())
							line = rootNodesReader.readLine() 
						}
						rootNodesReader.close()
					}
					
					val resultFileConnected = java.io.File.createTempFile("ResultsConnected", ".rdf")
					
					val fileWriter =  new BufferedWriter(new FileWriter(resultFileConnected))		
					val fis = new FileInputStream(resultFile)
					val data = new Array[Byte]  (resultFile.length().asInstanceOf[Int])
				    fis.read(data)
				    fis.close()
				    resultFile.delete()
				    FileUtils.deleteDirectory(resultDirFile)
				    //
				    val s = new String(data, "UTF-8")
					val rdfDescriptions = s.split("<rdf:Description")
					fileWriter.write(rdfDescriptions(0))
					var i = 0
					for( i <- 1 to (rdfDescriptions.length - 1)){
						fileWriter.write("<rdf:Description" + rdfDescriptions(i))
						if(rdfDescriptions(i).contains("<rdf:type")){
							var isInRootNodes = false
							if(rootNodesFile.equals("*"))
								isInRootNodes = true
							else{
								var j = 0
								try{
									for(j <- 0 to (rootNodes.size()-1)){
										if(rdfDescriptions(i).contains("\"" + rootNodes.get(j) + "\"")){
											isInRootNodes = true
											throw MustBreak
										}
									}
								}catch {case MustBreak => }
							}
							
							if(isInRootNodes){
								val theResource = rdfDescriptions(i).substring(rdfDescriptions(i).indexOf("\"")+1, rdfDescriptions(i).indexOf("\"", rdfDescriptions(i).indexOf("\"")+1))
								val theHost = "http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
								var connection = "<rdf:Description rdf:about=\"" + theHost +"/api/datasets/"+ id
								connection = connection	+ "\"><P129_is_about xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + theResource
								connection = connection	+ "\"/></rdf:Description>"
								fileWriter.write(connection)
							}	
						}
					}
					fileWriter.close()
	              
					services.Services.rdfSPARQLService.addFromFile(id, resultFileConnected, "dataset")
					resultFileConnected.delete()
					
					services.Services.rdfSPARQLService.addDatasetToGraph(id, "rdfCommunityGraphName")
					
					Dataset.setUserMetadataWasModified(id, false)
	            }
	            case None => {}
	 }
  }
  
  def jsonToXML(theJSON: String): java.io.File = {
    
    val jsonObject = new JSONObject(theJSON)    
    var xml = org.json.XML.toString(jsonObject)
    
    Logger.debug("thexml: " + xml)
    
    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while(currStart != -1){
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1,currStart)
      currEnd = xml.indexOf(">", currStart+1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart,currEnd+1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd+1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1)
    
    val xmlFile = java.io.File.createTempFile("xml",".xml")
    val fileWriter =  new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile    
  }
  
  def dumpAllDatasetGroupings(): List[String] = {
    
    Logger.debug("Dumping dataset groupings of all datasets.")
    
    val fileSep = System.getProperty("file.separator")
    val lineSep = System.getProperty("line.separator")
    var datasetsDumpDir = play.api.Play.configuration.getString("datasetdump.dir").getOrElse("")
	if(!datasetsDumpDir.endsWith(fileSep))
		datasetsDumpDir = datasetsDumpDir + fileSep
	var dsDumpMoveDir = play.api.Play.configuration.getString("datasetdumpmove.dir").getOrElse("")	
	if(dsDumpMoveDir.equals("")){
		Logger.warn("Will not move dumped dataset groupings to staging directory. No staging directory set.")	  
	}
	else{
	    if(!dsDumpMoveDir.endsWith(fileSep))
		  dsDumpMoveDir = dsDumpMoveDir + fileSep
	}
		
	var unsuccessfulDumps: ListBuffer[String] = ListBuffer.empty
	
	for(dataset <- Dataset.findAll){
	  try{
		  val dsId = dataset.id.toString
		  val datasetnameNoSpaces = dataset.name.replaceAll("\\s+","_")
		  val filePathInDirs = dsId.charAt(dsId.length()-3)+ fileSep + dsId.charAt(dsId.length()-2)+dsId.charAt(dsId.length()-1)+ fileSep + dsId + fileSep + datasetnameNoSpaces + ".txt"
		  
		  val groupingFile = new java.io.File(datasetsDumpDir + filePathInDirs)
		  groupingFile.getParentFile().mkdirs()
		  
		  val filePrintStream =  new PrintStream(groupingFile)
		  for(file <- dataset.files){
		    filePrintStream.println("id:"+file.id.toString+" "+"filename:"+file.filename)
		  }
		  filePrintStream.close()
		  
		  if(!dsDumpMoveDir.equals("")){
			  try{
				  val groupingMoveFile = new java.io.File(dsDumpMoveDir + filePathInDirs)
			      groupingMoveFile.getParentFile().mkdirs()
			      
				  if(groupingFile.renameTo(groupingMoveFile)){
	            	Logger.info("Dataset file grouping dumped and moved to staging directory successfully.")
				  }else{
	            	Logger.warn("Could not move dumped dataset file grouping to staging directory.")
	            	throw new Exception("Could not move dumped dataset file grouping to staging directory.")
				  }
			  }catch {case ex:Exception =>{
				  val badDatasetId = dataset.id.toString
				  Logger.error("Unable to stage file grouping of dataset with id "+badDatasetId+": "+ex.printStackTrace())
				  unsuccessfulDumps += badDatasetId
			  }}
			}
	  }catch {case ex:Exception =>{
	    val badDatasetId = dataset.id.toString
	    Logger.error("Unable to dump file grouping of dataset with id "+badDatasetId+": "+ex.printStackTrace())
	    unsuccessfulDumps += badDatasetId
	  }}
	}
	
    return unsuccessfulDumps.toList
  }
  
  
  def dumpAllDatasetMetadata(): List[String] = {    
    Logger.debug("Dumping metadata of all datasets.")
    
    val fileSep = System.getProperty("file.separator")
    val lineSep = System.getProperty("line.separator")
    var dsMdDumpDir = play.api.Play.configuration.getString("datasetdump.dir").getOrElse("")
	if(!dsMdDumpDir.endsWith(fileSep))
		dsMdDumpDir = dsMdDumpDir + fileSep
	var dsMdDumpMoveDir = play.api.Play.configuration.getString("datasetdumpmove.dir").getOrElse("")	
	if(dsMdDumpMoveDir.equals("")){
		Logger.warn("Will not move dumped datasets metadata to staging directory. No staging directory set.")	  
	}
	else{
	    if(!dsMdDumpMoveDir.endsWith(fileSep))
		  dsMdDumpMoveDir = dsMdDumpMoveDir + fileSep
	}	
    
	var unsuccessfulDumps: ListBuffer[String] = ListBuffer.empty 	
		
	for(dataset <- Dataset.findAll){
	  try{
		  val dsId = dataset.id.toString
		  
		  val dsTechnicalMetadata = Dataset.getTechnicalMetadataJSON(dsId)
		  val dsUserMetadata = Dataset.getUserMetadataJSON(dsId)
		  val dsXMLMetadata = Dataset.getXMLMetadataJSON(dsId)
		  if(dsTechnicalMetadata != "{}" || dsUserMetadata != "{}" || dsXMLMetadata != "{}"){
		    
		    val datasetnameNoSpaces = dataset.name.replaceAll("\\s+","_")
		    val filePathInDirs = dsId.charAt(dsId.length()-3)+ fileSep + dsId.charAt(dsId.length()-2)+dsId.charAt(dsId.length()-1)+ fileSep + dsId + fileSep + datasetnameNoSpaces + "__metadata.txt"
		    val mdFile = new java.io.File(dsMdDumpDir + filePathInDirs)
		    mdFile.getParentFile().mkdirs()
		    
		    val fileWriter =  new BufferedWriter(new FileWriter(mdFile))
			fileWriter.write(dsTechnicalMetadata + lineSep + lineSep + dsUserMetadata + lineSep + lineSep + dsXMLMetadata)
			fileWriter.close()
			
			if(!dsMdDumpMoveDir.equals("")){
			  try{
				  val mdMoveFile = new java.io.File(dsMdDumpMoveDir + filePathInDirs)
			      mdMoveFile.getParentFile().mkdirs()
			      
				  if(mdFile.renameTo(mdMoveFile)){
	            	Logger.info("Dataset metadata dumped and moved to staging directory successfully.")
				  }else{
	            	Logger.warn("Could not move dumped dataset metadata to staging directory.")
	            	throw new Exception("Could not move dumped dataset metadata to staging directory.")
				  }
			  }catch {case ex:Exception =>{
				  val badDatasetId = dataset.id.toString
				  Logger.error("Unable to stage dumped metadata of dataset with id "+badDatasetId+": "+ex.printStackTrace())
				  unsuccessfulDumps += badDatasetId
			  }}
			}
		    
		  }
	  }catch {case ex:Exception =>{
	    val badDatasetId = dataset.id.toString
	    Logger.error("Unable to dump metadata of dataset with id "+badDatasetId+": "+ex.printStackTrace())
	    unsuccessfulDumps += badDatasetId
	  }}
	}
    
    return unsuccessfulDumps.toList
  }
  
}