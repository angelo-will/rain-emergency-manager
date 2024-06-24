package systemelements

object SystemElements:

  enum ZoneState:
    case Ok
    case Alarm
    case InManaging

  enum FireStationState:
    case Free
    case Busy

  class PluviometerState extends Serializable

  case class PluviometerAlarm() extends PluviometerState

  case class PluviometerNotAlarm() extends PluviometerState

  case class Position(coordX: Int, coordY: Int)

  case class Pluviometer(pluvCode: String, zoneCode: String, position: Position, waterLevel: Double, pluviometerState: PluviometerState) extends Serializable

  case class Zone(zoneCode: String, zoneState: ZoneState, pluviometers: Seq[Pluviometer], row: Int, col: Int, width: Int, height: Int) extends Serializable

  case class FireStation(fireStationCode: String, fireStationState: FireStationState, zone: Zone)
