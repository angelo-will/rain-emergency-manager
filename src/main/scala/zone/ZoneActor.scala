package zone

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.typed.{Cluster, Subscribe}
import message.Message
import zone.ZoneActor.{Command, MemberExitedAdapter}

import scala.concurrent.duration.FiniteDuration
import systemelements.SystemElements.{Pluviometer, Zone, ZoneState}


object ZoneActor:
  sealed trait Command extends Message


  ///// Messages from firestation

  case class RegisterFireStation(fSRef: ActorRef[Message], replyTo: ActorRef[Message]) extends Command

  case class GetZoneStatus(ref: ActorRef[Message]) extends Command

  case class ZoneStatus(zone: Zone, zoneRef: ActorRef[Message]) extends Command


  case class UnderManagement() extends Command

  case class Solved() extends Command

  //////

  /**
   * Give an ack to notify that element who has requested is connected.
   *
   * @param zoneRef ref to prime actor of zone
   */
  case class ElementConnectedAck(zoneRef: ActorRef[Message]) extends Command

  //////
  case class MemberExitedAdapter(event: MemberExited) extends Command


  private def memberEventBehavior(zoneCtx: ActorContext[Message]) =
    Behaviors.setup { ctx2 =>
      Cluster(zoneCtx.system).subscriptions ! Subscribe(
        ctx2.messageAdapter[MemberExited](MemberExitedAdapter.apply),
        classOf[MemberExited]
      )
      Behaviors.receivePartial {
        case (memberEventCtx, MemberExitedAdapter(event)) =>
          memberEventCtx.log.info(s"MemberEventWrapper received with event ${event.member.address}")
          zoneCtx.self ! MemberExitedAdapter(event)
          Behaviors.same
      }
    }

  def apply(zone: Zone): Behavior[Message] = Behaviors.setup { zoneCtx =>
    zoneCtx.spawn(memberEventBehavior(zoneCtx), "Member-event-actor")
    new ZoneActor(zone).creating
  }

private case class ZoneActor(zone: Zone):

  import ZoneActor.*
  import pluviometer.PluviometerActor.{Alarm, UnsetAlarm, PluviometerTryRegister, PluviometerStatus}
  import scala.collection.mutable
  import akka.actor.typed.receptionist.Receptionist

  private val pluviometersRefs: mutable.Map[ActorRef[Message], Boolean] = mutable.Map()
  private val pluviometers: mutable.Map[String, Pluviometer] = mutable.Map()
  private val maxPluviometersConnected = 2
  private val maxWaterLevel = 100

  private def creating: Behavior[Message] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Register(ServiceKey[Message](zone.zoneCode), ctx.self)
    this.working
  }
  //    this.ctx.log.info("Entering creating")

  private def working: Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(Behaviors.same)
        .orElse {
          case _ => Behaviors.same
        }
    }
  //  private def working: Behavior[Message] =
  //    Behaviors.receivePartial {
  //      handlers(Behaviors.same)
  //        .orElse(getStatus(Behaviors.same, ZoneState.Ok))
  //        .orElse {
  //          case (ctx, Alarm(pluvRef)) =>
  //            ctx.log.info(s"RECEIVED ALARM from ${pluvRef.path.address}")
  //            pluviometers(pluvRef) = true
  //            printPluvState(ctx)
  //            if this.isZoneInAlarm then
  //              // fireS ! ZoneInfo(ZoneState.Alarm, pluviometers.size)
  //              inAlarm
  //            //            else
  //
  //            Behaviors.same
  //          case (ctx, NoAlarm(pluvRef)) =>
  //            ctx.log.info(s"RECEIVED NO ALARM from ${pluvRef.path.address}")
  //            // fireS ! ZoneInfo(ZoneState.Ok, pluviometers.size)
  //            pluviometers(pluvRef) = false
  //            printPluvState(ctx)
  //            Behaviors.same
  //        }
  //    }


  private def inAlarm: Behavior[Message] =
    println("------------------------------------------------ AAAAAAAAAAAAAAAAA ZONE IN ALARM")
    //    Behaviors.withTimers{timers =>
    //      timers.startTimerAtFixedRate(SendInfo(),FiniteDuration(3, "second"))
    //      Behaviors.receivePartial {
    //          case (ctx,SendInfo()) =>
    //            // fireStationRef ! zoneInfo(Zonestate.Alarm
    //            ctx.log.info("AAAAA Zone return in working")
    //            this.resetAlarm(ctx)
    //            this.working
    //      }
    //    }
    // COMMENTED FOR DEBUG
    Behaviors.receivePartial {
      pluvTryRegister(Behaviors.same)
//        .orElse(getStatus(Behaviors.same, ZoneState.Alarm))
        //        .orElse(receivedUpdates(Behaviors.same, ZoneState.Alarm))
        .orElse {
          case (ctx, UnderManagement()) =>
            ctx.log.info("Received under management")
            this.underManagement
        }
    }

  private def underManagement: Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(Behaviors.same)
