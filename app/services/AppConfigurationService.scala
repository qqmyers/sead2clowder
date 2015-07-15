package services

import models.AppConfiguration

/**
 * Application wide configuration options.
 *
 * Created by lmarini on 2/21/14.
 * 
 * @author Luigi Marini
 * @author Constantinos Sophocleous
 */
trait AppConfigurationService {

  val themes = "bootstrap/bootstrap.css" ::
    "bootstrap-amelia.min.css" ::
    "bootstrap-simplex.min.css" :: Nil

  def getDefault(): Option[AppConfiguration]

  def setTheme(theme: String)

  def getTheme(): String
  
  def addAdmin(newAdminEmail: String)
  
  def removeAdmin(adminEmail: String)
  
  def adminExists(adminEmail: String): Boolean
  
  def setViewNoLoggedIn(viewNoLoggedIn: Boolean)
  
  /** Adds an additional value to the property with the specified key. */
  def addPropertyValue(key: String, value: AnyRef)

  /** Removes the value from the property with the specified key. */
  def removePropertyValue(key: String, value: AnyRef)

  /** Checks to see if the value is part of the property with the specified key. */
  def hasPropertyValue(key: String, value: AnyRef): Boolean

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return None.
   */
  def getProperty[objectType <: AnyRef](key: String): Option[objectType]

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return the default value (empty string if not specified).
   */
  def getProperty[objectType <: AnyRef](key: String, default:objectType): objectType = {
    getProperty[objectType](key) match {
      case Some(x) => x
      case None => default
    }
  }

  /**
   * Sets the configuration property with the specified key to the specified value. If the
   * key already existed it will return the old value, otherwise it returns None.
   */
  def setProperty(key: String, value: AnyRef): Option[AnyRef]

  /**
   * Remove the configuration property with the specified key and returns the value if any
   * was set, otherwise it will return None.
   */
  def removeProperty(key: String): Option[AnyRef]
  
}

object AppConfiguration {
  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
  
  // ----------------------------------------------------------------------
    
  /** Set singular and plural display aliases for Datasets and Collections */
  def setResourceDisplayAlias(alias: String, resourceType: String, plurality: String) = appConfig.setProperty("resourceAliases."+resourceType
		  					+(if (plurality.trim().toLowerCase().equals("plural")) ".plural" else ".singular"), alias)

  /** Get singular and plural display aliases for Datasets and Collections */
  def getResourceDisplayAlias(resourceType: String, plurality: String) = {
    val pluralityTrimmed = plurality.trim().toLowerCase()
    appConfig.getProperty("resourceAliases."+resourceType+(if (pluralityTrimmed.equals("plural")) ".plural" else ".singular"),
    						resourceType.capitalize+(if (pluralityTrimmed.equals("plural")) "s" else ""))
  }
  
  /** Get singular and plural display aliases for Datasets and Collections together */
  def getResourceDisplayAliases() = {
    Map("dSingular" -> getResourceDisplayAlias("dataset","singular"), "dPlural" -> getResourceDisplayAlias("dataset","plural"),
        "cSingular" -> getResourceDisplayAlias("collection","singular"), "cPlural" -> getResourceDisplayAlias("collection","plural"))
  }
    
  // ----------------------------------------------------------------------
  
}
