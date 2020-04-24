package de.devk.chameleon

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import de.devk.chameleon.jmx.JmxManager

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object MainImpl extends Main(ConfigFactory.load())

abstract class Main(mainConfig: Config) extends App with Logging {

  logger.info("Starting Chameleon service")

  implicit val system: ActorSystem = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val config = mainConfig.getConfig("chameleon")

  val jmxManager = new JmxManager
  val completionFuture = new FlowComposer(config, jmxManager).serverBindingFlow

  completionFuture.onComplete {
    case Success(_) =>
      logger.info(s"Successfully unbound")
    case Failure(exception) =>
      logger.error(s"Error in TCP stream, exiting", exception)
      sys.exit(1)
  }

}
