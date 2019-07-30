package de.devk.chameleon

import org.slf4j.{Logger, LoggerFactory}

trait Logging {
  self =>

  val logger: Logger = LoggerFactory.getLogger(self.getClass)

}
