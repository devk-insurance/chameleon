package de.devk.chameleon

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.CoordinatedShutdownOps
import de.devk.chameleon.input.tcp.TcpServerService
import de.devk.chameleon.jmx.JmxManager

import scala.concurrent.Future

class FlowComposer(config: Config, jmxManager: JmxManager)(implicit system: ActorSystem) extends Logging {

  private val tcpServerService = new TcpServerService(config, jmxManager)
  private val (tcpServerBinding, completionFuture) = tcpServerService.serverBindingFlow

  val serverBindingFlow: Future[Done] = tcpServerBinding.flatMap { value =>
    logger.info(s"Successfully bound to ${value.localAddress}")
    CoordinatedShutdown(system).addShutdownTask(CoordinatedShutdown.PhaseServiceUnbind, "TCP binding")(value.unbind())

    completionFuture
  }(system.dispatcher)

}
