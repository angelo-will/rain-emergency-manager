package view

import akka.actor.typed.ActorRef
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.Behaviors
import firestastion.FireStation.{FireStationState, MessageToActorView}
import message.Message

import scala.swing.Action


object ViewListenerActor:
  sealed trait Command extends Message

  def apply(fsCode: String): Action =
    Action("INTERVIENI") {
      println(s"INTERVIENE la caserma:$fsCode")
    }
    
  def apply(fsCode: String, actorRef: ActorRef[Message]): Action =
    Action("INTERVIENI") {
      println(s"INTERVIENE la caserma:$fsCode")
      actorRef ! MessageToActorView(fsCode, None, FireStationState.Busy, actorRef)
    }