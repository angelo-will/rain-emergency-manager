package pluviometer

import systemelements.SystemElements.Pluviometer
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.*
import akka.actor.typed.{ActorRef, Behavior}
import message.Message
import systemelements.SystemElements.PluviometerState.NotAlarm

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
  import zone.*
  import ZoneActor.ElementConnectedAck
  import message.Message
  import systemelements.SystemElements.PluviometerState

  private val searchZoneFrequency = FiniteDuration(5000, "milli")
  private val updateFrequency = FiniteDuration(3, "second")

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
            timers.cancelAll()
            updateZoneJob(zoneRef, pluviometer)
        }
      }
    }

  private def notAlarm(pluviometer: Pluviometer): Behavior[Message] = Behaviors.receivePartial {
    handlerSendDataToZone(pluviometer, PluviometerState.NotAlarm, Behaviors.same)
      .orElse {
        case (ctx, Alarm(zoneRef)) =>
          alarm(pluviometer)
      }
  }

  private def alarm(pluviometer: Pluviometer): Behavior[Message] = Behaviors.receivePartial {
    handlerSendDataToZone(pluviometer, PluviometerState.Alarm, Behaviors.same)
      .orElse {
        case (ctx, UnsetAlarm(zoneRef)) =>
          notAlarm(pluviometer)
      }
  }

  private def updateZoneJob(ref: ActorRef[Message], pluviometer: Pluviometer): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendDataToZone(ref), updateFrequency)
      notAlarm(pluviometer)
    }

  private def handlerSendDataToZone(pluviometer: Pluviometer, pluvNewState: PluviometerState, behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, SendDataToZone(zoneRef)) => zoneRef ! PluviometerStatus(buildNewPluvWithState(pluviometer, pluvNewState), ctx.self); behavior
  //    behavior

  private def buildNewPluvWithState(pluviometer: Pluviometer, pluvState: PluviometerState) =
    Pluviometer(
      pluviometer.pluvCode,
      pluviometer.zoneCode,
      pluviometer.position,
      pluvState,
      Random.nextInt(200)
    )


  private def info(ctx: ActorContext[Message])(msg: String) = ctx.log.info(msg)

  private def printContextInfo(ctx: ActorContext[Message]) =
    println(s"Received context $ctx")
    println(s"Received context.self: ${ctx.self}")
    println(s"Received context.self.path: ${ctx.self.path}")



