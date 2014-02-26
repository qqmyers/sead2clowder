package services

import play.api.{ Plugin, Logger, Application }

/**
 * File metadata automatic dump service.
 *
 * @author Constantinos Sophocleous
 *
 */
class FileMetadataAutodumpService (application: Application) extends Plugin {

  val files: FileService = DI.injector.getInstance(classOf[FileService])
  
  override def onStart() {
    Logger.debug("Starting file metadata autodumper Plugin")
  }
  
  override def onStop() {
    Logger.debug("Shutting down file metadata autodumper Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("filemetadatadumpservice").filter(_ == "disabled").isDefined
  }
  
  def dumpAllFileMetadata() = {
    val unsuccessfulDumps = files.dumpAllFileMetadata
    if(unsuccessfulDumps.size == 0)
      Logger.info("Dumping of files metadata was successful for all files.")
    else{
      var unsuccessfulMessage = "Dumping of files metadata was successful for all files except file(s) with id(s) "
      for(badFile <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badFile + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Logger.info(unsuccessfulMessage)  
    } 
    
  }
  
}