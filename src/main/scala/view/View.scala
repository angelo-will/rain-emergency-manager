package view

import scala.swing.*

object FireStationGUI:
  enum ZoneStateGUI:
    case Alarm
    case Managing
    case Ok

  enum FireStationStateGUI:
    case Busy
    case Free

  def apply() = new FireStationGUI()


case class FireStationGUI() extends SimpleSwingApplication:

  import FireStationGUI.{ZoneStateGUI, FireStationStateGUI}

  private val sensorsInAlarm = new Label("0/0")
  private val zoneState = new Label("In allarme")
  private val fireStationState = new Label("Libera")

  def top = new MainFrame {
    title = "Visualizzazione zona e caserma"
    contents = new BoxPanel(Orientation.Vertical) {
      contents += new Label("Sensori in allarme:")
      contents += sensorsInAlarm
      contents += new Label("Stato zona")
      contents += zoneState
      contents += new Label("Stato caserma")
      contents += fireStationState
      contents += Button("Bottone") {
        println("Hai premuto il bottone!")
        // Modifica le etichette qui
        sensorsInAlarm.text = "3"
        zoneState.text = "In gestione"
        fireStationState.text = "Occupata"
      }
    }
    size = new Dimension(300, 200)
  }

  def setZoneState(zoneState: ZoneStateGUI): Unit =
    zoneState match
      case ZoneStateGUI.Alarm => setZoneState("In allarme")
      case ZoneStateGUI.Managing => setZoneState("In gestione")
      case ZoneStateGUI.Ok => setZoneState("Sicura")

  def setFireStationState(fireStationState: FireStationStateGUI): Unit =
    fireStationState match
      case FireStationStateGUI.Free => setFireStationState("Libera")
      case FireStationStateGUI.Busy => setFireStationState("Occupata")

  private def setZoneState(state: String): Unit = zoneState.text = state

  private def setFireStationState(state: String): Unit = zoneState.text = state


@main def aaa() =
  FireStationGUI().main(Array())


