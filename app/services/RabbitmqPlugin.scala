package services

import java.io.IOException
import java.net.{URI, URL}
import java.text.SimpleDateFormat
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.util.Date

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.ning.http.client.Realm.AuthScheme
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory, DefaultConsumer, Envelope}
import models.{Extraction, UUID}
import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.ws.{Response, WS}
import play.api.{Application, Logger, Plugin}
import play.libs.Akka
import play.api.libs.json.JsValue

import scala.concurrent.Future
import scala.util.Try


// TODO make optional fields Option[UUID]

case class ExtractorMessage(
  fileId: UUID,
  intermediateId: UUID,
  host: String,
  key: String,
  metadata: Map[String, Any],
  fileSize: String,
  datasetId: UUID,
  flags: String,
  secretKey: String = play.api.Play.configuration.getString("commKey").getOrElse(""))

/**
 * Rabbitmq service.
 *
 */
class RabbitmqPlugin(application: Application) extends Plugin {
  val files: FileService = DI.injector.getInstance(classOf[FileService])

  var extractQueue: Option[ActorRef] = None
  var channel: Option[Channel] = None
  var connection: Option[Connection] = None
  var factory: Option[ConnectionFactory] = None
  var restURL: Option[String] = None
  var event_filter: Option[ActorRef] = None
  var vhost: String = ""
  var username: String = ""
  var password: String = ""
  var rabbitmquri: String = ""
  var exchange: String = ""
  var mgmtPort: String = ""

  override def onStart() {
    Logger.debug("Starting Rabbitmq Plugin")
    val configuration = play.api.Play.configuration
    rabbitmquri = configuration.getString("clowder.rabbitmq.uri").getOrElse("amqp://guest:guest@localhost:5672/%2f")
    exchange = configuration.getString("clowder.rabbitmq.exchange").getOrElse("clowder")
    mgmtPort = configuration.getString("clowder.rabbitmq.managmentPort").getOrElse("15672")
    Logger.debug("uri= "+ rabbitmquri)

    try {
      val uri = new URI(rabbitmquri)
      factory = Some(new ConnectionFactory())
      factory.get.setUri(uri)
    } catch {
      case t: Throwable => {
        factory = None
        Logger.error("Invalid URI for RabbitMQ", t)
      }
    }
  }

  override def onStop() {
    Logger.debug("Shutting down Rabbitmq Plugin")
    factory = None
    close()
  }

  override lazy val enabled = {
    !application.configuration.getString("rabbitmqplugin").filter(_ == "disabled").isDefined
  }

  def close() {
    Logger.debug("Closing connection")
    extractQueue = None
    restURL = None
    vhost = ""
    username = ""
    password = ""
    if (channel.isDefined) {
      Logger.debug("Channel closing")
      try {
        channel.get.close()
      } catch {
        case e: Exception => Logger.error("Error closing channel.", e)
      }
      channel = None
    }
    if (connection.isDefined) {
      Logger.debug("Connection closing")
      try {
      connection.get.close()
      } catch {
        case e: Exception => Logger.error("Error closing connection.", e)
      }
      connection = None
    }

    if (event_filter.isDefined) {
      event_filter.get ! PoisonPill
      event_filter = None
    }
    if (extractQueue.isDefined) {
      extractQueue.get ! PoisonPill
      extractQueue = None
    }
  }

  def connect: Boolean = {
    if (channel.isDefined) return true
    if (!factory.isDefined) return true

    val configuration = play.api.Play.configuration

    try {
      val protocol = if (factory.get.isSSL) "https://" else "http://"
      restURL = Some(protocol + factory.get.getHost +  ":" + mgmtPort)
      vhost = URLEncoder.encode(factory.get.getVirtualHost)
      username = factory.get.getUsername
      password = factory.get.getPassword
            
      connection = Some(factory.get.newConnection())
      channel = Some(connection.get.createChannel())

      Logger.debug("vhost: "+ vhost)
      
      // setup exchange if provided
      if (exchange != "") {
        channel.get.exchangeDeclare(exchange, "topic", true)
      }
     
      // create an anonymous queue for replies
      val replyQueueName = channel.get.queueDeclare().getQueue
      Logger.debug("Reply queue name: " + replyQueueName)

      // status consumer
      Logger.info("Starting extraction status receiver")

      event_filter = Some(Akka.system.actorOf(
        Props(new EventFilter(channel.get, replyQueueName)),
        name = "EventFilter"
      ))

      Logger.debug("Initializing a MsgConsumer for the EventFilter")
      channel.get.basicConsume(
        replyQueueName,
        false, // do not auto ack
        "event_filter", // tagging the consumer is important if you want to stop it later
        new MsgConsumer(channel.get, event_filter.get)
      )

      // setup akka for sending messages
      extractQueue = Some(Akka.system.actorOf(Props(new SendingActor(channel = channel.get,
        exchange = exchange,
        replyQueueName = replyQueueName))))

      true
    } catch {
      case t: Throwable => {
        Logger.error("Error connecting to rabbitmq broker", t)
        close()
        false
      }
    }
  }

  // ----------------------------------------------------------------------
  // EXTRACTOR MESSAGE
  // ----------------------------------------------------------------------
  def extract(message: ExtractorMessage) = {
    Logger.debug("Sending message " + message)
    connect
    extractQueue match {
      case Some(x) => x ! message
      case None => Logger.warn("Could not send message over RabbitMQ")
    }
  }

