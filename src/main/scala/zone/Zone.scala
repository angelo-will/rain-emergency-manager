package zone

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.typed.{Cluster, Subscribe}
import message.Message
import zone.Zone.{Command, MemberExitedAdapter}

import scala.collection.mutable.Map
import scala.concurrent.duration.FiniteDuration


object Zone:
  sealed trait Command extends Message
  case class PluviometerTryRegister(actorToRegister: ActorRef[Message], replyTo: ActorRef[Message]) extends Command
  case class PluviometerRegistered(zoneRef: ActorRef[Message]) extends Command
//  case class PluviometerRegistered(name: String, replyTo: ActorRef[Message]) extends Command

  /////
  case class GetStatus(fireSRef: ActorRef[Message]) extends Command

  ///// Message to inform condition from pluviometers
  case class Alarm(pluvRef: ActorRef[Message]) extends Command

  case class NoAlarm(pluvRef: ActorRef[Message]) extends Command

  ///// Messages from firestation
  case class UnderManagement() extends Command

  case class Solved() extends Command

  //////
  case class MemberExitedAdapter(event: MemberExited) extends Command

  enum ZoneState:
    case Alarm
    case Managing
    case Ok

  case class ZoneInfo(zoneState: ZoneState, pluvQuantity: Int) extends Command


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

  def apply(name: String, zoneCode: String, row: Int, column: Int): Behavior[Message] = Behaviors.setup { zoneCtx =>
    zoneCtx.spawn(memberEventBehavior(zoneCtx), "Member-event-actor")
    new Zone(name, zoneCode, row, column).creating
  }

private case class Zone(name: String, zoneCode: String, row: Int, column: Int):

  import Zone.*
  import pluviometer.Pluviometer.UnsetAlarm
  import scala.collection.mutable.Set
  import akka.actor.typed.receptionist.Receptionist

  private val pluviometers: Map[ActorRef[Message], Boolean] = Map()
  private val maxPluviometersConnected = 2

  private def creating: Behavior[Message] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Register(ServiceKey[Message](zoneCode), ctx.self)
    this.working

  }
  //    this.ctx.log.info("Entering creating")

  private def working: Behavior[Message] =
    Behaviors.receivePartial {
      handlers(Behaviors.same)
        .orElse(getStatus(Behaviors.same, ZoneState.Ok))
        .orElse {
          case (ctx, Alarm(pluvRef)) =>
            ctx.log.info(s"RECEIVED ALARM from ${pluvRef.path.address}")
            pluviometers(pluvRef) = true
            printPluvState(ctx)
            if this.isZoneInAlarm then
              // fireS ! ZoneInfo(ZoneState.Alarm, pluviometers.size)
              inAlarm
//            else
              
            Behaviors.same
          case (ctx, NoAlarm(pluvRef)) =>
            ctx.log.info(s"RECEIVED NO ALARM from ${pluvRef.path.address}")
            // fireS ! ZoneInfo(ZoneState.Ok, pluviometers.size)
            pluviometers(pluvRef) = false
            printPluvState(ctx)
            Behaviors.same
        }
    }


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
      handlers(Behaviors.same)
        .orElse(getStatus(Behaviors.same, ZoneState.Alarm))
        //        .orElse(receivedUpdates(Behaviors.same, ZoneState.Alarm))
        .orElse {
          case (ctx, UnderManagement()) =>
            ctx.log.info("Received under management")
            this.underManagement
        }
    }

  private def underManagement: Behavior[Message] =
    Behaviors.receivePartial {
      handlers(Behaviors.same)
        .orElse(getStatus(Behaviors.same, ZoneState.Managing))
        //        .orElse(receivedUpdates(Behaviors.same, ZoneState.Managing))
        .orElse {
          case (ctx, Solved()) =>
            ctx.log.info("Received solved")
            this.resetAlarm(ctx)
            this.working
        }
    }

  private def receivedUpdates(behavior: Behavior[Message], zoneState: ZoneState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, Alarm(pluvRef)) =>
      pluviometers(pluvRef) = true
      //fireStationRef ! zoneInfo(zoneState, pluviometers.size)
      Behaviors.same
    case (ctx, NoAlarm(pluvRef)) =>
      pluviometers(pluvRef) = false
      //fireStationRef ! zoneInfo(zoneState, pluviometers.size)
      Behaviors.same

  private def pluvRegistered(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
//    case (ctx, PluviometerRegistered(name, replyTo)) =>
//      ctx.log.info(s"New pluv connected with name $name")
//      ctx.log.info(s"New pluv connected with ${replyTo.path.address}")
//      pluviometers.put(replyTo, false)
    case _ => behavior

  private def pluvTryRegister(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, PluviometerTryRegister(actorToRegister, replyTo)) =>
      if pluviometers.size < maxPluviometersConnected then
        ctx.log.info(s"New pluv connected with name $name")
        ctx.log.info(s"New pluv connected with ${replyTo.path.address}")
        pluviometers.put(actorToRegister, false)
        replyTo ! PluviometerRegistered(ctx.self)
      else 
        ctx.log.info(s"Ricevuto messaggio di registrazione ma ci sono sià $maxPluviometersConnected registrati")
      behavior

  private def memberExited(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberExitedAdapter(event)) =>
      val actorRefToRemove = pluviometers.keys.find(_.path.address == event.member.address)
      if actorRefToRemove.isDefined then pluviometers.remove(actorRefToRemove.get)
      printPluvState(ctx)
      behavior

  private def getStatus(behavior: Behavior[Message], zoneState: ZoneState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, GetStatus(replyTo)) =>
      replyTo ! ZoneInfo(zoneState, pluviometers.size)
      behavior

  private def handlers(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    pluvTryRegister(behavior)
      .orElse(memberExited(behavior))

  private def resetAlarm(ctx: ActorContext[Message]) =
    //    ctx.log.info(s"Sending unsetting alarm")
    for ((pluvRef, _) <- pluviometers)
      //pluvRef ! UnsetAlarm(ctx.self)
      pluviometers(pluvRef) = false

  private def isZoneInAlarm =
    pluviometers.foldLeft(0) {
      case (count, (_, value)) => if (value) count + 1 else count
    } >= pluviometers.size
  //    } >= (pluviometers.size / 2)

  private def printPluvState(ctx: ActorContext[Message]) = ctx.log.info(s"pluviometers state: $pluviometers")
