package services

import play.Logger
import util.ResourceLister

/**
 * Application wide information regarding metadata, such as
 * user metadata definitions.
 *
 * @author Rui Liu
 */
trait MetadataInfoService {
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

/**
 * Object to handle some common configuration options.
 */
object MetadataInfo {
  val metadataInfo: MetadataInfoService = DI.injector.getInstance(classOf[MetadataInfoService])

  /*
   * Retrieve the global user metadata definitions.
   */
  def retrieveGlobalUserMetadataDefs = {
    import play.api.libs.ws._
    import scala.concurrent.Future
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    val url = play.Play.application.configuration.getString("userMetadataDefinition.url.global")
    Logger.debug("In conf file, userMetadataDefinition.url.global = " + url)
    // Per Luigi's suggestion, leave the global defs unchanged if no url configured.
    if (url != null) {
      val url_files_nodes    = url + "/files/nodes.txt"
      val url_files_relas    = url + "/files/relationships.txt"
      val url_datasets_nodes = url + "/datasets/nodes.txt"
      val url_datasets_relas = url + "/datasets/relationships.txt"
      WS.url(url_files_nodes).get().map { response =>
        metadataInfo.setProperty("userMetadataDef_files_nodes", response.body.trim)
      }
      WS.url(url_files_relas).get().map { response =>
        metadataInfo.setProperty("userMetadataDef_files_relas", response.body.trim)
      }
      WS.url(url_datasets_nodes).get().map { response =>
        metadataInfo.setProperty("userMetadataDef_datasets_nodes", response.body.trim)
      }
      WS.url(url_datasets_relas).get().map { response =>
        metadataInfo.setProperty("userMetadataDef_datasets_relas", response.body.trim)
      }
    }
  }

}
