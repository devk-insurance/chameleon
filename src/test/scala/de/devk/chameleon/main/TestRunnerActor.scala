package de.devk.chameleon.main

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.io.Tcp.Register
import akka.io.{IO, Tcp}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import de.devk.chameleon.Logging
import de.devk.chameleon.main.TestRunnerActor.SendCheckMkData

import scala.concurrent.duration._

object TestRunnerActor {
  case class SendCheckMkData(metric: String, data: String)
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

    case _: SendCheckMkData =>
      stash()
  }

  def sendCheckMkData(connection: ActorRef): Receive = {
    case SendCheckMkData(metric, data) =>
      connection ! Tcp.Write(ByteString(s"$data\n"))

      influxDbActor ! InfluxDbActor.QueryRecord(metric)

    case InfluxDbActor.RecordFound(value) =>
      logger.debug(s"Received query response from InfluxDB: $value")

      testActor ! value
  }

}
