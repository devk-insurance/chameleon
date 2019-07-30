package de.devk.chameleon

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class ShutdownHookService extends Logging {

  private var shutdownHooks: Seq[() => Future[_]] = Seq.empty

  def prependShutdownHook(name: String)(f: => Future[_]): Unit = shutdownHooks.synchronized {
    shutdownHooks = shutdownHooks.+: (() => {
      logger.info(s"Running shutdown hook $name")
      f
    })
  }

  def awaitShutdown(timeout: FiniteDuration): Unit = {
    logger.info("Starting shutdown")
    Try(Await.result(resultFutureFunction(), timeout)) match {
      case Success(_) =>
        logger.info(s"Successfully stopped application")
      case Failure(exception) =>
        logger.error(s"Error during application shutdown", exception)
    }
  }

  private def resultFutureFunction: () => Future[_] =
    shutdownHooks.fold(() => Future.successful(())) { (f1, f2) =>
      () => f1().flatMap(_ => f2())
    }
}
