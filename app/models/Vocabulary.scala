package models

import java.util.Date

import play.api.libs.json.{Json, JsValue, Writes}
import securesocial.core.Identity

/**
  * Created by todd_n on 2/8/16.
  */
case class Vocabulary (
  id : UUID = UUID.generate(),
  author : Option[Identity],
  created : Date = new Date(),
  name : String = "",
  lastModified : Date = new Date(),
  keys : List[String] = List.empty,
  description : List[String] = List.empty,
  spaces : List[UUID] = List.empty,
  isPublic : Boolean = false)


object Vocabulary{
  implicit val vocabularyWrites = new Writes[Vocabulary] {
    def writes(vocabulary : Vocabulary) : JsValue = {
      val vocabularyAuthor = vocabulary.author.get.identityId.userId
      Json.obj("id" -> vocabulary.id.toString,"author" -> vocabularyAuthor, "name" -> vocabulary.name,
        "keys" -> vocabulary.keys.toList.toString, "description" -> vocabulary.description.toList)
    }
  }
}
