import actors.Deploy
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.Behaviors
import message.Message
import utils.startup
import systemelements.SystemElements.*

import actors.Deploy.*

private val fireStationCode1: String = "firestation-1"
private val zoneCode1: String = "zone-1"
private val zoneCode2: String = "zone-2"
private val topicName: String = "GUIChannel"

@main def singleDeployZone01(): Unit =
  val zoneCode = zoneCode1
  startup(port = 2552)(Deploy.zone(
    Zone(
      zoneCode,
      ZoneOk(),
      pluviometers = Map(),
      maxPluviometersPerZone = 3,
      maxWaterLevel = 350,
      row = 0,
      col = 0,
      width = 200,
      height = 200
    ), s"actor-$zoneCode"))

@main def singleDeployZone02(): Unit =
  val zoneCode = zoneCode2
  startup(port = 2551)(Deploy.zone(
    Zone(
      zoneCode,
      ZoneOk(),
      pluviometers = Map(),
      maxPluviometersPerZone = 3,
      maxWaterLevel = 350,
      row = 0,
      col = 0,
      width = 200,
      height = 200
    ), s"actor-$zoneCode"))

// Single start
@main def singleDeployFireStation01(): Unit =
  startup(port = 8090)(Deploy.fireStation(zoneCode1, fireStationCode1, topicName))

@main def singleDeploySensor01(): Unit =
  val pluvCode = "esp-001"
  //  val zoneCode = "zone-0-0"
  val zoneCode = zoneCode1
  startup(port = 8081)(Deploy.pluviometer(
    Pluviometer(
      pluvCode = pluvCode,
      zoneCode1,
      Position(0, 0),
      waterLevel = 0,
      PluviometerNotAlarm()
    ), s"actor-pluviometer-$pluvCode"))

@main def singleDeploySensor02(): Unit =
  val pluvCode = "esp-002"
  startup(port = 8082)(Deploy.pluviometer(
    Pluviometer(
      pluvCode = pluvCode,
      zoneCode = zoneCode1,
      Position(0, 0),
      waterLevel = 0,
      PluviometerNotAlarm()
    ), s"actor-pluviometer-$pluvCode"))

@main def singleDeploySensor03(): Unit =
  val pluvCode = "esp-003"
  startup(port = 8083)(Deploy.pluviometer(
    Pluviometer(
      pluvCode = pluvCode,
      zoneCode = zoneCode1,
      Position(0, 0),
      waterLevel = 0,
      PluviometerNotAlarm()
    ), s"actor-pluviometer-$pluvCode"))

@main def singleDeploySensor04(): Unit =
  val pluvCode = "esp-004"
  startup(port = 8084)(Deploy.pluviometer(
    Pluviometer(
      pluvCode = pluvCode,
      zoneCode = zoneCode1,
      Position(0, 0),
      waterLevel = 0,
      PluviometerNotAlarm()
    ), s"actor-pluviometer-$pluvCode"))

// Deploy view
@main def deployView(): Unit =
  //  val codes = Seq("fs-01", "fs-02", "fs-03", "fs-04")
  val codes = Seq(fireStationCode1)
  startup(port = 8004)(Deploy.view(fireStationCode1, codes, topicName))