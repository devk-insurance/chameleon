package de.devk.chameleon.main

import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import de.devk.chameleon.Main
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.duration._
import scala.util.Random

class MainTest extends TestKit(ActorSystem("test"))
  with ImplicitSender
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with TestDataProvider {

  behavior of classOf[Main].getSimpleName

  it should "save CheckMK data in InfluxDB" in {
    val testData = validTestData

    val testRunnerActor = system.actorOf(Props(new TestRunnerActor(new InetSocketAddress("127.0.0.1", 2003), testActor)))
    testRunnerActor ! TestRunnerActor.SendCheckMkDataAndQueryInfluxDb(testData.metric, testData.line)

    expectMsg(10.seconds, testData.value)
    expectNoMessage()
  }

  it should "save valid CheckMK data in InfluxDB only" in {
    val testData1 = validTestData
    val testData2 = validTestData

    val testRunnerActor = system.actorOf(Props(new TestRunnerActor(new InetSocketAddress("127.0.0.1", 2003), testActor)))
    testRunnerActor ! TestRunnerActor.SendCheckMkDataAndQueryInfluxDb(testData1.metric, testData1.line)
    testRunnerActor ! TestRunnerActor.SendCheckMkDataAndQueryInfluxDb(UUID.randomUUID().toString, "invalid data")
    testRunnerActor ! TestRunnerActor.SendCheckMkDataAndQueryInfluxDb(testData2.metric, testData2.line)
    testRunnerActor ! TestRunnerActor.SendCheckMkDataAndQueryInfluxDb(UUID.randomUUID().toString, "invalid data")

    expectMsgAllOf(10.seconds, testData1.value, testData2.value)
    expectNoMessage()
  }

  it should "save escaped data" in {
    val uuid = UUID.randomUUID().toString
    val testRunnerActor = system.actorOf(Props(new TestRunnerActor(new InetSocketAddress("127.0.0.1", 2003), testActor)))
    testRunnerActor ! TestRunnerActor.SendCheckMkData(s"\\060testhost\t\\061.testmeasurement.\\062${uuid}testmetric 1234 ${Instant.now().getEpochSecond}")
    testRunnerActor ! TestRunnerActor.QueryInfluxDb(s"2${uuid}testmetric")

    expectMsg(10.seconds, 1234)
    expectNoMessage()
  }

  it should "save data with utf8 emoticons" in {
    val uuid = UUID.randomUUID().toString
    val randomNumber = Random.nextInt
    val testRunnerActor = system.actorOf(Props(new TestRunnerActor(new InetSocketAddress("127.0.0.1", 2003), testActor)))
    testRunnerActor ! TestRunnerActor.SendCheckMkData(s"\\060testhost\t\\061.testmeasurement.\\062${uuid}test\uD83D\uDC14metric $randomNumber ${Instant.now().getEpochSecond}")
    testRunnerActor ! TestRunnerActor.QueryInfluxDb(s"2${uuid}test\uD83D\uDC14metric")

    expectMsg(10.seconds, randomNumber)
    expectNoMessage()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val main = new Main(ConfigFactory.load("application-test")) {}
    main.main(Array.empty)
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)

    super.afterAll()
  }

}