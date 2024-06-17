package view

import javax.swing.{JDesktopPane, JInternalFrame}
import scala.collection.mutable
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

case class FireStationStateComponent() extends BoxPanel(Orientation.Vertical):

  import FireStationGUI.ZoneStateGUI
  import FireStationGUI.FireStationStateGUI

  private val sensors = new Label("0")
  private val zoneState = new Label("In allarme")
  private val fireStationState = new Label("Libera")

  contents += new Label("Sensori in allarme:")
  contents += sensors
  contents += new Label("Stato zona")
  contents += zoneState
  contents += new Label("Stato caserma")
  contents += fireStationState
  contents += Button("Bottone") {
    println("Hai premuto il bottone!")
    // Modifica le etichette qui
    sensors.text = "3"
    zoneState.text = "In gestione"
    fireStationState.text = "Occupata"
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

end FireStationStateComponent

case class FireStationGUI() extends SimpleSwingApplication {

  import FireStationGUI.{ZoneStateGUI, FireStationStateGUI}


  // Create a container to hold multiple frames
  private val framesContainer = new BoxPanel(Orientation.Vertical)
  private var panels: mutable.Seq[Component] = mutable.Seq()
  private var fireStations: mutable.Map[String, FireStationStateComponent] = mutable.Map()
  var index = 0

  def top: Frame = new MainFrame {
    title = "Visualizzazione zona e caserma"
    contents = new BoxPanel(Orientation.Vertical) {
      contents += new Label("Stato delle caserma")
      contents += Button("Debug aggiungi caserma") {
        println("Hai premuto il bottone!")
        val fireS = FireStationStateComponent()
        contents += fireS
        fireStations(s"firestation-$index") = fireS
        this.revalidate()
        this.repaint()
      }
    }
    size = new Dimension(300, 200)
  }

}

@main def aaa() =
  FireStationGUI().main(Array())