  // ----------------------------------------------------------------------
  // RABBITMQ MANAGEMENT ENDPOINTS
  // ----------------------------------------------------------------------
  def getRestEndPoint(path: String): Future[Response] = {
    connect

    restURL match {
      case Some(x) => {
        val url = x + path
        Logger.trace("RESTURL: "+ url)
        WS.url(url).withHeaders("Accept" -> "application/json").withAuth(username, password, AuthScheme.BASIC).get()
      }
      case None => {
        Logger.warn("Could not get bindings")
        Future.failed(new IOException("Not connected"))
      }
    }
  }

 /**
 * Get the exchange list for a given host
 */
  def getExchanges : Future[Response] = {
    connect
    getRestEndPoint("/api/exchanges/" + vhost )
  }
  
  /**
   * get list of queues attached to an exchange
   */
  def getQueuesNamesForAnExchange(exchange: String): Future[Response] = {
    connect
    getRestEndPoint("/api/exchanges/"+ vhost +"/"+ exchange +"/bindings/source")
  }
  
  /**
   * Get the binding lists (lists of routing keys) from the rabbitmq broker
   */
  def getBindings: Future[Response] = {
    getRestEndPoint("/api/bindings")
  }

  /**
   * Get Channel list from rabbitmq broker
   */

  def getChannelsList: Future[Response] = {
    getRestEndPoint("/api/channels")
  }
  
  /**
   * Get queue details for a given queue
   */

  def getQueueDetails(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname)
  }


  /**
   * Get queue bindings for a given host and queue from rabbitmq broker
   */

  def getQueueBindings(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname + "/bindings")
  }

  /**
   * Get Channel information from rabbitmq broker for given channel id 'cid'
   */
  def getChannelInfo(cid: String): Future[Response] = {
    getRestEndPoint("/api/channels/" + cid)
  }
}

  /**
   * Send message on specified channel and exchange, and tells receiver to reply
   * on specified queue.
   */
class SendingActor(channel: Channel, exchange: String, replyQueueName: String) extends Actor {
  val appHttpPort = play.api.Play.configuration.getString("http.port").getOrElse("")
  val appHttpsPort = play.api.Play.configuration.getString("https.port").getOrElse("")

  def receive = {
    case ExtractorMessage(id, intermediateId, host, key, metadata, fileSize, datasetId, flags, secretKey) => {
      var theDatasetId = ""
      if (datasetId != null)
        theDatasetId = datasetId.stringify

      var actualHost = host
      //Tell the extractors to use https if webserver is so configured
      if (!appHttpsPort.equals("")) {
        actualHost = host.replaceAll("^http:", "https:").replaceFirst(":" + appHttpPort, ":" + appHttpsPort)
      }

      val msgMap = scala.collection.mutable.Map(
        "id" -> Json.toJson(id.stringify),
        "intermediateId" -> Json.toJson(intermediateId.stringify),
        "fileSize" -> Json.toJson(fileSize),
        "host" -> Json.toJson(actualHost),
        "datasetId" -> Json.toJson(theDatasetId),
        "flags" -> Json.toJson(flags),
        "secretKey" -> Json.toJson(secretKey)
      )
      // add extra fields
      metadata.foreach(kv =>
        kv._2 match {
          case x: JsValue => msgMap.put(kv._1, x)
          case x: String => msgMap.put(kv._1, Json.toJson(x))
          case _ => msgMap.put(kv._1, Json.toJson(kv._2.toString))
        }

      )
      val msg = Json.toJson(msgMap.toMap)
      // correlation id used for rpc call
      val corrId = java.util.UUID.randomUUID().toString() // TODO switch to models.UUID?
      // setup properties
      val basicProperties = new BasicProperties().builder()
          .contentType("application\\json")
          .correlationId(corrId)
          .replyTo(replyQueueName)
          .build()
      try {
        channel.basicPublish(exchange, key, true, basicProperties, msg.toString().getBytes())
      } catch {
        case e: Exception => {
          Logger.error("Error connecting to rabbitmq broker", e)
          current.plugin[RabbitmqPlugin].foreach {
            _.close()
          }
        }
      }
    }
    case _ => {
      Logger.error("Unknown message type submitted.")
    }
  }
}

/**
 * Listen for responses coming back on replyQueue
 */
class MsgConsumer(channel: Channel, target: ActorRef) extends DefaultConsumer(channel) {
  override def handleDelivery(consumer_tag: String,
                              envelope: Envelope,
                              properties: BasicProperties,
                              body: Array[Byte]) {
    val delivery_tag = envelope.getDeliveryTag
    val body_text = new String(body)

    target ! body_text
    channel.basicAck(delivery_tag, false)
  }
}

/**
 * Actual message on reply queue
 */
class EventFilter(channel: Channel, queue: String) extends Actor {
  val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])

  def receive = {
    case statusBody: String =>
      Logger.debug("Received extractor status: " + statusBody)
      val json = Json.parse(statusBody)
      val file_id = UUID((json \ "file_id").as[String])
      val extractor_id = (json \ "extractor_id").as[String]
      val status = (json \ "status").as[String]
      val startDate = (json \ "start").asOpt[String].map(x =>
        Try(Date.from(ZonedDateTime.parse(x).toInstant)).getOrElse {
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(x)
        })
      val updatedStatus = status.toUpperCase()
      //TODO : Enforce consistent status updates: STARTED, DONE, ERROR and
      //       other detailed status updates to logs when we start implementing
      //       distributed logging
      if (updatedStatus.contains("DONE")) {
        extractions.insert(Extraction(UUID.generate(), file_id, extractor_id, "DONE", startDate, None))
      } else {
        extractions.insert(Extraction(UUID.generate(), file_id, extractor_id, status, startDate, None))
      }
      Logger.debug("updatedStatus=" + updatedStatus + " status=" + status + " startDate=" + startDate)
      models.ExtractionInfoSetUp.updateDTSRequests(file_id, extractor_id)
  }
}
