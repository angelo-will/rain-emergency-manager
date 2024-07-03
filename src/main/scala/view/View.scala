package view

import scala.collection.mutable
import scala.swing.{FlowPanel, *}

object FireStationGUI:
  enum ZoneStateGUI:
    case Alarm
    case Managing
    case Ok

  enum FireStationStateGUI:
    case Busy
    case Free

  def apply(fireStationCode: String, allFireStationsCode: Seq[String]) = new FireStationGUI(fireStationCode, allFireStationsCode)

case class FireStationStateComponent(fsCode: String) extends BoxPanel(Orientation.Vertical):

  import FireStationGUI.ZoneStateGUI
  import FireStationGUI.FireStationStateGUI

  private val sensors = new Label("0")
  private val zoneState = new Label("Non Connessa")
  private val fireStationState = new Label("Non Connessa")
  private val zoneControlledCode = new Label("Non Connessa")
  private val button = new Button("")
  button.enabled = false

  contents += new Label(s"CASERMA ${fsCode.trim.substring(12)}")
  contents += new Label("Zona controllata:")
  contents += zoneControlledCode
  contents += new Label("Sensori:")
  contents += sensors
  contents += new Label("Stato zona")
  contents += zoneState
  contents += new Label("Stato caserma")
  contents += fireStationState
  contents += button
  border = Swing.EmptyBorder(10, 10, 10, 10)

  def setZoneState(zoneState: ZoneStateGUI): Unit =
    zoneState match
      case ZoneStateGUI.Alarm => setZoneState("In allarme")
      case ZoneStateGUI.Managing => setZoneState("In gestione")
      case ZoneStateGUI.Ok => setZoneState("Sicura")

  def setFState(fireStationState: FireStationStateGUI): Unit =
    fireStationState match
      case FireStationStateGUI.Free => setFireStationState("Libera")
      case FireStationStateGUI.Busy => setFireStationState("Occupata")

  def setControlledZone(zoneCode: String): Unit =
    zoneControlledCode.text = zoneCode

  def resetLabels(): Unit =
    setControlledZone("Non connessa")
    setPluvQuantity(0)
    setZoneState("Non connessa")
    setFState(FireStationStateGUI.Free)
    setButton(false)

  def setPluvQuantity(quantity: Int): Unit = sensors.text = "" + quantity

  def setButtonAction(listener: Action): Unit =
    button.action = listener
    setButton(true)

  def setButton(active: Boolean): Unit =
    button.enabled = active

  private def setZoneState(state: String): Unit = zoneState.text = state

  private def setFireStationState(state: String): Unit = fireStationState.text = state

end FireStationStateComponent

case class FireStationGUI(fireStationCode: String, allFireStationsCode: Seq[String]) extends SimpleSwingApplication {

  import FireStationGUI.{ZoneStateGUI, FireStationStateGUI}

  // Create a container to hold multiple frames
  private val framesContainer = new BoxPanel(Orientation.Vertical)
  private val panels: mutable.Seq[Component] = mutable.Seq()
  val fireStations: mutable.Map[String, FireStationStateComponent] = mutable.Map()

  private val mainPanel = new FlowPanel() {
    contents += new Label("Stato delle caserma")
    for fsCode <- allFireStationsCode do
      println("Add new FS panel")
      val fireSGui = FireStationStateComponent(fsCode)
      if fsCode.equals(fireStationCode) then fireSGui.setButton(true)
      fireStations(fsCode) = fireSGui
      contents += fireSGui
      print(s"Add FS $fsCode: $fireSGui\nNow firestations is: $fireStations\n\n\n")
    this.revalidate()
    this.repaint()
  }

  def setButtonAction(listener: Action, fsCode: String): Unit =
    if fsCode.equals(fireStationCode) then
      val fsComponent = fireStations(fsCode)
      println(s"Add listener to FSComponent ${fsComponent.fsCode}")
      fsComponent.setButtonAction(listener)
      fireStations(fsCode).setButton(true)

  def disableFireStation(fsCode: String): Unit =
    fireStations(fsCode).resetLabels()

  def setFSZControlled(fsCode: String, zoneCode: String): Unit =
    fireStations(fsCode).setControlledZone(zoneCode)

  def setFSZState(fSCode: String, zoneStateGUI: ZoneStateGUI): Unit =
    fireStations(fSCode).setZoneState(zoneStateGUI)

  def setFSState(fSCode: String, fireStationStateGUI: FireStationStateGUI): Unit =
    fireStations(fSCode).setFState(fireStationStateGUI)

  def setPluvZoneQuantity(fSCode: String, quantity: Int): Unit =
    fireStations(fSCode).setPluvQuantity(quantity)

  def top: Frame = new MainFrame {
    title = s"Visualizzazione zone - Caserma ${fireStationCode.trim.substring(12)}"
    contents = mainPanel
  }

}
