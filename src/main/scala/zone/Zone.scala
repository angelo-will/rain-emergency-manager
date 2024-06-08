package zone

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.typed.{Cluster, Subscribe}
import message.Message
import zone.Zone.{Command, MemberExitedAdapter}

import scala.collection.mutable.Map


object Zone:
  sealed trait Command extends Message

  case class PluviometerRegistered(name: String, replyTo: ActorRef[Message]) extends Command

  case class Alarm(pluvRef: ActorRef[Message]) extends Command

  case class NoAlarm() extends Command

  case class UnderManagement() extends Command

  case class Solved() extends Command

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

  def apply(name: String): Behavior[Message] = Behaviors.setup { zoneCtx =>
    zoneCtx.spawn(memberEventBehavior(zoneCtx), "Member-event-actor")

    zoneCtx.log.info(s"Zone $name created")
    new Zone(name).creating
  }

private case class Zone(name: String):

  import Zone.*
  import pluviometer.Pluviometer.UnsetAlarm
  import scala.collection.mutable.Set

  private val pluviometers: Map[ActorRef[Message], Boolean] = Map()

  private def creating: Behavior[Message] =
    //    this.ctx.log.info("Entering creating")
    this.working

  private def working: Behavior[Message] =
    Behaviors.receivePartial {
      handlers(Behaviors.same)
        .orElse {
          case (ctx, Alarm(pluvRef)) =>
            ctx.log.info(s"RECEIVED ALARM from $pluvRef")
            ctx.log.info(s"RECEIVED ALARM from ${pluvRef.path.address}")
            pluviometers(pluvRef) = true
            printPluvState(ctx)
            if this.isZoneInAlarm then inAlarm else Behaviors.same
          case (ctx, NoAlarm()) =>
            ctx.log.info("RECEIVED NO ALARM")
            printPluvState(ctx)
            Behaviors.same
        }
    }


  private def inAlarm: Behavior[Message] =
    Behaviors.receivePartial {
      handlers(Behaviors.same)
        .orElse {
          case (ctx, UnderManagement()) =>
            ctx.log.info("Received under management")
            this.underManagement
        }
    }

  private def underManagement: Behavior[Message] =
    Behaviors.receivePartial {
      handlers(Behaviors.same)
        .orElse {
          case (ctx, Solved()) =>
            ctx.log.info("Received solved")
            this.resetAlarm(ctx)
            this.working
        }
    }

  private def pluvRegistered(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] = {
    case (ctx, PluviometerRegistered(name, replyTo)) =>
      ctx.log.info(s"New pluv connected with name $name")
      ctx.log.info(s"New pluv connected with ${replyTo.path.address}")
      pluviometers.put(replyTo, false)
      behavior
  }

  private def memberExited(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] = {
    case (ctx, MemberExitedAdapter(event)) =>
      val actorRefToRemove = pluviometers.keys.find(_.path.address == event.member.address)
      if actorRefToRemove.isDefined then pluviometers.remove(actorRefToRemove.get)
      printPluvState(ctx)
      behavior
  }

  private def handlers(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] = {
    pluvRegistered(behavior)
      .orElse(memberExited(behavior))
  }

  private def resetAlarm(ctx: ActorContext[Message]) =
    ctx.log.info(s"Sending unsetting alarm")
    for ((pluvRef, _) <- pluviometers)
      pluvRef ! UnsetAlarm(ctx.self)
      pluviometers(pluvRef) = false

  private def isZoneInAlarm =
    pluviometers.foldLeft(0) {
      case (count, (_, value)) => if (value) count + 1 else count
    } >= (pluviometers.size / 2)

  private def printPluvState(ctx: ActorContext[Message]) = ctx.log.info(s"pluviometers state: $pluviometers")
