package services

import models.AppAppearance

/**
 * @author Constantinos Sophocleous
 */
trait AppAppearanceService {

  def getDefault(): Option[AppAppearance]
  
  def setDisplayedName(displayedName: String)
  
  def setWelcomeMessage(welcomeMessage: String)
  
}