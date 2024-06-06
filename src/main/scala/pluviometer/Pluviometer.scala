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

  case class GetStatus(ref: ActorRef[Message]) extends Command

  case class SendWelcome(ref: ActorRef[Message]) extends Command

  case class UnsetAlarm(ref: ActorRef[Message]) extends Command

  private case class ConnectTo(ref: ActorRef[Message]) extends Command

  private case class SendDataToZone() extends Command

  private case class FindZone(zoneCode: String) extends Command


  def apply(name: String, zoneCode: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info(s"Pluviometer $name created")
    new Pluviometer(ctx, name, zoneCode).start
  }

private case class Pluviometer(ctx: ActorContext[Message], name: String, zoneCode: String):

  import Pluviometer.*
  import zone.*
  import Zone.PluviometerRegistered

  private val updateFrequency = FiniteDuration(3, "second")
  var count = 0

  private def start: Behavior[Message] =
    info("Entering start")
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(FindZone(zoneCode), FiniteDuration(5000, "milli"))
      info("Timer finding started")
      Behaviors.receiveMessagePartial {
        case FindZone(zone) =>
          ctx.spawnAnonymous(connectToZone(zone))
          Behaviors.same
        case ConnectTo(replyTo) =>
          timers.cancelAll()
          info(s"I'm ${ctx.self}")
          info("ConnectedTo msg receiver, starting worker actor")

          val actRef = ctx.spawn(work(replyTo), "WORK")

          info(s"Ref of new WORK Actor: $actRef")
          replyTo ! PluviometerRegistered(name, actRef)
          Behaviors.empty
      }
    }

  private def connectToZone(zoneCode: String) =
      Behaviors.setup { ctx2 =>
        val zoneServiceKey = ServiceKey[Message](zoneCode)
        ctx2.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx2.self)
        Behaviors.receiveMessagePartial {
          case zoneServiceKey.Listing(l) =>
            l.foreach(e =>
              ctx2.log.info(s"Element listing: $e")
              ctx.self ! ConnectTo(e)
            )
            Thread.sleep(2000)
            Behaviors.stopped
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
              timers.cancelAll()
//              this.alarm(workActorCtx, ref)
              this.alarm(ref)
            else
              workActorCtx.log.info("Send Something else")
              ref ! Zone.NoAlarm()
              Behaviors.same
        }
      }
    }

//  private def alarm(ctx: ActorContext[Message], ref: ActorRef[Message]): Behavior[Message] =
  private def alarm(ref: ActorRef[Message]): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendDataToZone(), updateFrequency)
//      ctx.log.info("Timer alarm started")
      Behaviors.receivePartial {
        case (ctx,SendDataToZone()) => ref ! Zone.Alarm(ctx.self); Behaviors.same
        case (ctx,UnsetAlarm(ref)) => ctx.log.info("unsetting alarm...");timers.cancelAll(); work(ref)
        case _ => Behaviors.same
      }
    }


  private def info(msg: String) = this.ctx.log.info(msg)

  private def printContextInfo(ctx: ActorContext[Message]) =
    println(s"Received context $ctx")
    println(s"Received context.self: ${ctx.self}")
    println(s"Received context.self.path: ${ctx.self.path}")



