package actors.pluviometer

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
    new PluviometerActor(pluviometer).connectToZone

private case class PluviometerActor(pluviometer: Pluviometer):

  import PluviometerActor.*
  import actors.zone.ZoneActor.ElementConnectedAck
  import message.Message
  import systemelements.SystemElements.PluviometerState

  private val searchZoneFrequency = FiniteDuration(5000, "milli")
  private val updateFrequency = FiniteDuration(10, "second")

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

  private def notAlarm(pluviometer: Pluviometer): Behavior[Message] = Behaviors.receivePartial {
    handlerSendDataToZone(pluviometer, PluviometerNotAlarm())
      .orElse {
        case (ctx, Alarm(zoneRef)) =>
          ctx.log.info(s"Received alarm to zone, passing to alarm")
          alarm(pluviometer)
        case (ctx, msg) =>
          ctx.log.info(s"In NOT alarm received $msg")
          notAlarm(pluviometer)
      }
  }

  private def alarm(pluviometer: Pluviometer): Behavior[Message] = Behaviors.receivePartial {
    handlerSendDataToZone(pluviometer, PluviometerAlarm())
      .orElse {
        case (ctx, UnsetAlarm(zoneRef)) =>
          ctx.log.info(s"Received unset alarm to zone, passin to not alarm")
          val newPluviometer = pluviometer.copy(waterLevel = Random.nextInt(100), pluviometerState = PluviometerNotAlarm())
          zoneRef ! PluviometerStatus(newPluviometer, ctx.self)
          notAlarm(newPluviometer)
        case (ctx, msg) =>
          ctx.log.info(s"In alarm received $msg")
          alarm(pluviometer)
      }
  }

  private def updateZoneJob(ref: ActorRef[Message], pluviometer: Pluviometer): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendDataToZone(ref), updateFrequency)
      notAlarm(pluviometer)
    }

  private def handlerSendDataToZone(pluviometer: Pluviometer, pluvNewState: PluviometerState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, SendDataToZone(zoneRef)) =>
      ctx.log.info(s"Received SendDataToZone from zone")
      // That's emulate registration data by sensor
      val newPluviometer = pluviometer.copy(waterLevel = Random.nextInt(400),pluviometerState = pluvNewState)
      zoneRef ! PluviometerStatus(newPluviometer, ctx.self)
      pluvNewState match
        case PluviometerNotAlarm() => notAlarm(newPluviometer)
        case PluviometerAlarm() => alarm(newPluviometer)


  private def info(ctx: ActorContext[Message])(msg: String) = ctx.log.info(msg)

  private def printContextInfo(ctx: ActorContext[Message]) =
    println(s"Received context $ctx")
    println(s"Received context.self: ${ctx.self}")
    println(s"Received context.self.path: ${ctx.self.path}")



