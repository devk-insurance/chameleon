package de.devk.chameleon.main

import java.time.Instant
import java.util.UUID

import scala.util.Random

trait TestDataProvider {
  def validTestData: TestData = {
    val metric = UUID.randomUUID().toString
    val value = Random.nextInt()

    TestData(metric, value, s"testhost.testmeasurement.$metric $value ${Instant.now().getEpochSecond}")
  }
}

case class TestData(metric: String, value: Int, line: String)