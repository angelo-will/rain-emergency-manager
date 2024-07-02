import actors.Deploy
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.Behaviors
import message.Message
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

object TestFirestation:

  sealed trait Command extends Message

  def apply() = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)

    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("GUIChannel")

    topic ! Topic.Subscribe(ctx.self)

    Behaviors.receiveMessagePartial {
      case FireStationStatus(firestation) =>
        firestation.zone.zoneState match
          case ZoneOk() => ctx.log.info("Firestation says everything is ok"); Behaviors.same
          case ZoneAlarm() =>
            ctx.log.info("Firestation says everything there's an alarm")
            topic ! Topic.publish(Managing("firestation-01"))
            Behaviors.same
          case ZoneInManaging() =>
            ctx.log.info("Firestation says it's managing the alarm")
            topic ! Topic.publish(Solved("firestation-01"))
            Behaviors.same
    }
  }

object Main extends App:

  case class City(width: Double, height: Double, columns: Int, rows: Int)

  val city = City(100, 200, 2, 2)

  val maxPluviometersPerZone = 3
  val pluvPerZone = 2

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

  // Deploy Zone
  for
    zone <- zones
  yield
    index += 1
    startup(port = 9999 + index)(Deploy.zone(zone, s"actor-${zone.zoneCode}"))

  // Deploy Pluviometers
  index = 0
  for
    zone <- zones
    pluv <- 0 to pluvPerZone
  yield
    index += 1
    val coordX = Random.between(zone.width * zone.col, zone.width * (zone.col + 1)).toInt
    val coordY = Random.between(zone.height * zone.row, zone.height * (zone.row + 1)).toInt
    startup(port = 10099 + index)(Deploy.pluviometer(
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
    startup(port = 10199 + index)(Deploy.fireStation(zone.zoneCode, fsCode, topicName))
  
  for 
    fsCode <- fsCodes
  yield
    //Deploy view
    startup(port = 10300)(Deploy.view(fsCode, fsCodes.toSeq, topicName))
    startup(port = 10301)(Deploy.view(fsCode, fsCodes.toSeq, topicName))


