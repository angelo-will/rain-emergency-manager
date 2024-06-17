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

  def apply(fsList: Seq[String]) = new FireStationGUI(fsList)

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

  def setFState(fireStationState: FireStationStateGUI): Unit =
    fireStationState match
      case FireStationStateGUI.Free => setFireStationState("Libera")
      case FireStationStateGUI.Busy => setFireStationState("Occupata")
      
  def setPluvQuantity(quantity: Int): Unit = sensors.text = ""+quantity

  private def setZoneState(state: String): Unit = zoneState.text = state

  private def setFireStationState(state: String): Unit = zoneState.text = state

end FireStationStateComponent

case class FireStationGUI(fireStationsCodes: Seq[String]) extends SimpleSwingApplication {

  import FireStationGUI.{ZoneStateGUI, FireStationStateGUI}


  // Create a container to hold multiple frames
  private val framesContainer = new BoxPanel(Orientation.Vertical)
  private val panels: mutable.Seq[Component] = mutable.Seq()
  private val fireStations: mutable.Map[String, FireStationStateComponent] = mutable.Map()
  private val mainPanel = new BoxPanel(Orientation.Vertical) {
    contents += new Label("Stato delle caserma")
    for fs <- fireStationsCodes do
      println("Add new FS panel")
      val fireS = FireStationStateComponent()
      fireStations(s"firestation-$index") = fireS
      contents += fireS
    this.revalidate()
    this.repaint()
  }
  
  def setFSZState(fSCode: String, zoneStateGUI: ZoneStateGUI): Unit =
    fireStations(fSCode).setZoneState(zoneStateGUI)
  
  def setFSState(fSCode: String, fireStationStateGUI: FireStationStateGUI): Unit =
    fireStations(fSCode).setFState(fireStationStateGUI)
    
  def setPluvZoneQuantity(fSCode: String, quantity: Int): Unit =
    fireStations(fSCode).setPluvQuantity(quantity)  
  
  var index = 0

  def top: Frame = new MainFrame {
    title = "Visualizzazione zona e caserma"
    contents = mainPanel
//    size = new Dimension(300, 200)
  }

}

@main def aaa() =
  FireStationGUI(Seq("fs-01","fs-02")).main(Array())


