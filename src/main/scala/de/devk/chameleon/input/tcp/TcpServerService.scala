package de.devk.chameleon.input.tcp

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.util.ByteString
import com.typesafe.config.Config
import de.devk.chameleon.input.filter.EventFilterService
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.output.InfluxDbService
import de.devk.chameleon.{Logging, ShutdownHookService}

import scala.concurrent.Future

class TcpServerService(config: Config, jmxManager: JmxManager, shutdownHookService: ShutdownHookService)(implicit system: ActorSystem, materializer: ActorMaterializer) extends Logging {

  private val graphiteInterface = config.getString("graphite.interface")
  private val graphitePort = config.getInt("graphite.port")

  private val connections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp().bind(graphiteInterface, graphitePort)

  private val tcpClientConnectionService = new TcpClientService(config, jmxManager, shutdownHookService)
  private val eventFilterService = new EventFilterService(config, jmxManager)
  private val influxDbService = new InfluxDbService(config, jmxManager, shutdownHookService)

  def serverBindingFlow: (Future[Tcp.ServerBinding], Future[Done]) = connections.toMat(Sink.foreach { connection =>
    val remoteAddress = connection.remoteAddress.toString

    val flow = Flow[ByteString]
      .via(tcpClientConnectionService.clientConnectionFlow(remoteAddress))
      .via(eventFilterService.eventFilterFlow(remoteAddress))
      .via(influxDbService.influxDbFlow)
      .map(_ => ByteString.empty)

    connection.handleWith(flow)
  })(Keep.both).run()


}
