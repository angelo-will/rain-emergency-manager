package systemelements

object SystemElements:
  enum PluviometerState:
    case Alarm
    case NotAlarm

  enum ZoneState:
    case Ok
    case Alarm
    case InManaging

  enum FireStationState:
    case Free
    case Busy

  case class Position(coordX: Int, coordY: Int)

  case class Pluviometer(pluvCode: String, zoneCode: String, position: Position, pluvState: PluviometerState, waterLevel: Double)

  case class Zone(zoneCode: String, zoneState: ZoneState, pluviometers: Seq[Pluviometer], width: Int, height: Int)

  case class FireStation(fireStationCode: String, fireStationState: FireStationState, zone: Zone)
