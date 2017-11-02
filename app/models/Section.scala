package models

/**
 * A portion of a file.
 *
 *
 */
case class Section(
  id: UUID = UUID.generate,
  file_id: UUID = UUID.generate,
  order: Int = -1,
  startTime: Option[Int] = None, // in seconds
  endTime: Option[Int] = None, // in seconds
  area: Option[Rectangle] = None,
  preview: Option[Preview] = None,
  description: Option[String] = None,
  metadataCount: Long = 0,
  thumbnail_id: Option[String] = None,
  tags: List[Tag] = List.empty)

case class Rectangle(
  x: Double,
  y: Double,
  w: Double,
  h: Double) {
  override def toString() = f"x: $x%.2f, y: $y%.2f, width: $w%.2f, height: $h%.2f"
}