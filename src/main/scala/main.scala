import actors.Deploy
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.Behaviors
import actors.message.Message
import utils.startup
import actors.firestastion.FireStationActor.{FireStationStatus, Managing, Solved}
import systemelements.SystemElements.*

import scala.util.Random
import actors.Deploy.*
import actors.firestastion.FireStationActor

import scala.collection.mutable.ArrayBuffer

private var fireStationCode1: String = "firestation-1"
private var zoneCode1: String = "zone-1"
private var zoneCode2: String = "zone-2"
private var topicName: String = "GUIChannel"

object Main extends App:

  case class City(width: Double, height: Double, columns: Int, rows: Int)

  val city = City(100, 200, 1, 2)

  val maxPluviometersPerZone = 2
  val pluvPerZone = 3

  val maxWaterLevel = 200
  var index = 0

  // Create Zones
  val zones = for
    x <- 0 until city.rows
    y <- 0 until city.columns
  yield
    println(s"creating zone-$x-$y with index")
    Zone(
      s"zone-$x-$y",
      ZoneOk(),
      pluviometers = Map(),
      maxPluviometersPerZone,
      maxWaterLevel,
      row = x,
      col = y,
      (city.width / city.columns).toInt,
      (city.height / city.rows).toInt
    )

  utils.seeds.foreach(port => startup(port = port)(Behaviors.empty))

  // Deploy Zone
  for
    zone <- zones
  yield
    index += 1
    startup(port = 10000 + index)(Deploy.zone(zone, s"actor-${zone.zoneCode}"))

  // Deploy Pluviometers
  index = 0
  for
    zone <- zones
    pluv <- 0 to pluvPerZone
  yield
    index += 1
    val coordX = Random.between(zone.width * zone.col, zone.width * (zone.col + 1)).toInt
    val coordY = Random.between(zone.height * zone.row, zone.height * (zone.row + 1)).toInt
    startup(port = 10100 + index)(Deploy.pluviometer(
      Pluviometer(
        pluvCode = s"pluviometer-$index",
        zoneCode = zone.zoneCode,
        Position(0, 0),
        waterLevel = 0,
        PluviometerNotAlarm()
      ), s"actor-pluviometer-$index"))

  val fsCodes = ArrayBuffer.empty[String]

  // Deploy Firestations
  index = 0
  for
    zone <- zones
  yield
    index += 1
    val fsCode = s"firestation-$index"
    fsCodes += fsCode
    startup(port = 10200 + index)(Deploy.fireStation(zone.zoneCode, fsCode, topicName))

  index = 0
  for
    fsCode <- fsCodes
  yield
    //Deploy view
    index += 1
    startup(port = 10300 + index)(Deploy.view(fsCode, fsCodes.toSeq, topicName))
    startup(port = 10400 + index)(Deploy.view(fsCode, fsCodes.toSeq, topicName))



