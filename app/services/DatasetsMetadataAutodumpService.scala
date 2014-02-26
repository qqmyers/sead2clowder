package services

import play.api.{ Plugin, Logger, Application }

/**
 * Dataset metadata automatic dump service.
 *
 * @author Constantinos Sophocleous
 *
 */
class DatasetsMetadataAutodumpService (application: Application) extends Plugin {

  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  
  override def onStart() {
    Logger.debug("Starting dataset metadata autodumper Plugin")
  }
  
  override def onStop() {
    Logger.debug("Shutting down dataset metadata autodumper Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("datasetmetadatadumpservice").filter(_ == "disabled").isDefined
  }
  
  def dumpAllDatasetMetadata() = {
    val unsuccessfulDumps = datasets.dumpAllDatasetMetadata
    if(unsuccessfulDumps.size == 0)
      Logger.info("Dumping of datasets metadata was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of datasets metadata was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.info(unsuccessfulMessage)  
    } 
    
  }
  
}