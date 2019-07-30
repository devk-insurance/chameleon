package de.devk.chameleon.input.tcp

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source, Tcp}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.FlowOps
import de.devk.chameleon.input.filter.EventFilterService
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.jmx.client.ClientTcpConnectionMetrics
import de.devk.chameleon.jmx.global.GlobalTcpConnectionMetrics
import de.devk.chameleon.output.InfluxDbService
import de.devk.chameleon.{Logging, ShutdownHookService}
import org.apache.commons.text.StringEscapeUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class TcpClientService(config: Config, jmxManager: JmxManager, shutdownHookService: ShutdownHookService)(implicit system: ActorSystem, materializer: ActorMaterializer) extends Logging {

  private val graphiteEventSizeBytes = config.getInt("graphite.eventSize.bytes")

  private val globalTcpConnectionMetrics = new GlobalTcpConnectionMetrics
  jmxManager.registerMBean("de.devk.chameleon.global:type=GlobalTcpConnectionMetrics", globalTcpConnectionMetrics)

  def clientConnectionFlow(remoteAddress: String): Flow[ByteString, String, NotUsed] = Flow[ByteString]
    .via(clientConnectionDataFlow(remoteAddress))
    .via(clientConnectionCloseFlow(remoteAddress))

  private def clientConnectionCloseFlow[T](remoteAddress: String): Flow[T, T, Unit] = Flow[T]
    .watchTermination()((m, completionFuture) =>
      completionFuture.onComplete { f =>
        f match {
          case Success(_) =>
            logger.info(s"Closed TCP connection for $remoteAddress successfully")
          case Failure(exception) =>
            logger.error(s"TCP connection for $remoteAddress failed", exception)
        }

        globalTcpConnectionMetrics.incrementClosedConnections()
        globalTcpConnectionMetrics.decrementCurrentlyEstablishedConnections()

        m
      }(scala.concurrent.ExecutionContext.global))

  private def clientConnectionDataFlow(remoteAddress: String): Flow[ByteString, String, NotUsed ] = {
    logger.info(s"Connection established by $remoteAddress")

    val clientConnectionMetricsMBeanName = s"""de.devk.chameleon.client:type=ClientTcpConnectionMetrics,remoteAddress="$remoteAddress""""
    val tcpConnectionMetrics = new ClientTcpConnectionMetrics(remoteAddress)

    globalTcpConnectionMetrics.incrementEstablishedConnections()
    globalTcpConnectionMetrics.incrementCurrentlyEstablishedConnections()

    Flow[ByteString]
      .registerMBean(clientConnectionMetricsMBeanName, tcpConnectionMetrics)(jmxManager, ExecutionContext.global)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = graphiteEventSizeBytes, allowTruncation = true))
      .map { b =>
        globalTcpConnectionMetrics.incrementIncomingLines()
        tcpConnectionMetrics.incrementIncomingLines()

        StringEscapeUtils.escapeJava(b.utf8String)
      }
  }

}
