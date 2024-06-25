package view


import akka.actor.typed.Behavior
import firestastion.FireStationActor.FireStationStatus
import message.Message
import systemelements.SystemElements.{FireStation, ZoneState}
import view.ViewListenerActor.ActionCommand.{END_INTERVENTION, INTERVENE, WAITING}

object ViewActor:
  sealed trait Command extends Message

  def apply(fsCodes: Seq[String]): Behavior[Message] =
    new ViewActor(fsCodes).start()


case class ViewActor(fsCodes: Seq[String]):

  import akka.actor.typed.pubsub.{Topic, PubSub}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.{ActorRef, Behavior}

  import scala.collection.mutable
  
  import systemelements.SystemElements.FireStationState
  
  import firestastion.FireStationActor

  import view.FireStationGUI.{FireStationStateGUI, ZoneStateGUI}

  private val fireStations = mutable.Map[String, ActorRef[Message]]()
  private val gui = FireStationGUI(fsCodes)
  private var context: ActorContext[Message] = _

  private def start(): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(listenerFireStation(ctx.self), s"listener-fs")
    context = ctx
    for fs <- fsCodes do
      gui.setButtonAction(ViewListenerActor(fs, ctx, INTERVENE), fs)
    gui.main(Array.empty)
    debugBehav()
  }

  private def listenerFireStation(forwardActor: ActorRef[Message]) = Behaviors.setup { ctx =>
    val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message]("firestations-topic")
    topic ! Topic.subscribe(ctx.self)
    Behaviors.receiveMessagePartial {
      case FireStationActor.Managing(fsCode) =>
        println("Received Managing")
        updateFSState(fsCode, FireStationState.Busy)
        updateFSZState(fsCode, ZoneState.InManaging)
        Behaviors.same
      case FireStationActor.Solved(fsCode) =>
        println("Received Solved")
        updateFSState(fsCode, FireStationState.Free)
        updateFSZState(fsCode, ZoneState.Ok)
        Behaviors.same
      case FireStationStatus(FireStation(fsCode, fireState, zoneClass)) =>
        updateFSState(fsCode, fireState)
        zoneClass.zoneState match
          case ZoneState.Alarm =>
            println("Received Alarm")
            updateFSZState(fsCode, zoneClass.zoneState)
          case _ =>updateFSState(fsCode, fireState)
            updateFSZState(fsCode, zoneClass.zoneState)
        Behaviors.same
    }
  }

  private def debugBehav(): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, _) => Behaviors.same
    }

  private def updateFSState(fsCode: String, fireStationState: FireStationState): Unit = fireStationState match
    case FireStationState.Free => gui.setFSState(fsCode, FireStationStateGUI.Free)
    case FireStationState.Busy => gui.setFSState(fsCode, FireStationStateGUI.Busy)

  private def updateFSZState(fsCode: String, zoneState: ZoneState): Unit =
    zoneState match
      case ZoneState.Alarm =>
        gui.setFSZState(fsCode, ZoneStateGUI.Alarm)
        gui.setButtonAction(ViewListenerActor(fsCode, context, INTERVENE), fsCode)
      case ZoneState.InManaging =>
        gui.setFSZState(fsCode, ZoneStateGUI.Managing)
        gui.setButtonAction(ViewListenerActor(fsCode, context, END_INTERVENTION), fsCode)
      case ZoneState.Ok =>
        gui.setFSZState(fsCode, ZoneStateGUI.Ok)
        gui.setButtonAction(ViewListenerActor(fsCode, context, WAITING), fsCode)