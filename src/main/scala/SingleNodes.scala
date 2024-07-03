import actors.Deploy
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.Behaviors
import actors.message.Message
import utils.startup
import systemelements.SystemElements.*
import actors.Deploy.*

private val fireStationCode1: String = "firestation-1"
private val fireStationCode2: String = "firestation-2"
private val fireStationCode3: String = "firestation-3"
private val fireStationCode4: String = "firestation-4"
private val allFireStationCodes: Seq[String] = Seq(fireStationCode1, fireStationCode2, fireStationCode3, fireStationCode4)
private val zoneCode1: String = "zone-1"
private val zoneCode2: String = "zone-2"
private val zoneCode3: String = "zone-3"
private val zoneCode4: String = "zone-4"
private val topicName: String = "GUIChannel"

/// seeds always up
@main def deploySeeds():Unit = utils.seeds.foreach(port => startup(port = port)(Behaviors.empty))

/// for zone 01
@main def singleDeployZone01(): Unit = simpleZoneStartup(9081, zoneCode1, 0, 0)

@main def singleDeployFireStation01(): Unit = simpleFireStationStartup(9082, fireStationCode1, zoneCode1)

@main def singleDeploySensor01InZone01(): Unit = simplePluviometerStartup(9083, "esp-001", zoneCode1)

@main def singleDeploySensor02InZone01(): Unit = simplePluviometerStartup(9084, "esp-002", zoneCode1)

@main def singleDeploySensor03InZone01(): Unit = simplePluviometerStartup(9085, "esp-003", zoneCode1)

@main def singleDeploySensor04InZone01(): Unit = simplePluviometerStartup(9086, "esp-004", zoneCode1)

@main def singleDeployViewFireStation01(): Unit = simpleViewStartup(9087, fireStationCode1)

/// for zone 02
@main def singleDeployZone02(): Unit = simpleZoneStartup(9088, zoneCode2, 0, 1)

@main def singleDeployFireStation02(): Unit = simpleFireStationStartup(9089, fireStationCode2, zoneCode2)

@main def singleDeploySensor01InZone02(): Unit = simplePluviometerStartup(9090, "esp-005", zoneCode2)

@main def singleDeploySensor02InZone02(): Unit = simplePluviometerStartup(9091, "esp-006", zoneCode2)

@main def singleDeploySensor03InZone02(): Unit = simplePluviometerStartup(9092, "esp-007", zoneCode2)

@main def singleDeploySensor04InZone02(): Unit = simplePluviometerStartup(9093, "esp-008", zoneCode2)

@main def singleDeployViewFireStation02(): Unit = simpleViewStartup(9094, fireStationCode2)

/// for zone 03
@main def singleDeployZone03(): Unit = simpleZoneStartup(9095, zoneCode3, 1, 0)

@main def singleDeployFireStation03(): Unit = simpleFireStationStartup(9096, fireStationCode3, zoneCode3)

@main def singleDeploySensor01InZone03(): Unit = simplePluviometerStartup(9097, "esp-009", zoneCode3)

@main def singleDeploySensor02InZone03(): Unit = simplePluviometerStartup(9098, "esp-010", zoneCode3)

@main def singleDeploySensor03InZone03(): Unit = simplePluviometerStartup(9099, "esp-011", zoneCode3)

@main def singleDeploySensor04InZone03(): Unit = simplePluviometerStartup(9100, "esp-012", zoneCode3)

@main def singleDeployViewFireStation03(): Unit = simpleViewStartup(9101, fireStationCode3)

/// for zone 04
@main def singleDeployZone04(): Unit = simpleZoneStartup(9102, zoneCode4, 1, 1)

@main def singleDeployFireStation04(): Unit = simpleFireStationStartup(9103, fireStationCode4, zoneCode4)

@main def singleDeploySensor01InZone04(): Unit = simplePluviometerStartup(9104, "esp-013", zoneCode4)

@main def singleDeploySensor02InZone04(): Unit = simplePluviometerStartup(9105, "esp-014", zoneCode4)

@main def singleDeploySensor03InZone04(): Unit = simplePluviometerStartup(9106, "esp-015", zoneCode4)

@main def singleDeploySensor04InZone04(): Unit = simplePluviometerStartup(9107, "esp-016", zoneCode4)

@main def singleDeployViewFireStation04(): Unit = simpleViewStartup(9108, fireStationCode4)

def simplePluviometerStartup(port: Int, pluvCode: String, zoneCode: String): ActorSystem[Message] =
  startup(port = port)(Deploy.pluviometer(
    Pluviometer(
      pluvCode = pluvCode,
      zoneCode = zoneCode,
      Position(0, 0),
      waterLevel = 0,
      PluviometerNotAlarm()
    ), s"actor-pluviometer-$pluvCode"))

def simpleZoneStartup(port: Int, zoneCode: String, row: Int, col: Int): ActorSystem[Message] =
  startup(port = port)(Deploy.zone(
    Zone(
      zoneCode,
      ZoneOk(),
      pluviometers = Map(),
      maxPluviometersPerZone = 3,
      maxWaterLevel = 350,
      row = row,
      col = col,
      width = 200,
      height = 200
    ), s"actor-$zoneCode"))

def simpleFireStationStartup(port: Int, fireStationCode: String, zoneCode: String): ActorSystem[Message] =
  startup(port = port)(Deploy.fireStation(zoneCode, fireStationCode, topicName))

def simpleViewStartup(port: Int, fireStationCode: String): ActorSystem[Message] =
  startup(port = port)(Deploy.view(fireStationCode, allFireStationCodes, topicName))
