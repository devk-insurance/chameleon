package de.devk.chameleon.main

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import de.devk.chameleon.Main
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

import scala.concurrent.duration._

class MainTest extends TestKit(ActorSystem("test"))
  with ImplicitSender
  with FlatSpecLike
  with BeforeAndAfterAll
  with TestDataProvider {

  behavior of classOf[Main].getSimpleName

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  it should "save CheckMK data in InfluxDB" in {
    val testData = validTestData

    val testRunnerActor = system.actorOf(Props(new TestRunnerActor(new InetSocketAddress("127.0.0.1", 2003), testActor)))
    testRunnerActor ! TestRunnerActor.SendCheckMkData(testData.metric, testData.line)

    expectMsg(10.seconds, testData.value)
    expectNoMessage()
  }

  it should "save valid CheckMK data in InfluxDB only" in {
    val testData1 = validTestData
    val testData2 = validTestData

    val testRunnerActor = system.actorOf(Props(new TestRunnerActor(new InetSocketAddress("127.0.0.1", 2003), testActor)))
    testRunnerActor ! TestRunnerActor.SendCheckMkData(testData1.metric, testData1.line)
    testRunnerActor ! TestRunnerActor.SendCheckMkData(UUID.randomUUID().toString, "invalid data")
    testRunnerActor ! TestRunnerActor.SendCheckMkData(testData2.metric, testData2.line)
    testRunnerActor ! TestRunnerActor.SendCheckMkData(UUID.randomUUID().toString, "invalid data")

    expectMsgAllOf(10.seconds, testData1.value, testData2.value)
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