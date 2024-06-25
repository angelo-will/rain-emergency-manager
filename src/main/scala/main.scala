import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import message.Message
import utils.{seeds, startup}
import pluviometer.PluviometerActor
import firestastion.FireStationActor
import firestastion.FireStationActor.{FireStationStatus, Managing, Solved}
import zone.ZoneActor
import systemelements.SystemElements.*
//import systemelements.SystemElements.{PluviometerAlarm, PluviometerNotAlarm}
import view.ViewActor

import scala.concurrent.duration.{DAYS, FiniteDuration}
import scala.util.Random

object Deploy:
  def zone(zoneCode: String, zoneName: String, row: Int, column: Int): Behavior[Message] =
    deploy(ZoneActor(Zone(zoneCode, ZoneState.Ok, Seq(), row, column, 100, 100)), s"actor-$zoneCode")

  def pluviometer(zoneCode: String, pluviometerName: String, coordX: Int, coordY: Int): Behavior[Message] =
    deploy(PluviometerActor(Pluviometer(pluviometerName, zoneCode, Position(coordX, coordY), 0, PluviometerNotAlarm())), s"actor-$pluviometerName")

  def fireStation(zoneCode: String, fireStationName: String, PubSubChannelName: String): Behavior[Message] =
    deploy(FireStationActor(fireStationName, fireStationName, zoneCode, PubSubChannelName), s"actor-$fireStationName")

  def view(fsCodes: Seq[String]): Behavior[Message] =
    deploy(ViewActor(fsCodes), "actor-view")

  private def deploy(behavior: Behavior[Message], actorName: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(behavior, actorName)
    Behaviors.empty
  }



// Single start

@main def singleDeployZone01(): Unit =
  startup(port = 2551)(Deploy.zone("zone-01", "zone-01", 1, 1))

@main def singleDeployFireStation01(): Unit =
  startup(port = 8090)(Deploy.fireStation("zone-01", "firestation-01", "GUIChannel"))

@main def singleDeploySensor01(): Unit =
  startup(port = 8080)(Deploy.pluviometer("zone-01", "esp32-001", 1, 1))

@main def singleDeploySensor02(): Unit =
  startup(port = 8081)(Deploy.pluviometer("zone-01", "esp32-002", 1, 2))

@main def singleDeploySensor03(): Unit =
  startup(port = 8082)(Deploy.pluviometer("zone-01", "esp32-003", 1, 3))

@main def testCode: Unit =
  val city = Main.City(100, 200, 2, 2)
  val pluvPerZone = 3
  var index = 0


  val zones = for
    x <- 0 until city.rows
    y <- 0 until city.columns
  yield
    println(s"creating zone-$x-$y with index")
    index += 1
    Main.Zone(s"zone-$x-$y", index, x, y, city.width / city.columns, city.height / city.rows)

  for
    zone <- zones
  yield
    println(s"startup(port = ${8080 + zone.index})(ZoneDeploy(${zone.zoneCode}, ${zone.zoneCode}, ${zone.row}, ${zone.column}))")

  // Deploy pluvs
  for
    zone <- zones
    pluv <- 1 to pluvPerZone
  yield
    val coordX = Random.between(zone.width * zone.column, zone.width * (zone.column + 1)).toInt
    val coordY = Random.between(zone.height * zone.row, zone.height * (zone.row + 1)).toInt
    //    startup(port = 8180 + pluv)(PluviometerDeploy(zone.zoneCode, s"pluv-$pluv-of-${zone.zoneCode}",coordX, coordY))
    println(s"startup(port = ${8180 + (zone.index * 10) + pluv})(PluviometerDeploy(${zone.zoneCode}, pluv-$pluv-of-${zone.zoneCode},$coordX, $coordY))")

  index = 0
  for
    zone <- zones
  yield
    index += 1
    println(s"startup(port = ${9000 + index})(FireStationDeploy(${zone.zoneCode}, firestation-$index))")


