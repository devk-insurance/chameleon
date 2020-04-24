package de.devk.chameleon.main

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.io.Tcp.Register
import akka.io.{IO, Tcp}
import akka.util.ByteString
import de.devk.chameleon.Logging
import de.devk.chameleon.main.TestRunnerActor.{QueryInfluxDb, SendCheckMkData, SendCheckMkDataAndQueryInfluxDb}

import scala.concurrent.duration._

object TestRunnerActor {
  case class SendCheckMkData(data: String)
  case class QueryInfluxDb(metric: String)
  case class SendCheckMkDataAndQueryInfluxDb(metric: String, data: String)
}
class TestRunnerActor(remote: InetSocketAddress, testActor: ActorRef)
  extends Actor
  with Stash
  with Logging {

  import context.{dispatcher, system}

  val influxDbActor: ActorRef = system.actorOf(Props(new InfluxDbActor(self)))

  IO(Tcp) ! Tcp.Connect(remote)

  override def receive: Receive = connect

  def connect: Receive = {
    case Tcp.CommandFailed(_: Tcp.Connect) =>
      logger.debug("TCP connection failed, retrying")
      context.system.scheduler.scheduleOnce(100.milliseconds, self, Tcp.Connect)

    case _: Tcp.Connected =>
      logger.debug("TCP connection established, sending CheckMK data and querying InfluxDB")

      val connection = sender()
      connection ! Register(self)

      context.become(sendCheckMkData(connection))

      unstashAll()

    case _: SendCheckMkDataAndQueryInfluxDb =>
      stash()
    case _: SendCheckMkData =>
      stash()
    case _: QueryInfluxDb =>
      stash()
  }

  def sendCheckMkData(connection: ActorRef): Receive = {
    case SendCheckMkData(data) =>
      connection ! Tcp.Write(ByteString(s"$data\n"))

    case QueryInfluxDb(metric) =>
      influxDbActor ! InfluxDbActor.QueryRecord(metric)

    case SendCheckMkDataAndQueryInfluxDb(metric, data) =>
      self ! SendCheckMkData(data)

      self ! QueryInfluxDb(metric)

    case InfluxDbActor.RecordFound(value) =>
      logger.debug(s"Received query response from InfluxDB: $value")

      testActor ! value
  }

}
