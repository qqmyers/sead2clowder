package models

import play.api.libs.json.JsValue

/**
  * Promoted extractor-generated metadata fields
  */
case class PromotedMetadata (
  id: UUID = UUID.generate(),
  spaceId: Option[UUID] = None,
  json: JsValue
    /*
      uri: String, // metadata field URL
      label: String, // metadata field name
      type: String // metadata field type
    */
)
