package services

import play.api.{Plugin, Logger, Application}

class UserMetadataPlugin(application: Application) extends Plugin {

  override def onStart() {
    Logger.debug("Community-generated metadata plugin started")
  }
  
  override def onStop() {
    Logger.debug("Shutting down community-generated metadata plugin.")
  }
  
  override lazy val enabled = {
    !application.configuration.getString("usermetadataplugin").filter(_ == "disabled").isDefined
  }
}