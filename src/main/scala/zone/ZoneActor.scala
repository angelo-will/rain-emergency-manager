package zone

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.typed.{Cluster, Subscribe}
import message.Message
import pluviometer.PluviometerActor
import zone.ZoneActor.{Command, MemberExitedAdapter}

import scala.concurrent.duration.FiniteDuration
import systemelements.SystemElements.{Pluviometer, Zone, ZoneState}


object ZoneActor:
  sealed trait Command extends Message


  ///// Messages from firestation

  case class RegisterFireStation(fSRef: ActorRef[Message], replyTo: ActorRef[Message]) extends Command

  case class GetZoneStatus(ref: ActorRef[Message]) extends Command

  case class ZoneStatus(zone: Zone, zoneRef: ActorRef[Message]) extends Command


  case class UnderManagement(fireStationRef: ActorRef[Message]) extends Command

  case class Solved(fireStationRef: ActorRef[Message]) extends Command

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
          case (ctx, PluviometerStatus(pluv, pluvRef)) =>
            ctx.log.info(s"Received pluviometer: $pluv")
            pluviometers(pluv.pluvCode) = pluv
            pluviometersRefs(pluvRef) = pluv.waterLevel >= maxWaterLevel
            if isZoneInAlarm then 
              pluvRef ! PluviometerActor.Alarm(ctx.self)
              for ((pluvRef, _) <- pluviometersRefs)
                pluviometersRefs(pluvRef) = true
              inAlarm
            else
              Behaviors.same  
        }
    }


  private def inAlarm: Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(Behaviors.same)
        .orElse {
          case (ctx, PluviometerStatus(pluv, pluvRef)) =>
            ctx.log.info(s"Received pluviometer: $pluv")
            pluviometers(pluv.pluvCode) = pluv
            pluvRef ! PluviometerActor.Alarm(ctx.self)
            Behaviors.same
          case (ctx, UnderManagement(fireSRef)) => underManagement
        }
    }

  private def underManagement: Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(Behaviors.same)
        .orElse {
          case (ctx, PluviometerStatus(pluv, pluvRef)) =>
            pluviometers(pluv.pluvCode) = pluv
            pluvRef ! PluviometerActor.Alarm(ctx.self)
            Behaviors.same
          case (ctx, Solved(fireSRef)) =>
            ctx.log.info("Received solved")
            this.resetAlarm(ctx)
            this.working
        }
    }


  private def pluStatusUpdates(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerStatus(pluv, pluvRef)) =>
      pluviometers(pluv.pluvCode) = pluv
      pluviometersRefs(pluvRef) = pluv.waterLevel >= maxWaterLevel
      behavior

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


  private def memberExited(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberExitedAdapter(event)) =>
      val actorRefToRemove = pluviometersRefs.keys.find(_.path.address == event.member.address)
      if actorRefToRemove.isDefined then pluviometersRefs.remove(actorRefToRemove.get)
      printPluvState(ctx)
      behavior

  private def resetAlarm(ctx: ActorContext[Message]) =
    for ((pluvRef, _) <- pluviometersRefs)
      pluvRef ! UnsetAlarm(ctx.self)
      pluviometersRefs(pluvRef) = false

  private def isZoneInAlarm =
    pluviometersRefs.foldLeft(0) {
      case (count, (_, value)) => if (value) count + 1 else count
    } >= pluviometersRefs.size
  //    } >= (pluviometers.size / 2)

  private def printPluvState(ctx: ActorContext[Message]) = ctx.log.info(s"pluviometers state: $pluviometersRefs")