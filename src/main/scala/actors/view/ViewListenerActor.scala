package actors.view

import actors.firestastion.FireStationActor
import akka.actor.typed.ActorRef
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.ActorContext
import message.Message

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
          // No need to send message on the topic
          // There's nothing to do, just wait for the alarm
          // The zone is in a OK state
        }
      case ActionCommand.INTERVENE =>
        // The firestation is in alarm and must intervene
        Action("INTERVIENI") {
          // Post a message on the topic
          // To say that the fire station is intervening
          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message](topicName)
          topic ! Topic.publish(FireStationActor.Managing(fsCode))
        }
      case ActionCommand.END_INTERVENTION =>
        // The firestation has started the intervention and eventually ended it
        Action("TERMINA") {
          // Post a message on the topic
          // To say that the fire station has solved the problem
          val topic: ActorRef[Topic.Command[Message]] = PubSub(ctx.system).topic[Message](topicName)
          topic ! Topic.publish(FireStationActor.Solved(fsCode))
        }