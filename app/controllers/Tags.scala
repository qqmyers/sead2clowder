package controllers

import javax.inject.Inject

import models.User
import play.api.Logger
import play.api.Play.current
import services.{CollectionService, DatasetService, FileService, SectionService, ElasticsearchPlugin}
import util.Parsers

import scala.collection.mutable.ListBuffer

/**
 * Tagging.
 */
class Tags @Inject()(collections: CollectionService, datasets: DatasetService, files: FileService, sections: SectionService) extends SecuredController {

  /**
   * Search for a tag and display the page to the user. This will allow for pagination allowing the user
   * to go forwards and backwards through all tags. The user can specify the number of datasets and files
   * to display.
   *
   * The code will query the datasets, files and sections and combine the lists into a single sorted list
   * and display it to the user.
   */
  def search(tag: String, start: String, size: Integer, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    var nextItems = ListBuffer.empty[AnyRef]
    var prevItems = ListBuffer.empty[AnyRef]
    var tempItems = ListBuffer.empty[models.Dataset]

    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")

    // get all datasets tagged
    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the datasets names or descriptions
    tempItems ++= datasets.findByTag(tagCleaned, start, size + 1, false, user)
    for (aDataset <- tempItems) {
      nextItems += Utils.decodeDatasetElements(aDataset)
    }
    tempItems = ListBuffer.empty[models.Dataset]
    if (start != "") {
      tempItems ++= datasets.findByTag(tagCleaned, start, size + 1, true, user)
      for (aDataset <- tempItems) {
        prevItems += Utils.decodeDatasetElements(aDataset)
      }
    }

    // get all files tagged
    nextItems ++= files.findByTag(tagCleaned, start, size + 1, false, user)
    if (start != "") prevItems ++= files.findByTag(tagCleaned, start, size + 1, true, user)

    // get all sections tagged
    // TODO this logic should be moved to findByTag in sections.
    val sectionFiles = sections.findByTag(tagCleaned, user).map{ s => files.get(s.file_id) match {
      case Some(f) => f
    } }
    if (start == "" ) {
      nextItems ++= sectionFiles
      prevItems ++= sectionFiles
    } else {
      val startDate = Parsers.fromISO8601(start)
      nextItems ++= sectionFiles.filter(f => f.uploadDate.compareTo(startDate) <= 0)
      prevItems ++= sectionFiles.filter(f => f.uploadDate.compareTo(startDate) >= 0)
    }

    // order final list
    nextItems = nextItems.sortBy(_ match {
      case d: models.Dataset => d.created
      case f: models.File => f.uploadDate
    }).reverse
    prevItems = prevItems.sortBy(_ match {
      case d: models.Dataset => d.created
      case f: models.File => f.uploadDate
    })

    // check if there are next items
    val next = if (nextItems.size > size) {
      nextItems(size) match {
        case d: models.Dataset => Parsers.toISO8601(d.created)
        case f: models.File => Parsers.toISO8601(f.uploadDate)
      }
    } else ""

    // check if there are prev items
    val prev = if (prevItems.size > 1) {
      prevItems(Math.min(prevItems.length-1, size)) match {
        case d: models.Dataset => Parsers.toISO8601(d.created)
        case f: models.File => Parsers.toISO8601(f.uploadDate)
      }
    } else ""

    // check if there is a prev item

    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14   
    val viewMode: Option[String] = 
    if (mode == null || mode == "") {
      request.cookies.get("view-mode") match {
          case Some(cookie) => Some(cookie.value)
          case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
      }
    } else {
        Some(mode)
    }
      
    //Pass the viewMode into the view            
    Ok(views.html.searchByTag(tag, nextItems.slice(0, size).toList, prev, next, size, viewMode))
  }

