package de.devk.chameleon.main

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.util.ByteString
import de.devk.chameleon.Logging
import play.api.libs.json.{JsArray, JsString, JsValue, Json}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object InfluxDbActor {
  case class QueryRecord(metric: String)
  case class RecordFound(value: Int)
}
class InfluxDbActor(responseActor: ActorRef)
  extends Actor
  with Logging {

  import InfluxDbActor._
  import akka.pattern.pipe
  import context.{dispatcher, system}

  override def receive: Receive = {
    case queryMessage @ QueryRecord(metric) =>
      logger.debug(s"Sending request to InfluxDB: $metric")

      Http().singleRequest(HttpRequest(uri = influxDbQueryUri(metric)))
        .map((queryMessage, _))
        .pipeTo(self)

    case (queryMessage: QueryRecord, HttpResponse(StatusCodes.OK, _, entity, _)) =>
      logger.debug(s"Received response from InfluxDB: $entity")

      entity.dataBytes.runFold(ByteString(""))(_ ++ _)
        .map { byteString =>
          (queryMessage, Json.parse(byteString.utf8String))
        }
        .pipeTo(self)

    case (queryMessage: QueryRecord, json: JsValue) =>
      Try {
        val series = json("results")(0)("series")(0)
        val valueColumnIndex = series("columns").as[JsArray].value.zipWithIndex
          .find(_._1.as[JsString].value == "value")
          .map(_._2)
          .getOrElse(throw new RuntimeException("Unable to find column 'value'"))

        series("values")(0)(valueColumnIndex).as[Int]
      } match {
        case Success(value) =>
          logger.debug(s"Received valid JSON from InfluxDB: $value")

          responseActor ! RecordFound(value)
        case Failure(exception) =>
          logger.trace(s"Received bad JSON from InfluxDB: $json, retrying", exception)

          context.system.scheduler.scheduleOnce(1.second, self, queryMessage)
      }

    case unknown =>
      logger.error(s"Unable to handle message: $unknown")
  }

  private def influxDbQueryUri(metric: String): Uri =
    Uri()
      .withScheme("http")
      .withHost("127.0.0.1")
      .withPort(8086)
      .withPath(Uri.Path("/query"))
      .withQuery(Uri.Query(
        ("db", "metrics"),
        ("p", "s"),
        ("q", s"SELECT * FROM testmeasurement WHERE metric = '$metric'")
      ))
}
