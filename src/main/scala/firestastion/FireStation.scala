package firestastion

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import message.Message
import zone.Zone.ZoneInfo

object FireStation:

  import zone.Zone.ZoneState

  sealed trait Command extends Message

  case class Managing() extends Command

  case class Solved() extends Command

  ///////////
  case class FindZone(zoneCode: String) extends Command

  case class ConnectToZone(zoneRef: ActorRef[Message]) extends Command

  ///////////
  case class SendGetStatus(zoneRef: ActorRef[Message]) extends Command

  enum FireStationState extends Command:
    case Free
    case Busy

  case class MessageToActorView(
                                 zoneState: ZoneState,
                                 fireStationState: FireStationState
                               ) extends Command


  def apply(name: String, fireStationCode: String, zoneCode: String) =
    new FireStation(name, fireStationCode, zoneCode).searchZone(zoneCode)

private case class FireStation(name: String, fireStationCode: String, zoneCode: String):

  import zone.Zone
  import FireStation.{Managing, Solved, FindZone, ConnectToZone, SendGetStatus}
  import scala.concurrent.duration.FiniteDuration

  private val searchingZoneFrequency = FiniteDuration(5, "second")

  private def searchZone(zoneCode: String): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
      Behaviors.receivePartial {
        case (ctx, FindZone(zoneCode)) =>
          ctx.spawnAnonymous(connectToZone(zoneCode, ctx.self))
          Behaviors.same
        case (ctx, ConnectToZone(zoneRef)) =>
          timers.cancelAll()
          val getStatusActor = ctx.spawn(getStatus(ctx.self, zoneRef), "actor-get-status")
          free(zoneRef)
      }
    }

  private def free(zoneRef: ActorRef[Message]): Behavior[Message] =
    ???

  private def inAlarm(zoneRef: ActorRef[Message]): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, Managing()) =>
        ctx.log.info("Received managing signal")
        this.inManaging(zoneRef)
    }

  private def inManaging(zoneRef: ActorRef[Message]): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, Solved()) =>
        ctx.log.info("Situation solved")
        this.free(zoneRef)
    }

  private def getStatus(parentActor: ActorRef[Message], zoneRef: ActorRef[Message]): Behavior[Message] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(SendGetStatus(zoneRef), FiniteDuration(5, "s"))
        Behaviors.receiveMessagePartial {
          case SendGetStatus(zoneRef) => 
            zoneRef ! Zone.GetStatus(parentActor)
            Behaviors.same
//          case ZoneInfo(zoneState, pluvQuantity) =>
//            parentActor ! ZoneInfo(zoneState, pluvQuantity)
//            Behaviors.same
        }
      }
    }

  private def handlerZoneStatus(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, ZoneInfo(zoneState, pluvQuantity)) => ???
  private def connectToZone(zoneCode: String, replyTo: ActorRef[Message]) =
    Behaviors.setup { ctx2 =>
      val zoneServiceKey = ServiceKey[Message](zoneCode)
      ctx2.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx2.self)
      Behaviors.receiveMessagePartial {
        case zoneServiceKey.Listing(l) =>
          l.foreach(e =>
            ctx2.log.info(s"Element listing: $e")
            replyTo ! ConnectToZone(e)
          )
          Thread.sleep(2000)
          Behaviors.stopped
      }
    }  

