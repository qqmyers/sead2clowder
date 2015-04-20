package services

import java.io.InputStream
import models.{UUID, Thumbnail}

/**
 * Created by lmarini on 2/27/14.
 * @author Constantinos Sophocleous
 * @author Luigi Marini
 */
trait ThumbnailService {

  def get(thumbnailId: UUID): Option[Thumbnail]

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)]
}
