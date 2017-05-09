package util


/**
  * Enumeration for Direction
 */
object Direction extends Enumeration {
  type Direction = Value
  val ASC, DESC = Value
}

object SortBy extends Enumeration {
  type SortBy = Value

  val DATE, TITLE, AUTHOR = Value
}

import models.UUID
import util.Direction.Direction
import util.SortBy.SortBy

case class SearchOptions(
  // by what should the results be sorted
  sortBy: SortBy = SortBy.DATE,
  // what direction should results be sorted in
  direction: Direction = Direction.DESC,
  // given the sort, this will be the first/last item key on current page
  last: Option[String] = None,
  // given the sort, this will be the first/last item id on current page
  lastID: Option[UUID] = None,
  // only return results of the following space
  space: Option[UUID] = None,
  // only return results with the following words in the title
  title: Option[String] = None,
  // only return results for the following user
  owner: Option[UUID] = None,
  // should public results be returned
  public: Boolean = false,
  // maximum number of results
  limit: Integer = 20
)
