package de.devk.chameleon.output

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.ConfigOpts
import de.devk.chameleon.Implicits.CoordinatedShutdownOps
import de.devk.chameleon.input.GraphiteData
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.jmx.global.GlobalInfluxDbMetrics
import de.devk.chameleon.Logging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InfluxDbService(config: Config, jmxManager: JmxManager)(implicit actorSystem: ActorSystem) extends Logging {

  private val influxDbDatabase = config.getString("influxdb.database")

  private val influxDbMetrics = new GlobalInfluxDbMetrics
  jmxManager.registerMBean("de.devk.chameleon.global:type=GlobalInfluxDbMetrics", influxDbMetrics)

  private val influxDbWriteUri = {
    val params = Map(
      "consistency" -> config.getOptionalString("influxdb.consitency"),
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

  val influxDbFlow: Flow[GraphiteData, Done, NotUsed] = Flow[GraphiteData]
    .batch(influxDbWriteBatchSize, seed => Seq(seed))((seed, element) => seed :+ element)
    .map { data =>
      logger.debug(s"Sending ${data.size} events to InfluxDB, entity: $data")
      influxDbMetrics.incrementHttpRequests()

      HttpRequest(
        entity = HttpEntity(data.map(influxDbString).mkString("\n")),
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

  private def influxDbString(gL: GraphiteData): String =
    s"${gL.measurement},host=${gL.hostname},metric=${gL.metric} value=${gL.value} ${gL.timestamp}"

}
