package services

import play.api.{ Plugin, Logger, Application }

/**
 * Dataset file groupings automatic dump service.
 *
 * @author Constantinos Sophocleous
 *
 */
class DatasetsAutodumpService (application: Application) extends Plugin {

  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  
  override def onStart() {
    Logger.debug("Starting dataset file groupings autodumper Plugin")
  }
  
  override def onStop() {
    Logger.debug("Shutting down dataset file groupings autodumper Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("datasetsdumpservice").filter(_ == "disabled").isDefined
  }
  
  def dumpDatasetGroupings() = {
    
    val unsuccessfulDumps = datasets.dumpAllDatasetGroupings
    if(unsuccessfulDumps.size == 0)
      Logger.info("Dumping of dataset file groupings was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of dataset file groupings was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.info(unsuccessfulMessage)  
    }      
  }
  
}