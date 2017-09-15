package models

import play.api.libs.json.{JsPath, Reads, Json, JsValue}
import play.api.libs.functional.syntax._

/**
  * Promoted extractor-generated metadata fields
  */
case class PromotedMetadata (
  id: UUID = UUID.generate(),
  spaceId: Option[UUID] = None,
  json: JsValue
    /*
      uri: String, // metadata field URI (Also, it's unique name that includes hostname, exractor name )
      label: String, // metadata field name
      type: String // metadata field type
    */
)

object PromotedMetadata {

  implicit val promotedMetadataFormat = Json.format[PromotedMetadata]

  implicit val promotedMetadataReads: Reads[PromotedMetadata] = (
    (JsPath \ "id").read[UUID] and
      (JsPath \ "space_id").read[Option[UUID]] and
      (JsPath \ "json").read[JsValue]
    )(PromotedMetadata.apply _)

  implicit val promotedMetadataWrites = Json.writes[PromotedMetadata]
}
