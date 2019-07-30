package de.devk.chameleon.input.filter

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, Sink}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.FlowOps
import de.devk.chameleon.Logging
import de.devk.chameleon.input.{DataLine, GraphiteData, UnknownData}
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.jmx.client.ClientConnectionEventFilterMetrics
import de.devk.chameleon.jmx.global.GlobalEventFilterMetrics
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

class EventFilterService(config: Config, jmxManager: JmxManager) extends Logging {

  private val knownFormatLogger = LoggerFactory.getLogger("de.devk.chameleon.KnownFormat")
  private val unknownFormatLogger = LoggerFactory.getLogger("de.devk.chameleon.UnknownFormat")

  private val GraphiteLineRegex: Regex = config.getString("graphite.regex").r
  private val GraphiteCStringStyleEscapedCharacterRegex: Regex = config.getString("graphite.cStringStyleEscapedCharacterRegex").r

  private val eventFilterMetrics = new GlobalEventFilterMetrics

  logger.info(s"Using regex '$GraphiteLineRegex' to match Graphite events and '$GraphiteCStringStyleEscapedCharacterRegex' to match C string style escaped characters")

  jmxManager.registerMBean("de.devk.chameleon.global:type=GlobalEventFilterMetrics", eventFilterMetrics)

  private val unknownDataSink = Sink.ignore

  private val graphiteDataFilterFlow: Flow[DataLine, GraphiteData, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val graphiteDataFilter = b.add(Flow[DataLine].collect { case g: GraphiteData => g })
    val unknownDataFilter = b.add(Flow[DataLine].collect { case u: UnknownData => u })

    val partitions = b.add(Partition[DataLine](2, {
      case _: GraphiteData => DataLinePartition.GraphiteData
      case _: UnknownData => DataLinePartition.UnknownData
    }))

    partitions.out(DataLinePartition.UnknownData) ~> unknownDataFilter ~> unknownDataSink

    FlowShape(partitions.in, (partitions.out(DataLinePartition.GraphiteData) ~> graphiteDataFilter).outlet)
  })

  def eventFilterFlow(remoteAddress: String): Flow[String, GraphiteData, NotUsed] = {
    val clientConnectionEventFilterMetrics = new ClientConnectionEventFilterMetrics(remoteAddress)

    Flow[String]
      .registerMBean(s"""de.devk.chameleon.client:type=ClientEventFilterMetrics,remoteAddress="$remoteAddress"""", clientConnectionEventFilterMetrics)(jmxManager, ExecutionContext.global)
      .map {
        case GraphiteLineRegex(hostname, measurement, metric, value, timestamp) =>
          val unescapedHostname = GraphiteCStringStyleEscapedCharacterRegex.replaceAllIn(hostname, m => octalToAscii(m.matched))
          val unescapedMeasurement = GraphiteCStringStyleEscapedCharacterRegex.replaceAllIn(measurement, m => octalToAscii(m.matched))
          val unescapedMetric = GraphiteCStringStyleEscapedCharacterRegex.replaceAllIn(metric, m => octalToAscii(m.matched))

          logger.debug(
            s"""Line is in known format:
               |escaped:   hostname=$hostname, measurement=$measurement, metric=$metric, value=$value, timestamp=$timestamp
               |unescaped: hostname=$unescapedHostname, measurement=$unescapedMeasurement, metric=$unescapedMetric, value=$value, timestamp=$timestamp
               |""".stripMargin)
          knownFormatLogger.info(s"$remoteAddress: hostname=$hostname, measurement=$measurement, metric=$metric, value=$value, timestamp=$timestamp")

          eventFilterMetrics.incrementValidIncomingLines()
          clientConnectionEventFilterMetrics.incrementValidIncomingLines()

          GraphiteData(unescapedHostname, unescapedMeasurement, unescapedMetric, value, timestamp)
        case other =>
          logger.debug(s"Line '$other' is in unknown format, ignoring")
          unknownFormatLogger.info(s"$remoteAddress: $other")

          eventFilterMetrics.incrementBadIncomingLines()
          clientConnectionEventFilterMetrics.incrementBadIncomingLines()

          UnknownData(other)
      }
      .via(graphiteDataFilterFlow)
  }

  private def octalToAscii(octalEscapedCharacter: String): String = {
    if (!octalEscapedCharacter.startsWith("\\"))
      throw new IllegalArgumentException("The escaped character must start with a backslash")

    // Drop the backslash
    Character.toString(Integer.parseInt(octalEscapedCharacter.drop(1), 8))
  }

}
