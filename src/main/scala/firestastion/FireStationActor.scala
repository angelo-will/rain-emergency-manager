package firestastion

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import firestastion.FireStationActor.AdapterMessageForConnection
import message.Message
import systemelements.SystemElements.{FireStation, FireStationState, Zone, ZoneState}
import zone.ZoneActor

object FireStationActor:

  sealed trait Command extends Message

  case class Managing() extends Command

  case class Solved() extends Command

  ///////////
  case class FindZone(zoneCode: String) extends Command

  case class ConnectedToZone(zoneRef: ActorRef[Message]) extends Command

  ///////////
  case class SendGetStatus(zoneRef: ActorRef[Message]) extends Command

  //////
  
  case class FireStationStatus(fireStationStatus: FireStation) extends Command

//  case class MessageToActorView(
//                                 fireStationId: String,
//                                 zoneState: Option[ZoneState],
//                                 fireStationState: FireStationState,
//                                 refForReply: ActorRef[Message]
//                               ) extends Command

  //adapter use for recover the zoneActor reference
  private case class AdapterMessageForConnection(listing: Receptionist.Listing) extends Command


  def apply(name: String, fireStationCode: String, zoneCode: String): Behavior[Message] =
    new FireStationActor(name, fireStationCode, zoneCode).starting()

private case class FireStationActor(name: String, fireStationCode: String, zoneCode: String):
  
  import FireStationActor.{Managing, Solved, FindZone, ConnectedToZone, SendGetStatus, FireStationStatus}
  import scala.concurrent.duration.FiniteDuration
  import utilsduringdebug.PrintInfo
  import akka.actor.typed.pubsub.Topic
  import akka.actor.typed.pubsub.PubSub

  private val searchingZoneFrequency = FiniteDuration(5, "second")
  private val updateGUIConnectedFrequency = FiniteDuration(5, "second")
  private val askZoneStatus = FiniteDuration(5, "second")
  private var lastZoneState: Option[ZoneState] = None
//  private var lastFireStationState: Option[FireStationState] = Some(FireStationState.Free)

  private var zoneRef: Option[ActorRef[Message]] = None

  //First thing to do is retrieve the zoneActor reference
  private def starting(): Behavior[Message] = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("channel")
    topic ! Topic.Subscribe(ctx.self)

    Behaviors.withTimers { timers =>
      val zoneServiceKey = ServiceKey[Message](zoneCode)
      timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
      Behaviors.receiveMessagePartial {
        case FindZone(zoneCode) =>
          ctx.log.info(s"Timer tick, i'm trying to connect to zone $zoneCode")
          ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.messageAdapter[Receptionist.Listing](AdapterMessageForConnection.apply))
          Behaviors.same
        case AdapterMessageForConnection(zoneServiceKey.Listing(l))  =>
          if (l.nonEmpty) {
            // found the zone, move to work
            operating(l.head)
//            zoneRef = Some(l.head)
//            Behaviors.same //CAMBIO CON NUOVO BEHAVIOUR DI QUANDO LAVORA
          } else Behaviors.same
      }
    }
  }

  private def operating(zoneRef: ActorRef[Message]): Behavior[Message] = Behaviors.setup { ctx =>

    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("channel")

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendGetStatus(zoneRef), askZoneStatus)
      Behaviors.receiveMessagePartial {
        case SendGetStatus(zoneRef) =>
          zoneRef ! ZoneActor.GetZoneStatus(ctx.self)
          Behaviors.same
        case ZoneActor.ZoneStatus(zoneClass, zoneRef) =>
          //send message to gui using pub/sub
          topic ! Topic.publish(
            FireStationStatus(FireStation(fireStationCode, FireStationState.Free, zoneClass))
          )
          zoneClass.zoneState match
            case ZoneState.Alarm => inAlarm(zoneRef)
            case _ => Behaviors.same
      }
    }
  }

