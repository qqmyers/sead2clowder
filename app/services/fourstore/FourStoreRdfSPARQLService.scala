package services.fourstore

import java.io.FileInputStream
import play.Logger
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpDelete
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import play.api.Play.current
import java.util.ArrayList
import org.apache.http.NameValuePair
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import services.{RdfSPARQLService, DI, DatasetService}
import javax.inject.{Inject, Singleton}
import models.UUID

/**
 * @author Constantinos Sophocleous
 */
@Singleton
class FourStoreRdfSPARQLService @Inject() (datasets: DatasetService) extends RdfSPARQLService {
  
  var appPort = play.api.Play.configuration.getString("https.port").getOrElse("")
  val httpProtocol = {
					if(!appPort.equals("")){
						"https://"
					}
					else{
						appPort = play.api.Play.configuration.getString("http.port").getOrElse("9000")
						"http://"
					}
		}

  def addFileToGraph(fileId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null = {
    	
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
		
		
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        var updateQuery = "<" + httpProtocol + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + appPort +"/api/files/" + fileId.stringify
        updateQuery = updateQuery + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <" + graphName + "_file" + "> ."        
        
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName + "_file_" + fileId.stringify))
	    urlParameters.add(new BasicNameValuePair("mime-type", "application/x-turtle"))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)
    
        return null	
  }
  
  def addDatasetToGraph(datasetId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        var updateQuery = "<" + httpProtocol + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + appPort +"/api/datasets/" + datasetId.stringify
        updateQuery = updateQuery + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <" + graphName + "_dataset" + "> ."
        
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName + "_dataset_" + datasetId.stringify))
	    urlParameters.add(new BasicNameValuePair("mime-type", "application/x-turtle"))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

		return null
  }
  
  def linkFileToDataset(fileId: UUID, datasetId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + appPort
        var updateQuery = "<" + httpProtocol + hostIp +"/api/datasets/" + datasetId.stringify
        updateQuery = updateQuery + "> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#P148_has_component> <" + httpProtocol + hostIp +"/api/files/" + fileId.stringify + "> ."
        
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName + "_file_" + fileId.stringify))
	    urlParameters.add(new BasicNameValuePair("mime-type", "application/x-turtle"))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

		return null
  }
  
  def removeFileFromGraphs(fileId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
	    val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("")  + "/data/" + graphName + "_file_" + fileId.stringify        
        val httpclient = new DefaultHttpClient()
   	    
        val httpDelete = new HttpDelete(queryUrl)
        
        val queryResponse = httpclient.execute(httpDelete)
        Logger.info(queryResponse.getStatusLine().toString())
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)        
        Logger.debug("the results: "+resultsString)

		return null
    
  }
  
  def removeDatasetFromUserGraphs(datasetId: UUID): Null = {
    
	    val graphName = play.api.Play.configuration.getString("rdfCommunityGraphName").getOrElse("")
        val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("")  + "/data/" + graphName + "_dataset_" + datasetId.stringify        
        val httpclient = new DefaultHttpClient()
   
        val httpDelete = new HttpDelete(queryUrl)
        
        val queryResponse = httpclient.execute(httpDelete)
        Logger.info(queryResponse.getStatusLine().toString())
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)        
        Logger.debug("the results: "+resultsString)

		return null
    
  }
  
  def detachFileFromDataset(fileId: UUID, datasetId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/update/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + appPort
        var updateQuery = "DELETE { <" + httpProtocol + hostIp +"/api/datasets/" + datasetId.stringify
        updateQuery = updateQuery + "> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#P148_has_component> <" + httpProtocol+ hostIp +"/api/files/" + fileId.stringify + "> }"
        updateQuery = updateQuery + "WHERE { <" + httpProtocol + hostIp +"/api/datasets/" + datasetId.stringify
        updateQuery = updateQuery + "> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#P148_has_component> <" + httpProtocol+ hostIp +"/api/files/" + fileId.stringify + "> }"
        if(!graphName.equals("")){
          updateQuery = "WITH <" + graphName + "_file_" + fileId.stringify + "> " + updateQuery
        }
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("update", updateQuery))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

		return null
    
  }
  
  

  def removeDatasetFromGraphs(datasetId: UUID): Null = {
          
        //First, delete all RDF links having to do with files belonging only to the dataset to be deleted, as those files will be deleted together with the dataset
         datasets.get(datasetId) match{
          case Some(dataset)=> {
                var filesString = "" 
	            for(f <- dataset.files){
				      var notTheDataset = for(currDataset<- datasets.findByFileId(f.id) if !dataset.id.toString.equals(currDataset.id.toString)) yield currDataset
				      if(notTheDataset.size == 0){
				        if(f.filename.endsWith(".xml")){
				        	removeFileFromGraphs(f.id, "rdfXMLGraphName")
				        }
				        removeFileFromGraphs(f.id, "rdfCommunityGraphName")
				      }
				      else{
				        if(f.filename.endsWith(".xml")){
				        	detachFileFromDataset(f.id, datasetId, "rdfXMLGraphName")
				        }
				      }
				    }                
	        
	        //Then, delete the dataset itself
	        removeDatasetFromUserGraphs(datasetId)
          }
        }

		return null
    
  }
      
  def sparqlQuery(queryText: String): String = {
    
	    val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/sparql/"
        val httpclient = new DefaultHttpClient()
	    Logger.info("query text: "+ queryText)
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
	    urlParameters.add(new BasicNameValuePair("query", queryText))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
    
        return resultsString
  }
  
  def addFromFile(id: UUID, tempFile: java.io.File, fileOrDataset: String, selectedGraph:String = "rdfCommunityGraphName") : Null = {
    
        val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        
        val fis = new FileInputStream(tempFile)
        val data = new Array[Byte] (tempFile.length().asInstanceOf[Int])
        fis.read(data)
        fis.close()
          
        var updateQuery = new String(data, "UTF-8")       
        
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName + "_"+ fileOrDataset + "_" + id.stringify))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

    return null
  }
  
  
  
}