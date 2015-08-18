package services

import models._

/**
 * Service to manipulate curation objects.
 */
trait CurationService {
  /**
   * Create a new CO.
   */
  def insert(curation: CurationObject)

  /**
   * Get collection.
   */
  def get(id: UUID): Option[CurationObject]
}
