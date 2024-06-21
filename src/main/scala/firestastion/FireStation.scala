package firestastion

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import firestastion.FireStation.FireStationState
import message.Message
import zone.Zone.ZoneInfo

object FireStation:

  import zone.Zone.ZoneState

  sealed trait Command extends Message

  case class Managing() extends Command

  case class Solved() extends Command

  ///////////
  case class FindZone(zoneCode: String) extends Command

  case class ConnectedToZone(zoneRef: ActorRef[Message]) extends Command

  ///////////
  case class SendGetStatus(zoneRef: ActorRef[Message]) extends Command

  //////
  case class InformGui() extends Command

  enum FireStationState extends Command:
    case Free
    case Busy

  case class MessageToActorView(
                                 fireStationId: String,
                                 zoneState: Option[ZoneState],
                                 fireStationState: FireStationState,
                                 refForReply: ActorRef[Message]
                               ) extends Command


  def apply(name: String, fireStationCode: String, zoneCode: String) =
    new FireStation(name, fireStationCode, zoneCode).starting(zoneCode)

private case class FireStation(name: String, fireStationCode: String, zoneCode: String):

  import zone.Zone
  import FireStation.{Managing, Solved, FindZone, ConnectedToZone, SendGetStatus, InformGui}
  import scala.concurrent.duration.FiniteDuration
  import utilsduringdebug.PrintInfo
  import akka.actor.typed.pubsub.Topic
  import akka.actor.typed.pubsub.PubSub

  private val searchingZoneFrequency = FiniteDuration(5, "second")
  private val updateGUIConnectedFrequency = FiniteDuration(5, "second")
  private var lastZoneState: Option[Zone.ZoneState] = None
  private var lastFireStationState: Option[FireStationState] = Some(FireStationState.Free)

  private def starting(zoneCode: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.setLoggerName("" + ctx.self)
    PrintInfo.printActorInfo("From starting", ctx)
    val workerActor = ctx.spawn(waitConnectionToZone(), s"WORKER-FS-$fireStationCode")
    ctx.spawnAnonymous(connectToZone(zoneCode, ctx.self, workerActor))
    Behaviors.empty
    //    Behaviors.receiveMessagePartial {
    //      case ConnectedToZone(zoneRef) =>
    ////        val getStatusActor = ctx.spawn(getStatus(ctx.self, zoneRef), "actor-get-status")
    //        working(zoneRef)
    //    }
  }

  private def debugEmptyBehavior() = Behaviors.receiveMessagePartial {
    case _ => Behaviors.same
  }


  private def waitConnectionToZone(): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.setLoggerName("" + ctx.self)
    ctx.log.info(s"Inside wait connection to zone")
    PrintInfo.printActorInfo("From waitConnectionToZone", ctx)
    Behaviors.receivePartial {
      case (ctx, ConnectedToZone(zoneRef)) =>
        PrintInfo.printActorInfo("From wait conn... ConnectedToZone received", ctx)
        //        ctx.log.info("From wait conn... ConnectedToZone received")
        working(zoneRef)
      case (ctx, message) =>
        ctx.log.info(s"received message $message")
        Behaviors.same
    }
  }

  private def working(zoneRef: ActorRef[Message]): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(InformGui(), updateGUIConnectedFrequency)
      Behaviors.receivePartial {
        handlerInformGUIStatus(Behaviors.same)
          .orElse {
            case (ctx, _) =>
              ctx.log.info("Inside working")
              Behaviors.same
          }
      }
    }

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
        this.working(zoneRef)
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

  private def handlerInformGUIStatus(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, InformGui())
    =>
    //      PubSub(ctx.system).topic[Message](fireStationCode) !
    ???

  private def connectToZone(zoneCode: String, parentRef: ActorRef[Message], actorToRegister: ActorRef[Message]) =
    Behaviors.setup { ctx =>
      ctx.setLoggerName(s"FireStation.connectToZone-${ctx.self}")
      PrintInfo.printActorInfoG("From connect to zone", ctx)
      Behaviors.withTimers { timers =>
        val zoneServiceKey = ServiceKey[Message](zoneCode)
        timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
        Behaviors.receiveMessagePartial {
          case FindZone(zoneCode) =>
            PrintInfo.printActorInfoG("Timer end try to comunicate", ctx)
            // ctx.log.info("Timer end try to comunicate")
            ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.self)
            Behaviors.same
          case zoneServiceKey.Listing(l) =>
            PrintInfo.printActorInfoG("listing received, try connection", ctx)
            //            ctx.log.info("listing received, try connection")
            if l.nonEmpty then l.head ! Zone.RegisterFireStation(actorToRegister, ctx.self)
            Behaviors.same
          case Zone.ElementConnectedAck(zoneRef) =>
            PrintInfo.printActorInfoG("ElementConnectedAck Received", ctx)
            //            ctx.log.info("ElementConnectedAck Received")
            actorToRegister ! ConnectedToZone(zoneRef)
            Thread.sleep(1000)
            Behaviors.stopped
        }
      }
    }