object TestFirestation:

  sealed trait Command extends Message

  def apply() = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)

    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("GUIChannel")

    topic ! Topic.Subscribe(ctx.self)

    Behaviors.receiveMessagePartial {
      case FireStationStatus(firestation) =>
        firestation.zone.zoneState match
          case ZoneState.Ok => ctx.log.info("Firestation says everything is ok"); Behaviors.same
          case ZoneState.Alarm => 
            ctx.log.info("Firestation says everything there's an alarm") 
            topic ! Topic.publish(Managing("firestation-01"))
            Behaviors.same
          case ZoneState.InManaging => 
            ctx.log.info("Firestation says it's managing the alarm")
            topic ! Topic.publish(Solved("firestation-01"))
            Behaviors.same
    }
  }

@main def testFirestation: Unit =
  val ref = startup(port = 1503)(TestFirestation.apply())

object Main extends App:

  case class City(width: Double, height: Double, columns: Int, rows: Int)

  case class Zone(zoneCode: String, index: Int, row: Int, column: Int, width: Double, height: Double)

  val city = City(100, 200, 3, 2)
  var index = 0

  val zones = for
    x <- 1 to city.rows
    y <- 1 to city.columns
  yield
    println(s"creating zone-$x-$y with index")
    index += 1
    Zone(s"zone-$x-$y", index, x, y, city.width / city.columns, city.height / city.rows)

  for
    zone <- zones
  yield
    startup(port = 8080 + zone.index)(Deploy.zone(zone.zoneCode, zone.zoneCode, zone.row, zone.column))


  @main def startFireStation01(): Unit =
    startup(port = 8090)(Deploy.fireStation("zone-01", "firestation-01", "GUIChannel"))

  @main def startZone01(): Unit =
    startup(port = 2551)(Deploy.zone("zone-01", "zone-01", 1, 1))
  //  startup(port = seeds.head)(ZoneDeploy("zone-01", "zone-01"))

  //@main def startZone02(): Unit =

  @main def deploySensor01(): Unit =
    startup(port = 8100)(Deploy.pluviometer("zone-01", "esp32-001", 1, 1))

  @main def deploySensor02(): Unit =
    startup(port = 8101)(Deploy.pluviometer("zone-01", "esp32-002", 1, 2))

  @main def deploySensor03(): Unit =
    startup(port = 8102)(Deploy.pluviometer("zone-01", "esp32-003", 1, 3))


import akka.actor.typed.pubsub.Topic
import akka.actor.typed.pubsub.PubSub
import akka.actor.typed.ActorRef

object TestPub:
  sealed trait Command extends Message

  case class AAA() extends Command

  case class BBB(b: String) extends Command

  def apply(str: String) = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)

    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("my-topic")

    topic ! Topic.Subscribe(ctx.self)

    Behaviors.withTimers { timers =>

      timers.startTimerAtFixedRate(AAA(), FiniteDuration(5, "second"))

      Behaviors.receivePartial {
        case (ctx2, AAA()) =>
          ctx2.log.info("Received AAA")
          topic ! Topic.publish(BBB(s"bella raga invio $str"))
          Behaviors.same
        case (ctx2, BBB(s)) =>
          ctx2.log.info(s"received BBB with $s")
          Behaviors.same
      }
    }
  }

//object TestSub:
//
//  def apply() = Behaviors.setup { ctx =>
//    val pubSub = PubSub(ctx.system)
//    Topic.Subscribe()
//
//    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("my-topic")
//    Behaviors.empty
//  }

@main def testPubSub(): Unit =
  startup(port = 2551)(TestPub("1111"))

@main def testPubSub2(): Unit =
  startup(port = 2552)(TestPub("22222"))
//  startup(port = 8084)(TestSub())

// Deploy view
@main def deployView(): Unit =
  val codes = Seq("fs-01", "fs-02", "fs-03", "fs-04")
  startup(port = 8004)(Deploy.view(codes))