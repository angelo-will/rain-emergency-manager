package firestastion

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import message.Message

object FireStation:

  sealed trait Command extends Message

  case class WatchZone(zoneRef: ActorRef[Message]) extends Command
  case class Managing() extends Command
  case class Solved() extends Command

  def apply(ctx: ActorContext[Message], name: String, fireStationCode: String) =
    new FireStation(ctx, name, fireStationCode).free

private case class FireStation(ctx: ActorContext[Message], name: String, fireStationCode: String):

  import zone.Zone
  import FireStation.WatchZone
  import FireStation.Managing
  import FireStation.Solved
  var zoneWatching: Option[ActorRef[Message]] = None

  private def free: Behavior[Message] =
    Behaviors.receiveMessagePartial {
      case WatchZone(zoneRef) =>
        this.zoneWatching = Option(zoneRef)
//        zoneRef ! Zone.GetStatus()
        Behaviors.same
      case _ =>
        this.inAlarm
    }

  private def inAlarm: Behavior[Message] =
    Behaviors.receivePartial { 
      case (ctx, Managing()) => 
        ctx.log.info("Received managing signal")
        this.inManaging
    }
    
  private def inManaging: Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, Solved()) => 
        ctx.log.info("Situation solved")
        this.free
    }

