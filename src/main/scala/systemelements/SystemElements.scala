package systemelements

/**
 * Contains classes and case classes representing the elements of the system,
 * including zones, fire stations, and pluviometers, along with their states and attributes.
 */
object SystemElements:

  /**
   * Represents the state of a zone.
   *
   * @param state the state of the zone as a string.
   */
  class ZoneState(private val state: String) extends Serializable(state):
    override def toString: String = s"ZoneState: $state"

  /**
   * Represents a zone that is in an safe state.
   */
  case class ZoneOk() extends ZoneState("OK")

  /**
   * Represents a zone that is in an alarm state.
   */
  case class ZoneAlarm() extends ZoneState("Alarm")

  /**
   * Represents a zone that is currently under management.
   */
  case class ZoneInManaging() extends ZoneState("InManaging")

  /**
   * Represents the state of a fire station.
   *
   * @param state the state of the fire station as a string.
   */
  class FireStationState(private val state: String) extends Serializable(state):
    override def toString: String = s"FireStationState: $state"

  /**
   * Represents a fire station that is free.
   */
  case class FireStationFree() extends FireStationState("Free")

  /**
   * Represents a fire station that is busy due to managing a zone in alarm.
   */
  case class FireStationBusy() extends FireStationState("Alarm")

  /**
   * Represents the state of a pluviometer.
   *
   * @param state the state of the pluviometer as a string.
   */
  class PluviometerState(state: String) extends Serializable(state):
    override def toString: String = s"PluviometerState: $state"

  /**
   * Represents a pluviometer that is in an alarm state.
   */
  case class PluviometerAlarm() extends PluviometerState("ALARM")

  /**
   * Represents a pluviometer that is not in an alarm state.
   */
  case class PluviometerNotAlarm() extends PluviometerState("NOT-ALARM")

  /**
   * Represents the position of an element with x and y coordinates.
   *
   * @param coordX the x coordinate.
   * @param coordY the y coordinate.
   */
  case class Position(coordX: Int, coordY: Int)

  /**
   * Represents a pluviometer with its attributes.
   *
   * @param pluvCode the code identifying the pluviometer.
   * @param zoneCode the code of the zone where the pluviometer is located.
   * @param position the position of the pluviometer.
   * @param waterLevel the current water level measured by the pluviometer.
   * @param pluviometerState the state of the pluviometer.
   */
  case class Pluviometer(
                          pluvCode: String,
                          zoneCode: String,
                          position: Position,
                          waterLevel: Int,
                          pluviometerState: PluviometerState
                        ) extends Serializable(pluviometerState.code)

  /**
   * Represents a zone with its attributes.
   *
   * @param zoneCode the code identifying the zone.
   * @param zoneState the current state of the zone.
   * @param pluviometers a map of pluviometers in the zone, identified by their codes.
   * @param maxPluviometersPerZone the maximum number of pluviometers allowed in the zone.
   * @param maxWaterLevel the maximum water level before the zone goes into alarm.
   * @param row the row coordinate of the zone in a grid.
   * @param col the column coordinate of the zone in a grid.
   * @param width the width of the zone.
   * @param height the height of the zone.
   */
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
                 ) extends Serializable(zoneState.code)

  /**
   * Represents a fire station with its attributes.
   *
   * @param fireStationCode the code identifying the fire station.
   * @param fireStationState the current state of the fire station.
   * @param zone the zone that the fire station is responsible for.
   */
  case class FireStation(
                          fireStationCode: String,
                          fireStationState: FireStationState,
                          zone: Zone
                        ) extends Serializable(fireStationState.code)
