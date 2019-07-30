package de.devk.chameleon

import akka.stream.scaladsl.Flow
import com.typesafe.config.Config
import de.devk.chameleon.jmx.JmxManager

import scala.concurrent.ExecutionContext

object Implicits {

  implicit class ConfigOpts(config: Config) {
    def getOptionalString(path: String): Option[String] = {
      if (config.hasPath(path)) Some(config.getString(path))
      else None
    }
  }

  implicit class FlowOps[-In, +Out, +Mat](flow: Flow[In, Out, Mat]) {
    def registerMBean(name: String, mbean: AnyRef)(implicit jmxManager: JmxManager, unregisterExecutionContext: ExecutionContext): Flow[In, Out, Mat] = {
      jmxManager.registerMBean(name, mbean)

      flow
        .watchTermination() { (m, completionFuture) =>
          completionFuture.onComplete { _ =>
            jmxManager.unregisterMBean(name)
          }(unregisterExecutionContext)

          m
        }
    }
  }

}
