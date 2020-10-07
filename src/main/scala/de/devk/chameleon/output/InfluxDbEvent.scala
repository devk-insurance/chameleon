package de.devk.chameleon.output

import org.apache.commons.text.translate.{AggregateTranslator, LookupTranslator}

import java.util.Collections
import scala.jdk.CollectionConverters._

case class InfluxDbEvent(
                        measurement: String,
                        tags: Map[String, String],
                        fields: Map[String, String],
                        timestamp: String) {
  val lineData: String = {
    val lineMeasurement = escapeMeasurement(measurement)
    val lineTagSet = tags.map { case (key, value) =>
      s"${escapeTagKey(key)}=${escapeTagValue(value)}"
    }
    val lineMeasurementAndTagSet = lineTagSet.toSeq.prepended(lineMeasurement)

    val lineFieldSet = fields.map { case (key, value) =>
      s"${escapeFieldKey(key)}=${escapeFieldValue(value)}"
    }

    s"${lineMeasurementAndTagSet.mkString(",")} ${lineFieldSet.mkString(",")} $timestamp"
  }

  private def escapeFieldValue(fieldValue: String): String = {
    escapeMap(Map(
      "\"" -> "\\\"",
      "\\" -> "\\\\"
    )).translate(fieldValue)
  }

  private def escapeTagKey(tagKey: String): String = {
    escapeMap(Map(
      "," -> "\\,",
      "=" -> "\\=",
      " " -> "\\ "
    )).translate(tagKey)
  }

  private def escapeTagValue(tagValue: String): String =
    escapeTagKey(tagValue)

  private def escapeFieldKey(fieldKey: String): String =
    escapeTagKey(fieldKey)

  private def escapeMeasurement(measurement: String): String = {
    escapeMap(Map(
      "," -> "\\,",
      " " -> "\\ "
    )).translate(measurement)
  }

  private def escapeMap(map: Map[CharSequence, CharSequence]): AggregateTranslator = new AggregateTranslator(
    new LookupTranslator(Collections.unmodifiableMap(map.asJava))
  )
}
