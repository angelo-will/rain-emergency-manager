package view

import actors.view.ViewListenerActor

import scala.collection.mutable
import scala.swing.*
import scala.swing.event.ButtonClicked

object FireStationGUI:
  enum ZoneStateGUI:
    case Alarm
    case Managing
    case Ok
    case NotConnected

  enum FireStationStateGUI:
    case Busy
    case Free

  def apply(fsList: Seq[String]) = new FireStationGUI(fsList)

case class FireStationStateComponent(fsCode: String) extends BoxPanel(Orientation.Vertical):

  import FireStationGUI.ZoneStateGUI
  import FireStationGUI.FireStationStateGUI

  private val sensors = new Label("0")
  private val zoneState = new Label("In allarme")
  private val fireStationState = new Label("Libera")
  private val zoneControlledCode = new Label("non connessa")
  private val button = new Button("")

  contents += new Label(s"CASERMA $fsCode")
  contents += new Label("Zona controllata:")
  contents += zoneControlledCode
  contents += new Label("Sensori:")
  contents += sensors
  contents += new Label("Stato zona")
  contents += zoneState
  contents += new Label("Stato caserma")
  contents += fireStationState
  contents += button

  def setZoneState(zoneState: ZoneStateGUI): Unit =
    zoneState match
      case ZoneStateGUI.Alarm => setZoneState("In allarme")
      case ZoneStateGUI.Managing => setZoneState("In gestione")
      case ZoneStateGUI.Ok => setZoneState("Sicura")
      case ZoneStateGUI.NotConnected => setZoneState("Non connessa")

  def setFState(fireStationState: FireStationStateGUI): Unit =
    fireStationState match
      case FireStationStateGUI.Free => setFireStationState("Libera")
      case FireStationStateGUI.Busy => setFireStationState("Occupata")

  def setPluvQuantity(quantity: Int): Unit = sensors.text = "" + quantity

  def setButtonAction(listener: Action): Unit =
    button.action = listener

  private def setZoneState(state: String): Unit = zoneState.text = state

  private def setFireStationState(state: String): Unit = zoneState.text = state

end FireStationStateComponent

case class FireStationGUI(fireStationsCodes: Seq[String]) extends SimpleSwingApplication {

  import FireStationGUI.{ZoneStateGUI, FireStationStateGUI}

  // Create a container to hold multiple frames
  private val framesContainer = new BoxPanel(Orientation.Vertical)
  private val panels: mutable.Seq[Component] = mutable.Seq()
  val fireStations: mutable.Map[String, FireStationStateComponent] = mutable.Map()

  private val mainPanel = new BoxPanel(Orientation.Vertical) {
    contents += new Label("Stato delle caserma")
    for fs <- fireStationsCodes do
      println("Add new FS panel")
      val fireS = FireStationStateComponent(fs)
      fireStations(fs) = fireS
      contents += fireS
      print(s"Add FS $fs: $fireS\nNow firestations is: $fireStations\n\n\n")
    this.revalidate()
    this.repaint()
  }

  def setButtonAction(listener: Action, fsCode: String): Unit =
    val fsComponent = fireStations(fsCode)
    println(s"Add listener to FSComponent ${fsComponent.fsCode}")
    fsComponent.setButtonAction(listener)

  def setFSZState(fSCode: String, zoneStateGUI: ZoneStateGUI): Unit =
    fireStations(fSCode).setZoneState(zoneStateGUI)

  def setFSState(fSCode: String, fireStationStateGUI: FireStationStateGUI): Unit =
    fireStations(fSCode).setFState(fireStationStateGUI)

  def setPluvZoneQuantity(fSCode: String, quantity: Int): Unit =
    fireStations(fSCode).setPluvQuantity(quantity)

  def top: Frame = new MainFrame {
    title = "Visualizzazione zona e caserma"
    contents = mainPanel
    //    size = new Dimension(300, 200)
  }

}

@main def designGUI(): Unit =
  val codes = Seq("fs-01", "fs-02", "fs-03", "fs-04")
  val gui = FireStationGUI(codes)
  for fs <- codes do
    gui.setButtonAction(ViewListenerActor(fs), fs)
  gui.main(Array())

