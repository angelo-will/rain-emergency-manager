package systemelements

object SystemElements:

  enum ZoneState:
    case Ok
    case Alarm
    case InManaging

  enum FireStationState:
    case Free
    case Busy

  class PluviometerState(state : String) extends Serializable:
    override def toString: String = s"PluviometerState: $state"

  case class PluviometerAlarm() extends PluviometerState("ALARM")

  case class PluviometerNotAlarm() extends PluviometerState("NOT-ALARM")

  case class Position(coordX: Int, coordY: Int)

  case class Pluviometer(
                          pluvCode: String,
                          zoneCode: String,
                          position: Position,
                          waterLevel: Int,
                          pluviometerState: PluviometerState
                        ) extends Serializable

  case class Zone(
                   zoneCode: String,
                   zoneState: ZoneState,
                   pluviometers: Map[String, Pluviometer],
                   maxPluviometersPerZone: Int,
                   maxWaterLevel: Int,
                   row: Int,
                   col: Int,
                   width: Int,
                   height: Int
                 ) extends Serializable

  case class FireStation(
                          fireStationCode: String,
                          fireStationState:
                          FireStationState,
                          zone: Zone
                        ) extends Serializable
