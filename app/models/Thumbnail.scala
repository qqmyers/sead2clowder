package models

/**
 * Thumbnails for datasets and files.
 * @author Luigi Marini
 * @author Constantinos Sophocleous
 */
case class Thumbnail(
id: UUID = UUID.generate,
filename: Option[String] = None,
contentType: String,
length: Long)

