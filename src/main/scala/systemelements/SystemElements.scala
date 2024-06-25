package systemelements

object SystemElements:

  class ZoneState(state: String) extends Serializable:
    override def toString: String = s"ZoneState: $state"

  case class ZoneOk() extends ZoneState("OK")
  case class ZoneAlarm() extends ZoneState("Alarm")
  case class ZoneInManaging() extends ZoneState("InManaging")

  class FireStationState(state: String) extends Serializable:
    override def toString: String = s"FireStationState: $state"

  case class FireStationFree() extends FireStationState("Free")
  case class FireStationBusy() extends FireStationState("Alarm")

//  enum ZoneState:
//    case Ok
//    case Alarm
//    case InManaging

//  enum FireStationState:
//    case Free
//    case Busy

  //Case classes for pluviometer, use instead of enum for serialization
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
