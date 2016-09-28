package models

import java.util.Date

import play.api.libs.json.{Json, JsValue, Writes}

/**
  * Created by todd_n on 2/8/16.
  */
case class Vocabulary (
  id : UUID = UUID.generate(),
  author : Option[MiniUser],
  created : Date = new Date(),
  name : String = "",
  lastModified : Date = new Date(),
  keys : List[String] = List.empty,
  description : String = "",
  spaces : List[UUID] = List.empty,
  tags : List[String] = List.empty,
  isPublic : Boolean = false,
  terms : List[UUID] = List.empty)


object Vocabulary{
  implicit val vocabularyWrites = new Writes[Vocabulary] {
    def writes(vocabulary : Vocabulary) : JsValue = {
      Json.obj("id" -> vocabulary.id.toString,"author" -> vocabulary.author, "name" -> vocabulary.name,
        "keys" -> vocabulary.keys.toList, "description" -> vocabulary.description,"spaces"->vocabulary.spaces.toList)
    }
  }
}
