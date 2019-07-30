package de.devk.chameleon.jmx

import java.lang.management.ManagementFactory

import akka.stream.scaladsl.Flow
import javax.management.ObjectName

import scala.concurrent.ExecutionContext

class JmxManager {

  private val mbs = ManagementFactory.getPlatformMBeanServer

  def registerMBean(name: String, mbean: AnyRef): Unit = {
    val objectName = new ObjectName(name)

    mbs.registerMBean(mbean, objectName)
  }

  def registerFlowMBean[T](name: String, mbean: AnyRef)(implicit unregisterExecutionContext: ExecutionContext): Flow[T, T, Unit] = {
    registerMBean(name, mbean)

    Flow[T]
      .watchTermination()((m, completionFuture) =>
      completionFuture.onComplete { _ =>
        unregisterMBean(name)

        m
      }(unregisterExecutionContext))
  }

  def unregisterMBean(name: String): Unit = {
    val objectName = new ObjectName(name)

    mbs.unregisterMBean(objectName)
  }

}
