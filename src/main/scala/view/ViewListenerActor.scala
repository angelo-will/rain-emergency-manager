package view

import akka.actor.typed.ActorRef
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.ActorContext
import firestastion.FireStationActor
import firestastion.FireStationActor.FireStationStatus
import message.Message
import systemelements.SystemElements.{FireStation, FireStationState, Zone, ZoneState}

import scala.swing.Action


object ViewListenerActor:

  enum ActionCommand:
    case WAITING
    case INTERVENE
    case END_INTERVENTION

  def apply(fsCode: String): Action =
    Action("INTERVIENI") {
      println(s"INTERVIENE la caserma:$fsCode")
    }

  def apply(fsCode: String, ctx: ActorContext[Message], actionCommand: ActionCommand): Action =
    actionCommand match
      case ActionCommand.WAITING =>
        Action("IN ATTESA") {
          println(s"Nessun problema per la caserma:$fsCode")
          // No need to send message to ViewActor or FireStationActor
          // There's nothing to do, just wait for the alarm
          // The zone is in a OK state

          // To delete, just for testing
//          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message]("firestations-topic")
//          val msg = FireStationStatus(FireStation(fsCode, FireStationState.Free, Zone(
//            "zoneCode",
//            ZoneState.Alarm,
//            pluviometers = Map(),
//            maxPluviometersPerZone = 3,
//            maxWaterLevel = 200,
//            2,
//            1,
//            width = 100,
//            height = 100)))
//          topic ! Topic.publish(msg)
        }
      case ActionCommand.INTERVENE =>
        // The firestation is in alarm and must intervene
        Action("INTERVIENI") {
          println(s"Ora interviene la caserma:$fsCode")
          // Send message to ViewActor or FireStationActor
          // To say that the fire station is intervening
          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message]("firestations-topic")
          topic ! Topic.publish(FireStationActor.Managing(fsCode))
        }
      case ActionCommand.END_INTERVENTION =>
        // The firestation has started the intervention and eventually ended it
        Action("IN INTERVENTO") {
          println(s"Sta intervendendo la caserma:$fsCode")
          // No need to send message to ViewActor or FireStationActor
          // The fire station is already intervening and when the intervention ends
          // it will send a message to the ViewActor or FireStationActor
          // The zone is in a InManaging state
          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message]("firestations-topic")
          topic ! Topic.publish(FireStationActor.Solved(fsCode))
        }