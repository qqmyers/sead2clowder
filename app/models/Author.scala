package models

import play.api.libs.json.{Json, JsObject, Writes}

/**
  * Author will eventually replace author : MiniUser in File or Datasets
  * This way a separate 'uploader' or 'creator' can exist.
  */
case class Author(
              id : UUID = UUID.generate(),
                 author : MiniUser,
                 uploader : Option[MiniUser],
                 creator : String = "")

object Author {
  implicit object AuthorWrites extends Writes[Author] {
    def writes(author: Author): JsObject = {
      Json.obj(
        "id" -> author.id,
        "name" -> author.author.fullName,
        "creator"->author.creator)
    }
  }
}
