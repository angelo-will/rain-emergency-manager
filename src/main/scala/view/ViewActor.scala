package view

import akka.actor.typed.scaladsl.{Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import message.Message

object ViewActor:
  sealed trait Command extends Message

  def apply() = new ViewActor().running()

case class ViewActor():

//  import firestastion.FireStation.FireStationState
//  import view.FireStationGUI
//  import view.FireStationGUI.FireStationStateGUI

  val AAA = 0

  def running(): Behavior[Message] = Behaviors.setup { ctx =>
    val gui = FireStationGUI()
    gui.main(Array())
//    Behaviors.receiveMessagePartial {
//      case FireStationState.Free => gui.setFireStationState(FireStationStateGUI.Free); Behaviors.same
//      case FireStationState.Busy => gui.setFireStationState(FireStationStateGUI.Busy); Behaviors.same
//    }
    Behaviors.empty
  }
//      case FireStation. => gui.set(FireStation.State(z, f)); Behaviors.same
//      case FireStation. => gui.set(FireStation.State(z, f)); Behaviors.same