//        .orElse(getStatus(Behaviors.same, ZoneState.Managing))
        //        .orElse(receivedUpdates(Behaviors.same, ZoneState.Managing))
        .orElse {
          case (ctx, Solved()) =>
            ctx.log.info("Received solved")
            this.resetAlarm(ctx)
            this.working
        }
    }
    
    

  private def pluStatusUpdates(behavior: Behavior[Message], zoneState: ZoneState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerStatus(pluv, pluvRef)) =>
      pluviometers(pluv.pluvCode) = pluv
      if pluv.waterLevel >= maxWaterLevel then pluviometersRefs(pluvRef) = true
      
      Behaviors.same
  //  private def receivedUpdates(behavior: Behavior[Message], zoneState: ZoneState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
  //    case (ctx, Alarm(pluvRef)) =>
  //      pluviometersRefs(pluvRef) = true
  //      //fireStationRef ! zoneInfo(zoneState, pluviometers.size)
  //      Behaviors.same
  //    case (ctx, NoAlarm(pluvRef)) =>
  //      pluviometersRefs(pluvRef) = false
  //      //fireStationRef ! zoneInfo(zoneState, pluviometers.size)
  //      Behaviors.same

  private def pluvTryRegister(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerTryRegister(pluviometer, actorToRegister)) =>
      if pluviometersRefs.size < maxPluviometersConnected then
        ctx.log.info(s"New pluv connected with code ${pluviometer.pluvCode}")
        ctx.log.info(s"New pluv connected with ${actorToRegister.path.address}")
        pluviometersRefs(actorToRegister) = false
        pluviometers(pluviometer.pluvCode) = pluviometer
        actorToRegister ! ElementConnectedAck(ctx.self)
      else
        ctx.log.info(s"Ricevuto messaggio di registrazione ma ci sono sià $maxPluviometersConnected registrati")
      behavior

  //  private def fireStationRegister(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
  //    case (ctx, RegisterFireStation(fsRef, replyTo)) =>
  //    fireStations += fsRef
  //    replyTo ! ElementConnectedAck(ctx.self)
  //
  //    behavior

  private def memberExited(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberExitedAdapter(event)) =>
      val actorRefToRemove = pluviometersRefs.keys.find(_.path.address == event.member.address)
      if actorRefToRemove.isDefined then pluviometersRefs.remove(actorRefToRemove.get)
      printPluvState(ctx)
      behavior

//  private def getStatus(behavior: Behavior[Message], zoneState: ZoneState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
//    case (ctx, GetStatus(replyTo)) =>
//      replyTo ! ZoneInfo(zoneState, pluviometersRefs.size)
//      behavior
//
//  private def handlers(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
//    pluvTryRegister(behavior)
//      .orElse(fireStationRegister(behavior))
//      .orElse(memberExited(behavior))

  private def resetAlarm(ctx: ActorContext[Message]) =
    //    ctx.log.info(s"Sending unsetting alarm")
    for ((pluvRef, _) <- pluviometersRefs)
      //pluvRef ! UnsetAlarm(ctx.self)
      pluviometersRefs(pluvRef) = false

  private def isZoneInAlarm =
    pluviometersRefs.foldLeft(0) {
      case (count, (_, value)) => if (value) count + 1 else count
    } >= pluviometersRefs.size
  //    } >= (pluviometers.size / 2)

  private def printPluvState(ctx: ActorContext[Message]) = ctx.log.info(s"pluviometers state: $pluviometersRefs")
