package de.devk.chameleon.output

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.{ConfigOpts, CoordinatedShutdownOps, StringOps}
import de.devk.chameleon.Logging
import de.devk.chameleon.hosttags.HostTagService
import de.devk.chameleon.input.GraphiteData
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.jmx.global.GlobalInfluxDbMetrics

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InfluxDbService(config: Config, jmxManager: JmxManager)(implicit actorSystem: ActorSystem) extends Logging {

  private val influxDbDatabase = config.getString("influxdb.database")

  private val influxDbMetrics = new GlobalInfluxDbMetrics
  jmxManager.registerMBean("de.devk.chameleon.global:type=GlobalInfluxDbMetrics", influxDbMetrics)

  private val influxDbWriteUri = {
    val params = Map(
      "consistency" -> config.getOptionalString("influxdb.consistency"),
      "db" -> Some(influxDbDatabase),
      "p" -> config.getOptionalString("influxdb.password"),
      "precision" -> config.getOptionalString("graphite.timestamp.precision"),
      "rp" -> config.getOptionalString("influxdb.retention.policy.name"),
      "u" -> config.getOptionalString("influxdb.username")
    )
    .collect{ case (key, Some(value) ) => key -> value }

    Uri()
      .withPath(Uri.Path("/write"))
      .withQuery(Uri.Query(params))
  }

  private val influxDbHost = config.getString("influxdb.host")
  private val influxDbPort = config.getInt("influxdb.port")
  private val influxDbWriteBatchSize = config.getInt("influxdb.write.batchSize")

  private val connectionPoolFlow: Flow[(HttpRequest, NotUsed), (Try[HttpResponse], NotUsed), Http.HostConnectionPool] =
    Http().cachedHostConnectionPool(influxDbHost, influxDbPort)

  logger.info(s"Started HTTP connection pool for $influxDbHost:$influxDbPort")
  CoordinatedShutdown(actorSystem).addShutdownTask(CoordinatedShutdown.PhaseServiceStop, "HTTP connection pools")(Http().shutdownAllConnectionPools())

  private val httpFlow: Flow[HttpRequest, Try[HttpResponse], NotUsed] =
    Flow[HttpRequest]
        .map(r => (r, NotUsed))
        .via(connectionPoolFlow)
        .map(_._1)

  val influxDbFlow: Flow[InfluxDbEvent, Done, NotUsed] = Flow[InfluxDbEvent]
    .batch(influxDbWriteBatchSize, seed => Seq(seed))((seed, element) => seed :+ element)
    .map { data =>
      logger.debug(s"Sending ${data.size} events to InfluxDB, entity: ${data.mkString(", ").logEscape}")
      influxDbMetrics.incrementHttpRequests()

      HttpRequest(
        entity = HttpEntity(data.map(_.lineData).mkString("\n")),
        method = HttpMethods.POST,
        uri = influxDbWriteUri
      )
    }
    .via(httpFlow)
    .mapAsync(1) {
      case Success(value) =>
        logger.debug(s"Received response from InfluxDB, entity: ${value.entity}")
        influxDbMetrics.incrementSuccessfulHttpResponses()

        value.discardEntityBytes().future
      case Failure(exception) =>
        logger.error("Error on InfluxDB request, not retrying", exception)
        influxDbMetrics.incrementErrorHttpResponses()
        Future.successful(Done)
    }

  def graphiteDataToInfluxDbEventFlow(hostTagService: HostTagService): Flow[GraphiteData, InfluxDbEvent, NotUsed] = Flow[GraphiteData].map { data =>
    val influxDbEvent = InfluxDbEvent(
      measurement = data.measurement,
      tags = Map(
        "host" -> data.hostname,
        "metric" -> data.metric
      ),
      fields = Map(
        "value" -> data.value
      ),
      timestamp = data.timestamp
    )

    (data.hostname, influxDbEvent)
  }.mapAsync(1) { case (hostname, influxDbEvent) =>
    if (hostTagService.enabled) {
      hostTagService.tags(hostname).map { tagsFromDb =>
        logger.debug(s"Found ${tagsFromDb.size} tags for hostname $hostname")

        influxDbEvent.copy(
          tags = influxDbEvent.tags ++ tagsFromDb
        )
      }(actorSystem.dispatcher)
    } else {
      Future.successful(influxDbEvent)
    }
  }
}
