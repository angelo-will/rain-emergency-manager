package actors.zone

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


  def apply(zone: Zone): Behavior[Message] = new ZoneActor().creating(zone)

private case class ZoneActor():

  import akka.cluster.typed.{Cluster, Subscribe}
  import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
  import akka.actor.typed.receptionist.ServiceKey
  import scala.collection.mutable
  import ZoneActor.*
  import actors.pluviometer.PluviometerActor
  import PluviometerActor.{Alarm, UnsetAlarm, PluviometerTryRegister, PluviometerStatus}
  import akka.actor.typed.receptionist.Receptionist
  import actors.commonbehaviors.MemberEventBehavior

  private val pluviometersRefs: mutable.Map[ActorRef[Message], String] = mutable.Map()

  private def creating(zone: Zone): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Register(ServiceKey[Message](zone.zoneCode), ctx.self)
    ctx.spawn(MemberEventBehavior.memberExitBehavior(ctx), "Member-event-actor")
    this.working(zone)
  }

  private def working(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone, working)
        .orElse(getZoneStatusHandler(zone, working))
        .orElse(memberExited(zone, working))
        .orElse {
          case (ctx, PluviometerStatus(pluv, pluvRef)) =>
            ctx.log.info(s"Inside working, zone: $zone")
            ctx.log.info(s"Received pluviometer: $pluv")
            val newPluviometers = zone.pluviometers + ((pluv.pluvCode, pluv))
            if isZoneInAlarm(zone.copy(pluviometers = newPluviometers)) then
              pluvRef ! PluviometerActor.Alarm(ctx.self)
              inAlarm(zone.copy(zoneState = ZoneAlarm(), pluviometers = newPluviometers))
            else
              pluvRef ! PluviometerActor.UnsetAlarm(ctx.self)
              working(zone.copy(zoneState = ZoneOk(), pluviometers = newPluviometers))
        }
    }

  private def pluvStatusUpdates(zone: Zone, behavior: Zone => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerStatus(pluviometer, pluvRef)) =>
      ctx.log.info(s"Inside $behavior")
      ctx.log.info(s"zone $zone")
      ctx.log.info(s"Received pluviometer: $pluviometer")
      val newPluviometers = zone.pluviometers + ((pluviometer.pluvCode, pluviometer.copy(pluviometerState = PluviometerAlarm())))
      zone.zoneState match
        case ZoneAlarm() | ZoneInManaging() =>
          ctx.log.info(s"Saying to ${pluviometer.pluvCode} with value ${pluviometer.waterLevel} to go in alarm")
          pluvRef ! PluviometerActor.Alarm(ctx.self)
          behavior(zone.copy(pluviometers = newPluviometers))
        case _ => Behaviors.same


  private def inAlarm(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone, inAlarm)
        .orElse(pluvStatusUpdates(zone, inAlarm))
        .orElse(memberExited(zone, inAlarm))
        .orElse(getZoneStatusHandler(zone, inAlarm))
        .orElse {
          case (ctx, UnderManagement(fireSRef)) => underManagement(zone.copy(zoneState = ZoneInManaging()))
        }
    }

  private def ignoringPluvStatusAfterSolved(cycle:Int)(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone, ignoringPluvStatusAfterSolved(cycle))
        .orElse(memberExited(zone, ignoringPluvStatusAfterSolved(cycle)))
        .orElse(getZoneStatusHandler(zone, ignoringPluvStatusAfterSolved(cycle)))
        .orElse {
          case (ctx, PluviometerStatus(pluviometer, pluvRef)) =>
            ctx.log.info(s"Inside ignoringPluvStatusAfterSolved received pluviometer: $pluviometer")
            ctx.log.info(s"Inside ignoringPluvStatusAfterSolved cycle: $cycle")
            val newPluviometers = zone.pluviometers + ((pluviometer.pluvCode, pluviometer))
            if cycle > 0 then
              ignoringPluvStatusAfterSolved(cycle-1)(zone.copy(pluviometers = newPluviometers, zoneState = ZoneOk()))
            else
              working(zone.copy(pluviometers = newPluviometers, zoneState = ZoneOk()))
        }
    }

  private def underManagement(zone: Zone): Behavior[Message] =
    Behaviors.receivePartial {
      pluvTryRegister(zone, underManagement)
        .orElse(pluvStatusUpdates(zone, underManagement))
        .orElse(memberExited(zone, underManagement))
        .orElse(getZoneStatusHandler(zone, underManagement))
        .orElse {
          case (ctx, Solved(fireSRef)) =>
            ctx.log.info("Received solved")
            this.resetAlarm(ctx)
//            working(zone.copy(zoneState = ZoneOk()))
            ignoringPluvStatusAfterSolved(zone.maxPluviometersPerZone*2)(zone.copy(zoneState = ZoneOk()))

        }
    }

  private def pluvTryRegister(zone: Zone, behavior: Zone => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerTryRegister(pluviometer, actorToRegister)) =>
      if pluviometersRefs.size < zone.maxPluviometersPerZone then
        ctx.log.info(s"New pluv connected with code ${pluviometer.pluvCode}")
        ctx.log.info(s"New pluv connected with ${actorToRegister.path.address}")
        pluviometersRefs(actorToRegister) = pluviometer.pluvCode
        actorToRegister ! ElementConnectedAck(ctx.self)
        behavior(zone.copy(pluviometers = zone.pluviometers + ((pluviometer.pluvCode, pluviometer))))
      else
        ctx.log.info(s"Ricevuto messaggio di registrazione ma ci sono sià ${zone.maxPluviometersPerZone} registrati")
        behavior(zone)

  private def getZoneStatusHandler(zone: Zone, behavior: Zone => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, GetZoneStatus(replyTo)) =>
      ctx.log.info(s"Received zone status request from ${replyTo.path}")
      ctx.log.info(s"i'm sending zone: ${zone}")
      replyTo ! ZoneStatus(zone, ctx.self);
      behavior(zone)


  private def memberExited(zone: Zone, behavior: Zone => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberEventBehavior.MemberExit(address)) =>
      ctx.log.info(s"Received MemberExitedAdapter, $address is exited")
      val actorRefToRemove = pluviometersRefs.keys.find(_.path.address == address)
      if actorRefToRemove.isDefined then
        val newPluviometers = zone.pluviometers - pluviometersRefs(actorRefToRemove.get)
        pluviometersRefs.remove(actorRefToRemove.get)
        printPluvState(ctx)
        behavior(zone.copy(pluviometers = newPluviometers))
      else
        behavior(zone)


  private def resetAlarm(ctx: ActorContext[Message]): Unit =
    for ((pluvRef, _) <- pluviometersRefs)
      pluvRef ! UnsetAlarm(ctx.self)

  private def isZoneInAlarm(zone: Zone) =
    val m = math.ceil(zone.pluviometers.size.toDouble / 2.0)
    val c = zone.pluviometers.foldLeft(0) {
      case (count, (_, p)) => if p.waterLevel >= zone.maxWaterLevel then count + 1 else count
    }
    c >= m


  private def printPluvState(ctx: ActorContext[Message]): Unit = ctx.log.info(s"pluviometers state: $pluviometersRefs")
