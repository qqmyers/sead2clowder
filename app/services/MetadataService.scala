package services

import api.UserRequest
import play.api.libs.Files
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import models.{MetadataDefinition, ResourceRef, UUID, Metadata, User, Agent}
import play.api.mvc.MultipartFormData
import java.util.Date

/**
 * MetadataService for add and query metadata
 */
trait MetadataService {

    /** Add metadata to the metadata collection and attach to a section /file/dataset/collection */
  def addMetadata(content_ld: JsValue, context: JsValue, attachedTo: ResourceRef, createdAt: Date, creator: Agent, spaceId:Option[UUID]): JsObject
  
  /** Add metadata to the metadata collection and attach to a section /file/dataset/collection */
  def addMetadata(metadata: Metadata) : UUID
  
  /** Get Metadata By Id*/
  def getMetadataById(id : UUID) : Option[Metadata]
  
  /** Get Metadata based on Id of an element (section/file/dataset/collection) */
  def getMetadataByAttachTo(resourceRef: ResourceRef): List[Metadata]

  /** Get Extractor metadata by attachTo, from a specific extractor if given */
  def getExtractedMetadataByAttachTo(resourceRef: ResourceRef, extractor: String): List[Metadata]

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(resourceRef: ResourceRef, typeofAgent:String): List[Metadata]

  /** Remove metadata */
  def removeMetadataById(metadataId: UUID)
  
  /** Remove metadata */
  def removeMetadata(attachedTo: ResourceRef, term: String, itemId: String, deletedAt: Date, deletor:Agent, spaceId:Option[UUID]):JsValue

  /** Update metadata value*/
  def updateMetadata(content_ld: JsValue, context: JsValue, attachedTo: ResourceRef, itemId: String, updatedAt: Date, updator:Agent, spaceId:Option[UUID]):JsValue
  
  /** Remove metadata by attachTo*/
  def removeMetadataByAttachTo(resourceRef: ResourceRef): Long

  /** Remove metadata by attachTo from a specific extractor */
  def removeMetadataByAttachToAndExtractor(resourceRef: ResourceRef, extractorName: String): Long
  
  /** Get the current summary of all metadata actions for this resource */
  def getMetadataSummary(resourceRef: ResourceRef, spaceId:Option[UUID]): models.RdfMetadata
  
  /** Get metadata context if available */
  def getMetadataContext(metadataId: UUID): Option[JsValue]

  /** Vocabulary definitions for user fields **/
  def getDefinitions(spaceId: Option[UUID] = None): List[MetadataDefinition]

  /** Vocabulary definitions with distinct names **/
  def getDefinitionsDistinctName(user: Option[User] = None): List[MetadataDefinition]

  /** Get vocabulary based on id **/
  def getDefinition(id: UUID): Option[MetadataDefinition]

  /** Get vocabulary based on uri and space **/
  def getDefinitionByUriAndSpace(uri: String, spaceId: Option[String] = None): Option[MetadataDefinition]

  /** Get vocabulary based on uri and space **/
  def getDefinitionByLabelAndSpace(label: String, spaceId: Option[String] = None): Option[MetadataDefinition]
  
  /** Remove all metadata definitions related to a space**/
  def removeDefinitionsBySpace(spaceId: UUID)

  /** Add vocabulary definitions, leaving it unchanged if the update argument is set to false, defaults to update **/
  def addDefinition(definition: MetadataDefinition)

  /** Edit vocabulary definitions**/
  def editDefinition(id:UUID, json: JsValue)

  /** Delete vocabulary definitions**/
  def deleteDefinition(id: UUID)

  /** Make vocabulary definition appear in "Add Metadata" menu (or not)**/
  def makeDefinitionAddable(id: UUID, addable:Boolean)
  
  /** Search for metadata that have a key in a dataset **/
  def searchbyKeyInDataset(key: String, datasetId: UUID): List[Metadata]

  /** Update author full name**/
  def updateAuthorFullName(userId: UUID, fullName: String)
}
