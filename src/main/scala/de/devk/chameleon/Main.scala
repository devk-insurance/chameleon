package de.devk.chameleon

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import de.devk.chameleon.jmx.JmxManager

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object MainImpl extends Main(ConfigFactory.load())

abstract class Main(mainConfig: Config) extends App with Logging {

  logger.info("Starting Chameleon service")
  val shutdownHookService = new ShutdownHookService

  implicit val system: ActorSystem = ActorSystem()
  shutdownHookService.prependShutdownHook("Actor system")(system.terminate())
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val config = mainConfig.getConfig("chameleon")

  val jmxManager = new JmxManager
  val completionFuture = new FlowComposer(config, jmxManager, shutdownHookService).serverBindingFlow

  completionFuture.onComplete {
    case Success(_) =>
      logger.info(s"Successfully unbound")
    case Failure(exception) =>
      logger.error(s"Error in TCP stream, exiting", exception)
      sys.exit(1)
  }

  sys.addShutdownHook {
    logger.info("Starting shutdown")

    shutdownHookService.awaitShutdown(10.seconds)
  }

}