//  private def starting(zoneCode: String): Behavior[Message] = Behaviors.setup { ctx =>
//    ctx.setLoggerName("" + ctx.self)
//    PrintInfo.printActorInfo("From starting", ctx)
//    val workerActor = ctx.spawn(waitConnectionToZone(), s"WORKER-FS-$fireStationCode")
//    ctx.spawnAnonymous(connectToZone(zoneCode, ctx.self, workerActor))
//    Behaviors.empty
//    //    Behaviors.receiveMessagePartial {
//    //      case ConnectedToZone(zoneRef) =>
//    ////        val getStatusActor = ctx.spawn(getStatus(ctx.self, zoneRef), "actor-get-status")
//    //        working(zoneRef)
//    //    }
//  }

  private def debugEmptyBehavior() = Behaviors.receiveMessagePartial {
    case _ => Behaviors.same
  }


//  private def waitConnectionToZone(): Behavior[Message] = Behaviors.setup { ctx =>
//    ctx.setLoggerName("" + ctx.self)
//    ctx.log.info(s"Inside wait connection to zone")
//    PrintInfo.printActorInfo("From waitConnectionToZone", ctx)
//    Behaviors.receivePartial {
//      case (ctx, ConnectedToZone(zoneRef)) =>
//        PrintInfo.printActorInfo("From wait conn... ConnectedToZone received", ctx)
//        //        ctx.log.info("From wait conn... ConnectedToZone received")
//        working(zoneRef)
//      case (ctx, message) =>
//        ctx.log.info(s"received message $message")
//        Behaviors.same
//    }
//  }

//  private def working(zoneRef: ActorRef[Message]): Behavior[Message] =
//    Behaviors.withTimers { timers =>
//      timers.startTimerAtFixedRate(InformGui(), updateGUIConnectedFrequency)
//      Behaviors.receivePartial {
//        handlerInformGUIStatus(Behaviors.same)
//          .orElse {
//            case (ctx, _) =>
//              ctx.log.info("Inside working")
//              Behaviors.same
//          }
//      }
//    }

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
        this.operating(zoneRef)
    }

//  private def getStatus(parentActor: ActorRef[Message], zoneRef: ActorRef[Message]): Behavior[Message] =
//    Behaviors.setup { ctx =>
//      Behaviors.withTimers { timers =>
//        timers.startTimerAtFixedRate(SendGetStatus(zoneRef), FiniteDuration(5, "s"))
//        Behaviors.receiveMessagePartial {
//          case SendGetStatus(zoneRef) =>
//            zoneRef ! Zone.GetStatus(parentActor)
//            Behaviors.same
//          //          case ZoneInfo(zoneState, pluvQuantity) =>
//          //            parentActor ! ZoneInfo(zoneState, pluvQuantity)
//          //            Behaviors.same
//        }
//      }
//    }
//
//  private def handlerZoneStatus(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
//    case (ctx, ZoneInfo(zoneState, pluvQuantity)) => ???
//
//  private def handlerInformGUIStatus(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
//    case (ctx, InformGui())
//    =>
//    //      PubSub(ctx.system).topic[Message](fireStationCode) !
//    ???
//
//  private def connectToZone(zoneCode: String, parentRef: ActorRef[Message], actorToRegister: ActorRef[Message]) =
//    Behaviors.setup { ctx =>
//      ctx.setLoggerName(s"FireStation.connectToZone-${ctx.self}")
//      PrintInfo.printActorInfoG("From connect to zone", ctx)
//      Behaviors.withTimers { timers =>
//        val zoneServiceKey = ServiceKey[Message](zoneCode)
//        timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
//        Behaviors.receiveMessagePartial {
//          case FindZone(zoneCode) =>
//            PrintInfo.printActorInfoG("Timer end try to comunicate", ctx)
//            // ctx.log.info("Timer end try to comunicate")
//            ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.self)
//            Behaviors.same
//          case zoneServiceKey.Listing(l) =>
//            PrintInfo.printActorInfoG("listing received, try connection", ctx)
//            //            ctx.log.info("listing received, try connection")
//            if l.nonEmpty then l.head ! Zone.RegisterFireStation(actorToRegister, ctx.self)
//            Behaviors.same
//          case Zone.ElementConnectedAck(zoneRef) =>
//            PrintInfo.printActorInfoG("ElementConnectedAck Received", ctx)
//            //            ctx.log.info("ElementConnectedAck Received")
//            actorToRegister ! ConnectedToZone(zoneRef)
//            Thread.sleep(1000)
//            Behaviors.stopped
//        }
//      }
//    }

