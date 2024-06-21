package pluviometer

import akka.actor.PoisonPill
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorRef, Behavior}
import message.Message
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

object Pluviometer:
  sealed trait Command extends Message

  private case class FindZone(zoneCode: String) extends Command

  private case class ConnectTo(ref: ActorRef[Message]) extends Command

  private case class SendDataToZone() extends Command

  private case class Start(zoneRef: ActorRef[Message]) extends Command

  case class UnsetAlarm(ref: ActorRef[Message]) extends Command

  def apply(name: String, zoneCode: String, coordX: Int, coordY: Int): Behavior[Message] =
    new Pluviometer(name, zoneCode, coordX, coordY).idle

private case class Pluviometer(name: String, zoneCode: String, coordX: Int, coordY: Int):

  import Pluviometer.*
  import zone.*
  import Zone.ElementConnectedAck
  import Zone.PluviometerTryRegister

  private val updateFrequency = FiniteDuration(3, "second")

  private def idle: Behavior[Message] = Behaviors.setup { ctx =>
    val workerActor = ctx.spawn(notPaired(), "WORK")
    ctx.spawnAnonymous(connectToZone(zoneCode, ctx.self, workerActor))
    Behaviors.receivePartial {
      case (ctx, ConnectTo(zoneRef)) =>
        val info = this.info(ctx)
        info(s"I'm ${ctx.self}")
        info("ConnectedTo msg receiver, starting worker actor")
        workerActor ! Start(zoneRef)
        Behaviors.empty
    }
  }

  private def connectToZone(zoneCode: String, parentRef: ActorRef[Message], actorToRegister: ActorRef[Message]) =
    Behaviors.setup { ctx =>
      ctx.log.info("Start actor for search zone")
      Behaviors.withTimers { timers =>
        val zoneServiceKey = ServiceKey[Message](zoneCode)
        timers.startTimerAtFixedRate(FindZone(zoneCode), FiniteDuration(5000, "milli"))
        Behaviors.receiveMessagePartial {
          case FindZone(zoneCode) =>
            ctx.log.info(s"Timer tick, i'm trying to connect to zone $zoneCode")
            ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.self)
            Behaviors.same
          //            Behaviors.receiveMessagePartial {
          case zoneServiceKey.Listing(l) =>
            l.head ! PluviometerTryRegister(actorToRegister, ctx.self)
            Behaviors.same
          case ElementConnectedAck(zoneRef) =>
            parentRef ! ConnectTo(zoneRef)
            Thread.sleep(2000)
            Behaviors.stopped
          //            }
        }
      }
    }

  private def notPaired(): Behavior[Message] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case Start(zoneRef) => work(zoneRef)
      }
    }

  private def work(ref: ActorRef[Message]): Behavior[Message] =
    Behaviors.setup { workActorCtx =>
      workActorCtx.log.info(s"Inside work setup with ref: ${workActorCtx.self} ")
      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(SendDataToZone(), updateFrequency)
        workActorCtx.log.info("Timer setted")
        Behaviors.receiveMessagePartial {
          case SendDataToZone() =>
            workActorCtx.log.info("SendDataToZone received")
            if (new Random).nextBoolean() then
              workActorCtx.log.info("Send Alarm")
              ref ! Zone.Alarm(workActorCtx.self)
              Behaviors.same
            else
              workActorCtx.log.info("Send Something else")
              ref ! Zone.NoAlarm(workActorCtx.self)
              Behaviors.same
        }
      }
    }

  private def alarm(ref: ActorRef[Message]): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendDataToZone(), updateFrequency)
      Behaviors.receivePartial {
        case (ctx, SendDataToZone()) =>
          ctx.log.info("IN-ALARM sending alarm...")
          ref ! Zone.Alarm(ctx.self)
          Behaviors.same
        case (ctx, UnsetAlarm(ref)) => ctx.log.info("unsetting alarm..."); timers.cancelAll(); work(ref)
        case _ => Behaviors.same
      }
    }

  private def info(ctx: ActorContext[Message])(msg: String) = ctx.log.info(msg)

  private def printContextInfo(ctx: ActorContext[Message]) =
    println(s"Received context $ctx")
    println(s"Received context.self: ${ctx.self}")
    println(s"Received context.self.path: ${ctx.self.path}")