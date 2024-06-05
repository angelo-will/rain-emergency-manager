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

  private case class FindZone() extends Command


  def apply(name: String, zoneCode: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info(s"Pluviometer $name created")
    new Pluviometer(ctx, name, zoneCode).start
  }

class Pluviometer(ctx: ActorContext[Message], name: String, zoneCode: String):

  import Pluviometer.*
  import zone.*
  import Zone.NewPluvConnected

  private val updateFrequency = FiniteDuration(3, "second")
  var count = 0

  private val start: Behavior[Message] =
    info("Entering start")
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(FindZone(), FiniteDuration(5000, "milli"))
      info("Timer finding started")
      Behaviors.receiveMessagePartial {
        case FindZone() =>
          ctx.spawnAnonymous[Receptionist.Listing](
            Behaviors.setup { ctx2 =>
              val zoneServiceKey = ServiceKey[Message](zoneCode)
              ctx2.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx2.self)
              Behaviors.receiveMessagePartial[Receptionist.Listing] {
                case zoneServiceKey.Listing(l) =>
                  l.foreach(e =>
                    ctx2.log.info(s"Element listing: $e")
                    ctx.self ! ConnectTo(e)
                  )
                  Thread.sleep(2000)
                  Behaviors.stopped
              }
            })
          Behaviors.same
        case ConnectTo(replyTo) =>
          timers.cancelAll()
          info(s"I'm ${ctx.self}")
          info("ConnectedTo msg receiver, starting worker actor")

          val actRef = ctx.spawn(work(replyTo), "WORK")

          info(s"Ref of new WORK Actor: $actRef")
          replyTo ! NewPluvConnected(name, actRef)
          //          work(replyTo)
          Behaviors.empty
      }
    }

  private def work(ref: ActorRef[Message]): Behavior[Message] =
    //    println(s"work received context ${ctx}")
    //    println(s"work received context.self: ${ctx.self}")
    //    println(s"work received context.self.path: ${ctx.self.path}")
    //    info(s"Entering in work with contextref ${ctx.self}")
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
              ref ! Zone.Alarm()
              timers.cancelAll()
              this.alarm(workActorCtx, ref)
            else
              workActorCtx.log.info("Send Something else")
              ref ! Zone.NoAlarm()
              Behaviors.same
        }
      }
    }

  private def alarm(ctx: ActorContext[Message], ref: ActorRef[Message]): Behavior[Message] =
    //    ctx.log.info("Entering Alarm")
    //    Behaviors.receive { (context, message) =>
    //      context.log.info(s"Received a message: $message")
    //      context.log.info(s"I'm context: $context")
    //      context.log.info(s"I'm context.self: ${context.self}")
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendDataToZone(), updateFrequency)
      ctx.log.info("Timer alarm started")
      Behaviors.receiveMessagePartial {
        case SendDataToZone() => ref ! Zone.Alarm(); Behaviors.same
        case UnsetAlarm(ref) => timers.cancelAll(); work(ref)
        case _ => Behaviors.same
      }
    }
  //    }


  private def info(msg: String) = this.ctx.log.info(msg)


