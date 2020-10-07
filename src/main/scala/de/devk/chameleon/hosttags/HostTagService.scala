package de.devk.chameleon.hosttags

import akka.NotUsed
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.CoordinatedShutdownOps
import de.devk.chameleon.Logging
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.jmx.hostTags.HostTagsMetrics
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json

import scala.concurrent.{Await, Future}
import scala.jdk.DurationConverters._
import scala.util.{Failure, Success, Try}

class HostTagService(config: Config, jmxManager: JmxManager)(implicit actorSystem: ActorSystem) extends FailFastCirceSupport with Logging {

  type Hostname = String
  type HostTags = Map[String, String]

  private val hostTagsMetrics = new HostTagsMetrics
  jmxManager.registerMBean("de.devk.chameleon.hostTags:type=HostTagsMetrics", hostTagsMetrics)

  private var database: Database = _

  val enabled: Boolean = Try(config.getBoolean("hostTags.enabled")).getOrElse(false)

  if (enabled) {
    start()
  } else {
    logger.info("Not enabling host tag support, because config properties not set")
  }

  def tags(hostname: String): Future[Map[String, String]] = {
    if (enabled) {
      database.tagsForHost(hostname)
    } else {
      Future.failed(new IllegalStateException("Host tags are not enabled"))
    }
  }

  private def start(): Unit = {
    val startupDelay = config.getDuration("hostTags.timer.startupDelay").toScala
    val interval = config.getDuration("hostTags.timer.interval").toScala
    val checkMkUri = Uri(config.getString("hostTags.checkMk.url"))

    database = new Database(config, jmxManager)

    logger.info(s"Enabling host tag support, updating every $interval")

    val updateTask = actorSystem.scheduler.scheduleWithFixedDelay(startupDelay, interval) (() => {
      logger.info("Updating host tags")
      Await.ready(updateHostTags(database, checkMkUri), interval)
        .onComplete {
          case Success(numberOfInsertedHostTags) => logger.info(s"Inserted or updated $numberOfInsertedHostTags host tags")
          case Failure(e) => logger.error("Error when updating host tags", e)
        }(actorSystem.dispatcher)
    })(actorSystem.dispatcher)

    CoordinatedShutdown(actorSystem).addShutdownTask(CoordinatedShutdown.PhaseServiceUnbind, "Host tag update") {
      updateTask.cancel()
      Future.successful()
    }
  }

  private def updateHostTags(database: Database, checkMkUri: Uri)(implicit actorSystem: ActorSystem): Future[Int] =
    requestHostTagsFromCheckMkServer(checkMkUri)
      .mapAsync(1) { case (hostname, tags) =>
        val insertedOrUpdatedHostTags = database.insertOrUpdateHostTags(hostname, tags)
        hostTagsMetrics.addUpdatedHostTags(1, tags.size)

        insertedOrUpdatedHostTags
      }
      .toMat(Sink.fold(0) { (inserted, _) => inserted + 1 })(Keep.right).run()

  private def requestHostTagsFromCheckMkServer(checkMkUri: Uri): Source[(Hostname, HostTags), NotUsed] =
    Source
      .future(Http()(actorSystem).singleRequest(HttpRequest(uri = checkMkUri)))
      .mapAsync(1)(Unmarshal(_).to[Json])
      .mapConcat { json =>
        val hcursor = json.hcursor

        val resultCode = hcursor.get[Int]("result_code") match {
          case Left(decodingFailure) =>
            throw new IllegalArgumentException("Unable to parse result_code", decodingFailure)
          case Right(value) =>
            value
        }

        if (resultCode != 0) {
          throw new IllegalArgumentException(s"Result code does not equal to zero, $resultCode")
        }

        val defaultTagValue = "null"

        val result = hcursor.downField("result")
        result.keys.get.map { key =>
          val host = result.downField(key)
          val hostname = host.get[String]("hostname").getOrElse(throw new IllegalArgumentException("Unable to parse hostname"))
          val attributes = host.downField("attributes")
          val attributeKeys = attributes.keys.getOrElse(throw new IllegalArgumentException("Unable to parse attributes"))
          val tagKeys = attributeKeys
            .filter(_.startsWith("tag_"))

          val tags = tagKeys
            .map(key =>
              key.substring(key.indexOf("tag_") + "tag_".length) ->
                attributes.get[Option[String]](key)
                  .getOrElse(throw new IllegalArgumentException(s"Unable to parse attribute $key"))
                  .getOrElse(defaultTagValue))
            .toMap

          hostname -> tags
        }.toMap
      }
}
