package zone

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.MemberExited
import systemelements.SystemElements.*
import message.Message


object ZoneActor:
  sealed trait Command extends Message

  case class GetZoneStatus(ref: ActorRef[Message]) extends Command

  case class ZoneStatus(zone: Zone, zoneRef: ActorRef[Message]) extends Command

  case class UnderManagement(fireStationRef: ActorRef[Message]) extends Command

  case class Solved(fireStationRef: ActorRef[Message]) extends Command

  /**
   * Give an ack to notify that element who has requested is connected.
   *
   * @param zoneRef ref to prime actor of zone
   */
  case class ElementConnectedAck(zoneRef: ActorRef[Message]) extends Command

  case class MemberExitedAdapter(event: MemberExited) extends Command

  def apply(zone: Zone): Behavior[Message] = new ZoneActor().creating(zone)

private case class ZoneActor():

  import akka.cluster.typed.{Cluster, Subscribe}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.receptionist.ServiceKey
  import scala.collection.mutable
  import ZoneActor.*
  import pluviometer.PluviometerActor
  import PluviometerActor.{Alarm, UnsetAlarm, PluviometerTryRegister, PluviometerStatus}
  import akka.actor.typed.receptionist.Receptionist

  private val pluviometersRefs: mutable.Map[ActorRef[Message], Boolean] = mutable.Map()

  private def creating(zone: Zone): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Register(ServiceKey[Message](zone.zoneCode), ctx.self)
    ctx.spawn(memberEventBehavior(ctx), "Member-event-actor")
    this.working(zone)
  }

  private def working(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone)
        .orElse(getZoneStatusHandler(zone, working))
        .orElse(memberExited())
        .orElse {
          case (ctx, PluviometerStatus(pluv, pluvRef)) =>
            ctx.log.info(s"Received pluviometer: $pluv")
            val newPluviometers = zone.pluviometers + ((pluv.pluvCode, pluv))
            pluviometersRefs(pluvRef) = pluv.waterLevel >= zone.maxWaterLevel
            if isZoneInAlarm2(zone) then
              pluvRef ! PluviometerActor.Alarm(ctx.self)
              for ((pluvRef, _) <- pluviometersRefs)
                pluviometersRefs(pluvRef) = true
              inAlarm(zone.copy(zoneState = ZoneOk(), pluviometers = newPluviometers))
            else
              working(zone.copy(zoneState = ZoneOk(), pluviometers = newPluviometers))
        }
    }

  private def pluvStatusUpdates(zone: Zone, behavior: Zone => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerStatus(pluv, pluvRef)) =>
      ctx.log.info(s"Received pluviometer: $pluv")
      val newPluviometers = zone.pluviometers + ((pluv.pluvCode, pluv.copy(pluviometerState = PluviometerAlarm())))
      zone.zoneState match
        case ZoneAlarm() | ZoneInManaging() =>
          for ((pluvRef, _) <- pluviometersRefs)
            pluviometersRefs(pluvRef) = true
          pluvRef ! PluviometerActor.Alarm(ctx.self)
          behavior(zone.copy(pluviometers = newPluviometers))
        case _ => Behaviors.same


  private def inAlarm(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone)
        .orElse(pluvStatusUpdates(zone, inAlarm))
        .orElse(memberExited())
        .orElse(getZoneStatusHandler(zone, inAlarm))
        .orElse {
          case (ctx, UnderManagement(fireSRef)) => underManagement(zone.copy(zoneState = ZoneInManaging()))
        }
    }

  private def underManagement(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone)
        .orElse(pluvStatusUpdates(zone, underManagement))
        .orElse(memberExited())
        .orElse(getZoneStatusHandler(zone, underManagement))
        .orElse {
          case (ctx, Solved(fireSRef)) =>
            ctx.log.info("Received solved")
            this.resetAlarm(ctx)
            working(zone.copy(zoneState = ZoneOk()))
        }
    }

  private def pluvTryRegister(zone: Zone): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerTryRegister(pluviometer, actorToRegister)) =>
      if pluviometersRefs.size < zone.maxPluviometersPerZone then
        ctx.log.info(s"New pluv connected with code ${pluviometer.pluvCode}")
        ctx.log.info(s"New pluv connected with ${actorToRegister.path.address}")
        pluviometersRefs(actorToRegister) = false
        actorToRegister ! ElementConnectedAck(ctx.self)
      else
        ctx.log.info(s"Ricevuto messaggio di registrazione ma ci sono sià ${zone.maxPluviometersPerZone} registrati")
      Behaviors.same

  private def getZoneStatusHandler(zone: Zone, behavior: Zone => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, GetZoneStatus(replyTo)) =>
      ctx.log.info(s"Received zone status request from ${replyTo.path}")
      replyTo ! ZoneStatus(zone, ctx.self);
      behavior(zone)


  private def memberExited(): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberExitedAdapter(event)) =>
      ctx.log.info(s"Received MemberExitedAdapter, ${event.member.address} is exited")
      val actorRefToRemove = pluviometersRefs.keys.find(_.path.address == event.member.address)
      if actorRefToRemove.isDefined then pluviometersRefs.remove(actorRefToRemove.get)
      printPluvState(ctx)
      Behaviors.same

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

  private def resetAlarm(ctx: ActorContext[Message]) =
    for ((pluvRef, _) <- pluviometersRefs)
      pluvRef ! UnsetAlarm(ctx.self)
      pluviometersRefs(pluvRef) = false

  private def isZoneInAlarm2(zone: Zone) =
    zone.pluviometers.foldLeft(0) {
      case (count, (_, p)) => if p.waterLevel >= zone.maxWaterLevel then count + 1 else count
    } >= zone.pluviometers.size

  private def isZoneInAlarm =
    pluviometersRefs.foldLeft(0) {
      case (count, (_, value)) => if (value) count + 1 else count
    } >= pluviometersRefs.size
  //    } >= (pluviometers.size / 2)

  private def printPluvState(ctx: ActorContext[Message]) = ctx.log.info(s"pluviometers state: $pluviometersRefs")
