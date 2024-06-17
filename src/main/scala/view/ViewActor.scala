package view


import message.Message

object ViewActor:
  sealed trait Command extends Message

  def apply(fsCodes: Seq[String]) =
    val gui = FireStationGUI(fsCodes)
    gui.main(Array())
    new ViewActor(fsCodes, gui).start()

case class ViewActor(fsCodes: Seq[String],gui: FireStationGUI):

  import akka.actor.typed.pubsub.{Topic, PubSub}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.{ActorRef, Behavior}

  import scala.collection.mutable

  import firestastion.FireStation.{FireStationState, MessageToActorView}
  import zone.Zone.ZoneState

  import view.FireStationGUI
  import view.FireStationGUI.{FireStationStateGUI, ZoneStateGUI}

  val AAA = 0
  var fireStations = mutable.Map[String, ActorRef[Message]]()

  def start(): Behavior[Message] = Behaviors.setup { ctx =>

//    for fs <- fsCodes do
    ctx.spawn(listenerFireStation(ctx.self), s"listener-fs" )
    debugBehav()
  }

  def listenerFireStation(forwardActor: ActorRef[Message]) = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("firestations-topic")
    topic ! Topic.subscribe(ctx.self)
    Behaviors.receiveMessagePartial {
      case MessageToActorView(idFS,zoneState,fireStationState,refForReply) =>
        updateFSState(idFS, fireStationState)
        updateFSZState(idFS, zoneState)
        Behaviors.same
    }
  }

  def debugBehav(): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, _) => Behaviors.same
    }
  def updateFSState(fsCode:String, fireStationState: FireStationState): Unit = fireStationState match
    case FireStationState.Free => gui.setFSState(fsCode, FireStationStateGUI.Free)
    case FireStationState.Busy => gui.setFSState(fsCode, FireStationStateGUI.Busy)

  def updateFSZState(str: String, state: ZoneState): Unit = state match
    case ZoneState.Alarm => gui.setFSZState(str, ZoneStateGUI.Alarm)
    case ZoneState.Managing => gui.setFSZState(str, ZoneStateGUI.Managing)
    case ZoneState.Ok => gui.setFSZState(str, ZoneStateGUI.Ok)

