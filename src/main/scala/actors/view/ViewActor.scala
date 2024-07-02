package actors.view

import actors.firestastion.FireStationActor.{FireStationStatus, ZoneNotFound}
import actors.view.ViewListenerActor.ActionCommand.{END_INTERVENTION, INTERVENE, WAITING}
import akka.actor.typed.Behavior
import message.Message
import systemelements.SystemElements.*
import view.FireStationGUI

object ViewActor:
  sealed trait Command extends Message

  def apply(fsCode:String, allFSCodes: Seq[String], topicName: String): Behavior[Message] =
    new ViewActor(fsCode, allFSCodes, topicName).start()


case class ViewActor(fsCode: String, allFSCodes: Seq[String], topicName: String):

  import actors.firestastion.FireStationActor
  import akka.actor.typed.pubsub.{PubSub, Topic}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.{ActorRef, Behavior}
  import systemelements.SystemElements.FireStationState
  import view.FireStationGUI.{FireStationStateGUI, ZoneStateGUI}

  import scala.collection.mutable

//  private val fireStations = mutable.Map[String, ActorRef[Message]]()
  private val gui = FireStationGUI(fsCode, allFSCodes)
  private var context: ActorContext[Message] = _

  private def start(): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(listenerFireStation(ctx.self), s"listener-fs")
    context = ctx
    for fs <- allFSCodes do
      gui.setButtonAction(ViewListenerActor(fs, topicName, ctx, INTERVENE), fs)
    gui.main(Array.empty)
    debugBehav()
  }

  private def listenerFireStation(forwardActor: ActorRef[Message]) = Behaviors.setup { ctx =>
    val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message](topicName)
    topic ! Topic.subscribe(ctx.self)
    Behaviors.receiveMessagePartial {
      // Sent by Actors
      case FireStationStatus(FireStation(fsCode, fireStationState, zoneClass)) =>
        updateControlledZone(fsCode, zoneClass.zoneCode)
        updateFSZSensorsQuantityState(fsCode, zoneClass.pluviometers.size)
        updateFSState(fsCode, fireStationState)
        updateFSZState(fsCode, zoneClass.zoneState)
        Behaviors.same

      case ZoneNotFound(fsCode) => 
        updateZoneNotFound(fsCode)
        Behaviors.same
        
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

  private def updateFSZState(fsCode: String, zoneState: ZoneState): Unit =
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