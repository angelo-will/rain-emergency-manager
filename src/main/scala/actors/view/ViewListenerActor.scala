package actors.view

import actors.firestastion.FireStationActor
import actors.firestastion.FireStationActor.FireStationStatus
import akka.actor.typed.ActorRef
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.ActorContext
import message.Message
import systemelements.SystemElements.{FireStation, FireStationState, Zone, ZoneState}

import scala.swing.Action


object ViewListenerActor:

  enum ActionCommand:
    case WAITING
    case INTERVENE
    case END_INTERVENTION

  def apply(fsCode: String, topicName: String, ctx: ActorContext[Message], actionCommand: ActionCommand): Action =
    actionCommand match
      case ActionCommand.WAITING =>
        Action("IN ATTESA") {
          println(s"Nessun problema per la caserma:$fsCode")
          // No need to send message on the topic
          // There's nothing to do, just wait for the alarm
          // The zone is in a OK state
        }
      case ActionCommand.INTERVENE =>
        // The firestation is in alarm and must intervene
        Action("INTERVIENI") {
          println(s"Ora interviene la caserma:$fsCode")
          // Post a message on the topic
          // To say that the fire station is intervening
          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message](topicName)
          topic ! Topic.publish(FireStationActor.Managing(fsCode))
        }
      case ActionCommand.END_INTERVENTION =>
        // The firestation has started the intervention and eventually ended it
        Action("TERMINA") {
          println(s"Sta intervendendo la caserma:$fsCode")
          // No need to send message to the topic
          // The fire station is already intervening and when the intervention ends
          // it will send a message to the ViewActor or FireStationActor
          // The zone is in a InManaging state
          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message](topicName)
          topic ! Topic.publish(FireStationActor.Solved(fsCode))
        }