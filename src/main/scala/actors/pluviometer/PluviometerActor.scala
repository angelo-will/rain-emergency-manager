package actors.pluviometer

import actors.commonbehaviors.MemberEventBehavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.*
import akka.actor.typed.{ActorRef, Behavior}
import message.Message
import systemelements.SystemElements.{Pluviometer, PluviometerAlarm, PluviometerNotAlarm}

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object PluviometerActor:
  sealed trait Command extends Message

  private case class FindZone(zoneCode: String) extends Command

  private case class SendDataToZone(zoneRef: ActorRef[Message]) extends Command

  private case class AdapterMessageForConnection(listing: Receptionist.Listing) extends Command

  case class PluviometerTryRegister(pluviometer: Pluviometer, actorToRegister: ActorRef[Message]) extends Command

  case class PluviometerStatus(pluviometer: Pluviometer, pluvRef: ActorRef[Message]) extends Command

  case class Alarm(zoneRef: ActorRef[Message]) extends Command

  case class UnsetAlarm(zoneRef: ActorRef[Message]) extends Command

  def apply(pluviometer: Pluviometer): Behavior[Message] =
    new PluviometerActor(pluviometer).starting

private case class PluviometerActor(pluviometer: Pluviometer):

  import PluviometerActor.*
  import actors.zone.ZoneActor.ElementConnectedAck
  import message.Message
  import systemelements.SystemElements.PluviometerState

  private val searchZoneFrequency = FiniteDuration(5000, "milli")
  private val updateFrequency = FiniteDuration(3, "second")

  private def starting: Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(MemberEventBehavior.memberExitBehavior(ctx), s"${pluviometer.pluvCode}-member-event")
    connectToZone
  }

  private def connectToZone: Behavior[Message] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        val zoneServiceKey = ServiceKey[Message](pluviometer.zoneCode)
        timers.startTimerAtFixedRate(FindZone(pluviometer.zoneCode), searchZoneFrequency)
        Behaviors.receiveMessagePartial {
          case FindZone(zoneCode) =>
            ctx.log.info(s"Timer tick, i'm trying to connect to zone $zoneCode")
            ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.messageAdapter[Receptionist.Listing](AdapterMessageForConnection.apply))
            Behaviors.same
          case AdapterMessageForConnection(zoneServiceKey.Listing(l)) =>
            if l.nonEmpty then l.head ! PluviometerTryRegister(pluviometer, ctx.self)
            Behaviors.same
          case ElementConnectedAck(zoneRef) =>
            ctx.log.info(s"Received ElementConnectedAck pass updateZoneJob")
            timers.cancelAll()
            updateZoneJob(zoneRef, pluviometer)
        }
      }
    }

  private def notAlarm(
                        pluviometer: Pluviometer,
                        zoneRef: ActorRef[Message],
                        timerScheduler: TimerScheduler[Message]
                      ): Behavior[Message] =
    Behaviors.receivePartial {
      handlerSendDataToZone(pluviometer.copy(pluviometerState = PluviometerNotAlarm()))(p => notAlarm(p, zoneRef, timerScheduler))
        .orElse(handlerZoneDisconnected(zoneRef, timerScheduler))
        .orElse {
          case (ctx, Alarm(zoneRef)) =>
            ctx.log.info(s"Received alarm to zone, passing to alarm")
            alarm(pluviometer, zoneRef, timerScheduler)
          case (ctx, UnsetAlarm(_)) => Behaviors.same
        }
    }

  private def alarm(
                     pluviometer: Pluviometer,
                     zoneRef: ActorRef[Message],
                     timerScheduler: TimerScheduler[Message]
                   ): Behavior[Message] =
    Behaviors.receivePartial {
      handlerSendDataToZone(pluviometer.copy(pluviometerState = PluviometerAlarm()))(p => alarm(p, zoneRef, timerScheduler))
        .orElse(handlerZoneDisconnected(zoneRef, timerScheduler))
        .orElse {
          case (ctx, UnsetAlarm(zoneRef)) =>
            ctx.log.info(s"Received unset alarm to zone, passin to not alarm")
            val newPluviometer = pluviometer.copy(waterLevel = Random.nextInt(100), pluviometerState = PluviometerNotAlarm())
            zoneRef ! PluviometerStatus(newPluviometer, ctx.self)
            notAlarm(newPluviometer, zoneRef, timerScheduler)
          case (ctx, Alarm(_)) => Behaviors.same
        }
    }

  private def updateZoneJob(ref: ActorRef[Message], pluviometer: Pluviometer): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendDataToZone(ref), updateFrequency)
      notAlarm(pluviometer, ref, timers)
    }

  private def handlerSendDataToZone(pluviometer: Pluviometer)(newBehavior: Pluviometer => Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, SendDataToZone(zoneRef)) =>
      ctx.log.info(s"Received SendDataToZone")
      // That's emulate registration data by sensor
      val newPluviometer = pluviometer.copy(waterLevel = Random.nextInt(400))
      ctx.log.info(s"Sending to zone $newPluviometer")
      zoneRef ! PluviometerStatus(newPluviometer, ctx.self)
      newBehavior(newPluviometer)

  private def handlerZoneDisconnected(zoneRef: ActorRef[Message], timerScheduler: TimerScheduler[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberEventBehavior.MemberExit(address)) =>
      ctx.log.info(s"Received MemberExit")
      if zoneRef.path.address == address then
        timerScheduler.cancelAll()
        ctx.log.info(s"Zone disconnected, returning in connectToZone")
        connectToZone
      else
        Behaviors.same


  private def info(ctx: ActorContext[Message])(msg: String) = ctx.log.info(msg)

  private def printContextInfo(ctx: ActorContext[Message]) =
    println(s"Received context $ctx")
    println(s"Received context.self: ${ctx.self}")
    println(s"Received context.self.path: ${ctx.self.path}")



