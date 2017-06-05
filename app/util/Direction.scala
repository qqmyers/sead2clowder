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

object SpaceSelector extends Enumeration {
  type SpaceSelector = Value

  val ALL, MINE, SHARED = Value
}

import api.Permission
import api.Permission.Permission
import models.UUID
import util.Direction._
import util.SortBy._
import util.SpaceSelector._

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
  // maximum number of results
  limit: Integer = 20,
  // permission requested on searched items
  permission: Permission,
  // limit to spaces shared with others
  sharedSpaces: Boolean = false,
  // limit to spaced that are mine
  showAll: Boolean = true,
  // add spaces that are public
  showPublic: Boolean = true
)
