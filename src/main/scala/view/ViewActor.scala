package view


import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import message.Message
import systemelements.SystemElements.ZoneState

import javax.swing.SwingUtilities
import scala.concurrent.{ExecutionContext, Future}
import scala.swing.Action

object ViewActor:
  sealed trait Command extends Message

  def apply(fsCodes: Seq[String]): Unit =
    val gui = FireStationGUI(fsCodes)
    for fs <- fsCodes do
      gui.addButtonListener(ViewListenerActor(fs), fs)
    val viewActor = new ViewActor(fsCodes, gui).start()
    gui.main(Array.empty)



case class ViewActor(fsCodes: Seq[String], gui: FireStationGUI):

  import akka.actor.typed.pubsub.{Topic, PubSub}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.{ActorRef, Behavior}

  import scala.collection.mutable

  import firestastion.FireStation.{FireStationState, MessageToActorView}

  import view.FireStationGUI.{FireStationStateGUI, ZoneStateGUI}

  val AAA = 0
  private val fireStations = mutable.Map[String, ActorRef[Message]]()

  def start(): Behavior[Message] = Behaviors.setup { ctx =>
//    ctx.spawn(listenerFireStation(ctx.self), s"listener-fs")
    debugBehav()
  }

  def listenerFireStation(forwardActor: ActorRef[Message]) = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("firestations-topic")
    topic ! Topic.subscribe(ctx.self)
    Behaviors.receiveMessagePartial {
      case MessageToActorView(idFS, zoneState, fireStationState, refForReply) =>
        updateFSState(idFS, fireStationState)
        updateFSZState(idFS, zoneState)
        Behaviors.same
    }
  }

  def debugBehav(): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, _) => Behaviors.same
    }

  def updateFSState(fsCode: String, fireStationState: FireStationState): Unit = fireStationState match
    case FireStationState.Free => gui.setFSState(fsCode, FireStationStateGUI.Free)
    case FireStationState.Busy => gui.setFSState(fsCode, FireStationStateGUI.Busy)

  def updateFSZState(str: String, state: Option[ZoneState]): Unit =
    if state.isDefined then state.get match
      case ZoneState.Alarm => gui.setFSZState(str, ZoneStateGUI.Alarm)
      case ZoneState.InManaging => gui.setFSZState(str, ZoneStateGUI.Managing)
      case ZoneState.Ok => gui.setFSZState(str, ZoneStateGUI.Ok)
    else
      gui.setFSZState(str, ZoneStateGUI.NotConnected)


@main def testActorGUI(): Unit =
  val codes = Seq("fs-01", "fs-02", "fs-03", "fs-04")
  ViewActor(codes)