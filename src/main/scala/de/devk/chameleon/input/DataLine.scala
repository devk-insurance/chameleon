package de.devk.chameleon.input

sealed trait DataLine

case class GraphiteData(hostname: String, measurement: String, metric: String, value: String, timestamp: String) extends DataLine

case class UnknownData(data: String) extends DataLine
