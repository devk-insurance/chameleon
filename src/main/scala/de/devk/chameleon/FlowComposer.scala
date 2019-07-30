package de.devk.chameleon

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import de.devk.chameleon.input.tcp.TcpServerService
import de.devk.chameleon.jmx.JmxManager

import scala.concurrent.Future

class FlowComposer(config: Config, jmxManager: JmxManager, shutdownHookService: ShutdownHookService)(implicit system: ActorSystem, materializer: ActorMaterializer) extends Logging {

  private val tcpServerService = new TcpServerService(config, jmxManager, shutdownHookService)
  private val (tcpServerBinding, completionFuture) = tcpServerService.serverBindingFlow

  val serverBindingFlow: Future[Done] = tcpServerBinding.flatMap { value =>
    logger.info(s"Successfully bound to ${value.localAddress}")
    shutdownHookService.prependShutdownHook("TCP binding")(value.unbind())

    completionFuture
  }(system.dispatcher)

}