  def tagListWeighted() = PrivateServerAction { implicit request =>
    implicit val user = request.user
    val tags = computeTagWeights(user)
    if (tags.isEmpty) {
      Ok(views.html.tagList(List.empty[(String, Double)]))
    } else {
      val minFont = current.configuration.getDouble("tag.list.minFont").getOrElse(1.0)
      val maxFont = current.configuration.getDouble("tag.list.maxFont").getOrElse(5.0)
      val maxWeight = tags.maxBy(_._2)._2
      val minWeight = tags.minBy(_._2)._2
      val divide = (maxFont - minFont) / (maxWeight - minWeight)

      Ok(views.html.tagList(tags.map { case (k, v) => (k, minFont + (v - minWeight) * divide) }))
    }
  }

  def tagListOrdered() = PrivateServerAction { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagListChar(createTagList(user)))
  }

  def createTagList(user: Option[User]) = {
    val tagMap = collection.mutable.Map.empty[Char, collection.mutable.Map[String, Long]]

    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) if plugin.isEnabled() => {
        val results = plugin.listTags()
        for ((k,v) <- results) {
          val firstChar = if (k(0).isLetter) k(0).toUpper else '#'
          if (!tagMap.contains(firstChar)) {
            val map = collection.mutable.Map[String, Long]((k, v)).withDefaultValue(0)
            tagMap(firstChar) = map
          } else {
            val map = tagMap(firstChar)
            map(k) = map(k) + v
          }
        }
      }
      case _ => {
        Logger.error("ElasticSearch plugin could not be reached for tag search; using Mongo instead")

        datasets.getTags(user).foreach { case (tag: String, count: Long) =>
          if (tag.length == 0) {
            Logger.error("tag with length 0 : " + tag + " " + count)
          } else {
            val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
            if (!tagMap.contains(firstChar)) {
              val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
              tagMap(firstChar) = map
            } else {
              val map = tagMap(firstChar)
              map(tag) = map(tag) + count
            }
          }
        }
        files.getTags(user).foreach { case (tag: String, count: Long) =>
          if (tag.length == 0) {
            Logger.error("tag with length 0 : " + tag + " " + count)
          } else {
            val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
            if (!tagMap.contains(firstChar)) {
              val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
              tagMap(firstChar) = map
            } else {
              val map = tagMap(firstChar)
              map(tag) = map(tag) + count
            }
          }
        }
        sections.getTags(user).foreach { case (tag: String, count: Long) =>
          if (tag.length == 0) {
            Logger.error("tag with length 0 : " + tag + " " + count)
          } else {
            val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
            if (!tagMap.contains(firstChar)) {
              val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
              tagMap(firstChar) = map
            } else {
              val map = tagMap(firstChar)
              map(tag) = map(tag) + count
            }
          }
        }
      }
    }

    tagMap.map{ case (k, v) => (k, v.toMap)}.toMap
  }

  def computeTagWeights(user: Option[User]) = {
    val weightedTags = collection.mutable.Map.empty[String, Long].withDefaultValue(0)

    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) if plugin.isEnabled() => {
        for ((k, v) <- plugin.listTags("dataset")) {
          weightedTags(k) = weightedTags(k) + v * current.configuration.getInt("tags.weight.dataset").getOrElse(1)
        }
        for ((k, v) <- plugin.listTags("file")) {
          weightedTags(k) = weightedTags(k) + v * current.configuration.getInt("tags.weight.files").getOrElse(1)
        }
        for ((k, v) <- plugin.listTags("section")) {
          weightedTags(k) = weightedTags(k) + v * current.configuration.getInt("tags.weight.sections").getOrElse(1)
        }
      }
      case _ => {
        Logger.error("ElasticSearch plugin could not be reached for tag search; using Mongo instead")
        datasets.getTags(user).foreach { case (tag: String, count: Long) =>
          weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.dataset").getOrElse(1)
        }

        files.getTags(user).foreach { case (tag: String, count: Long) =>
          weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.files").getOrElse(1)
        }

        sections.getTags(user).foreach { case (tag: String, count: Long) =>
          weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.sections").getOrElse(1)
        }
      }
    }

    weightedTags.toList
  }
}
