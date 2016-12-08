package services

import play.api.{Application, Logger, Plugin}
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import java.net.InetAddress

import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders
import models.{Collection, Dataset, File, UUID}

import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray
import java.text.SimpleDateFormat

import org.elasticsearch.ElasticsearchException
import play.api.Play.current


/**
 * Elasticsearch plugin.
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  var client: Option[TransportClient] = None
  val nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("clowder")
  val serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("localhost")
  val serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)

  override def onStart() {
    Logger.debug("Elasticsearchplugin started but not yet connected to Elasticsearch")
  }

  def connect(): Boolean = {
    if (client.isDefined) {
      return true
    }
    try {
      val settings = if (nameOfCluster != "") {
        Settings.settingsBuilder().put("cluster.name", nameOfCluster).build()
      } else {
        Settings.settingsBuilder().build()
      }
      client = Some(TransportClient.builder().settings(settings).build()
    		  .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(serverAddress), serverPort)))
      Logger.debug("--- ElasticSearch Client is being created----")
      client match {
        case Some(x) => {
          Logger.debug("Index \"data\"  is being created if it does not exist ---")
          val indexSettings = Settings.settingsBuilder().loadFromSource(jsonBuilder()
            .startObject()
            .startObject("analysis")
            .startObject("analyzer")
            .startObject("default")
            .field("type", "snowball")
            .endObject()
            .endObject()
            .endObject()
            .endObject().string())
          val indexExists = x.admin().indices().prepareExists("data").execute().actionGet().isExists()
          if (!indexExists) {
            x.admin().indices().prepareCreate("data").setSettings(indexSettings).execute().actionGet()
          }
          Logger.info("Connected to Elasticsearch")
          true
        }
        case None => {
          Logger.error("Error connecting to elasticsearch: No Client Created")
          false
        }
      }

    } catch {
      case nn: NoNodeAvailableException => {
        Logger.error("Error connecting to elasticsearch: " + nn)
        client.map(_.close())
        client = None
        false
      }
      case _: Throwable => {
        Logger.error("Unknown exception connecting to elasticsearch")
        client.map(_.close())
        client = None
        false
      }
    }
  }

  def search(index: String, query: String): SearchResponse = {
    connect

    client match {
      case Some(x) => {
        try {
          Logger.debug("Searching ElasticSearch for " + query)
          val response = x.prepareSearch(index)
            .setTypes("file", "dataset","collection")
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .setQuery(QueryBuilders.queryStringQuery(query).analyzer("snowball"))
            .setFrom(0).setSize(60).setExplain(true)
            .execute()
            .actionGet()
          Logger.debug("Search hits: " + response.getHits().getTotalHits())
          response
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
            new SearchResponse()
          }
        }
      }
      case None => {
        Logger.error("Could not call search because we are not connected.")
        new SearchResponse()
      }
    }
  }

  def search(index: String, fields: Array[String], query: String): SearchResponse = {
    connect
    client match {
      case Some(x) => {
        try {
          Logger.debug("Searching ElasticSearch for " + query)
          var qbqs = QueryBuilders.queryStringQuery(query)
          for (f <- fields) {
            qbqs.field(f.trim())
          }
          val response = x.prepareSearch(index)
            .setTypes("file", "dataset","collection")
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .setQuery(qbqs.analyzer("snowball"))
            .setFrom(0).setSize(60).setExplain(true)
            .execute()
            .actionGet()
          Logger.debug("Search hits: " + response.getHits().getTotalHits())
          response
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
            new SearchResponse()
          }
        }
      }
      case None => {
        Logger.error("Could not call search because we are not connected.")
        new SearchResponse()
      }
    }
  }

  /**
   * Index document using an arbitrary map of fields.
   */
  def index(index: String, docType: String, id: UUID, fields: List[(String, String)]) {
    connect
    client match {
      case Some(x) => {
        try {
          val builder = jsonBuilder()
            .startObject()
          fields.map(fv => builder.field(fv._1, fv._2))
          builder.endObject()
          val response = x.prepareIndex(index, docType, id.toString())
            .setSource(builder)
            .execute()
            .actionGet()
          Logger.debug("Indexing document: " + response.getId)
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call index.", e)
            client = None
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  /** delete all indices */
  def deleteAll {
    connect
    client match {
      case Some(x) => {
        try {
          val response = x.admin().indices().prepareDelete("_all").get()
          if (!response.isAcknowledged())
            Logger.error("Did not delete all data from elasticsearch.")
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
            new SearchResponse()
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  def delete(index: String, docType: String, id: String) {
    connect
    client match {
      case Some(x) => {
        try {
          val response = x.prepareDelete(index, docType, id).execute().actionGet()
          Logger.debug("Deleting document: " + response.getId)
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
            new SearchResponse()
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }

  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  def index(collection: Collection, recursive: Boolean) {
    var dsCollsId = ""
    var dsCollsName = ""

    for (dataset <- datasets.listCollection(collection.id.stringify)) {
      if (recursive) {
        index(dataset, recursive)
      }
      dsCollsId = dsCollsId + dataset.id.stringify + " %%% "
      dsCollsName = dsCollsName + dataset.name + " %%% "
    }

    val formatter = new SimpleDateFormat("dd/MM/yyyy")

    index("data", "collection", collection.id,
      List(("name", collection.name),
        ("description", collection.description),
        ("created", formatter.format(collection.created)),
        ("datasetId", dsCollsId),
        ("datasetName", dsCollsName)))
  }

  /**
   * Reindex the given dataset, if recursive is set to true it will
   * also reindex all files.
   */
  def index(dataset: Dataset, recursive: Boolean) {
    var tagListBuffer = new ListBuffer[String]()

    for (tag <- dataset.tags) {
      tagListBuffer += tag.name
    }

    val tagsJson = new JSONArray(tagListBuffer.toList)

    Logger.debug("tagStr=" + tagsJson)

    val commentsByDataset = for (comment <- comments.findCommentsByDatasetId(dataset.id, false)) yield {
      comment.text
    }
    val commentJson = new JSONArray(commentsByDataset)

    Logger.debug("commentStr=" + commentJson.toString())

    val usrMd = datasets.getUserMetadataJSON(dataset.id)
    Logger.debug("usrmd=" + usrMd)

    val techMd = datasets.getTechnicalMetadataJSON(dataset.id)
    Logger.debug("techmd=" + techMd)

    val xmlMd = datasets.getXMLMetadataJSON(dataset.id)
    Logger.debug("xmlmd=" + xmlMd)

    var fileDsId = ""
    var fileDsName = ""
    for (fileId <- dataset.files) {
      files.get(fileId) match {
        case Some(f) => {
          if (recursive) {
            index(f)
          }
          fileDsId = fileDsId + f.id.stringify + "  "
          fileDsName = fileDsName + f.filename + "  "
        }
        case None => Logger.error(s"Error getting file $fileId")

      }
    }

    var dsCollsId = ""
    var dsCollsName = ""

    dataset.collections.foreach(c => {
      collections.get(c).foreach(collection => {
        dsCollsId = dsCollsId + collection.id.stringify + " %%% "
        dsCollsName = dsCollsName + collection.name + " %%% "
      })
    })

    val formatter = new SimpleDateFormat("dd/MM/yyyy")

    index("data", "dataset", dataset.id,
      List(("name", dataset.name),
        ("description", dataset.description),
        ("author", dataset.author.fullName),
        ("created", formatter.format(dataset.created)),
        ("fileId", fileDsId),
        ("fileName", fileDsName),
        ("collId", dsCollsId),
        ("collName", dsCollsName),
        ("tag", tagsJson.toString()),
        ("comments", commentJson.toString()),
        ("usermetadata", usrMd),
        ("technicalmetadata", techMd),
        ("xmlmetadata", xmlMd)))
  }

  /**
   * Reindex the given file.
   */
  def index(file: File) {
    var tagListBuffer = new ListBuffer[String]()

    for (tag <- file.tags) {
      tagListBuffer += tag.name
    }

    val tagsJson = new JSONArray(tagListBuffer.toList)

    Logger.debug("tagStr=" + tagsJson)

    val commentsByFile = for (comment <- comments.findCommentsByFileId(file.id)) yield {
      comment.text
    }
    val commentJson = new JSONArray(commentsByFile)

    Logger.debug("commentStr=" + commentJson.toString())

    val usrMd = files.getUserMetadataJSON(file.id)
    Logger.debug("usrmd=" + usrMd)

    val techMd = files.getTechnicalMetadataJSON(file.id)
    Logger.debug("techmd=" + techMd)

    val xmlMd = files.getXMLMetadataJSON(file.id)
    Logger.debug("xmlmd=" + xmlMd)

    var fileDsId = ""
    var fileDsName = ""

    for (dataset <- datasets.findByFileId(file.id)) {
      fileDsId = fileDsId + dataset.id.stringify + " %%% "
      fileDsName = fileDsName + dataset.name + " %%% "
    }

    val formatter = new SimpleDateFormat("dd/MM/yyyy")

    index("data", "file", file.id,
      List(("filename", file.filename),
        ("contentType", file.contentType),
        ("author", file.author.fullName),
        ("uploadDate", formatter.format(file.uploadDate)),
        ("datasetId", fileDsId),
        ("datasetName", fileDsName),
        ("tag", tagsJson.toString()),
        ("comments", commentJson.toString()),
        ("usermetadata", usrMd),
        ("technicalmetadata", techMd),
        ("xmlmetadata", xmlMd)))
  }

  override def onStop() {
    client.map(_.close())
    client = None
    Logger.info("ElasticsearchPlugin has stopped")
  }

}
