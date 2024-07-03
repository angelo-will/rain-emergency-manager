package actors.view

import actors.firestastion.FireStationActor.{FireStationStatus, ZoneNotFound}
import actors.view.ViewActor.CheckFireStationConnection
import actors.view.ViewListenerActor.ActionCommand.{END_INTERVENTION, INTERVENE, WAITING}
import akka.actor.typed.Behavior
import actors.message.Message
import systemelements.SystemElements.*
import view.FireStationGUI

import scala.concurrent.duration.FiniteDuration

object ViewActor:
  sealed trait Command extends Message

  private case class CheckFireStationConnection() extends Command

  def apply(fsCode: String, allFSCodes: Seq[String], topicName: String): Behavior[Message] =
    new ViewActor(fsCode, allFSCodes, topicName).start()


case class ViewActor(fsCode: String, allFSCodes: Seq[String], topicName: String):

  import actors.firestastion.FireStationActor
  import akka.actor.typed.pubsub.{PubSub, Topic}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.{ActorRef, Behavior}
  import systemelements.SystemElements.FireStationState
  import view.FireStationGUI.{FireStationStateGUI, ZoneStateGUI}

  import scala.collection.mutable

  private val gui = FireStationGUI(fsCode, allFSCodes)
  private val checkFSConnectionFrequency = FiniteDuration(20, "second")
  private val maxTimerCycle = 1
  private val counters = mutable.Map.from(allFSCodes.map(code => (code, 0)))

  private def start(): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(listenerFireStation(ctx.self), s"listener-fs")
    for fs <- allFSCodes do
      gui.setButtonAction(ViewListenerActor(fs, topicName, ctx, INTERVENE), fs)
    gui.main(Array.empty)
    debugBehav()
  }

  private def listenerFireStation(forwardActor: ActorRef[Message]): Behavior[Message] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(CheckFireStationConnection(), checkFSConnectionFrequency)
      val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message](topicName)
      topic ! Topic.subscribe(ctx.self)
      Behaviors.receiveMessagePartial {
        // Sent by Actors
        case FireStationStatus(FireStation(fsCode, fireStationState, zoneClass)) =>
          updateControlledZone(fsCode, zoneClass.zoneCode)
          updateFSZSensorsQuantityState(fsCode, zoneClass.pluviometers.size)
          updateFSState(fsCode, fireStationState)
          updateFSZState(fsCode, zoneClass.zoneState, ctx)
          counters(fsCode) = 0
          Behaviors.same

        case ZoneNotFound(fsCode) =>
          updateZoneNotFound(fsCode)
          Behaviors.same

        case CheckFireStationConnection() =>
          for (k, v) <- counters do
            if v >= maxTimerCycle then
              gui.disableFireStation(k)
              ctx.log.info(s"Disabilito FS $k")
            else
              counters(k) = v + 1
          Behaviors.same

      }
    }
  }

  private def debugBehav(): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, _) => Behaviors.same
    }

  private def updateControlledZone(fsCode: String, zoneCode: String): Unit =
    gui.setFSZControlled(fsCode, zoneCode)

  private def updateZoneNotFound(fsCode: String): Unit =
    gui.disableFireStation(fsCode)

  private def updateFSZSensorsQuantityState(fsCode: String, pluvQuantity: Int): Unit =
    gui.setPluvZoneQuantity(fsCode, pluvQuantity)

  private def updateFSState(fsCode: String, fireStationState: FireStationState): Unit =
    fireStationState match
      case fsState if fsState.equals(FireStationFree()) => gui.setFSState(fsCode, FireStationStateGUI.Free)
      case fsState if fsState.equals(FireStationBusy()) => gui.setFSState(fsCode, FireStationStateGUI.Busy)

  private def updateFSZState(fsCode: String, zoneState: ZoneState, context: ActorContext[Message]): Unit =
    zoneState match
      case zState if zState.equals(ZoneAlarm()) =>
        gui.setFSZState(fsCode, ZoneStateGUI.Alarm)
        gui.setButtonAction(ViewListenerActor(fsCode, topicName, context, INTERVENE), fsCode)
      case zState if zState.equals(ZoneInManaging()) =>
        gui.setFSZState(fsCode, ZoneStateGUI.Managing)
        gui.setButtonAction(ViewListenerActor(fsCode, topicName, context, END_INTERVENTION), fsCode)
      case zState if zState.equals(ZoneOk()) =>
        gui.setFSZState(fsCode, ZoneStateGUI.Ok)
        gui.setButtonAction(ViewListenerActor(fsCode, topicName, context, WAITING), fsCode)