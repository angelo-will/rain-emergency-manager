package actors.pluviometer

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.*
import akka.actor.typed.{ActorRef, Behavior}
import actors.message.Message
import systemelements.SystemElements.Pluviometer


object PluviometerActor:

  /**
   * Sealed trait representing a command that the PluviometerActor can handle.
   */
  sealed trait Command extends Message

  /**
   * Internal message to find a zone based on its code.
   *
   * @param zoneCode the code of the zone to find.
   */
  private case class FindZone(zoneCode: String) extends Command

  /**
   * Internal message to notify to send data to a zone.
   *
   * @param zoneRef the reference to the zone actor to send data to.
   */
  private case class SendDataToZone(zoneRef: ActorRef[Message]) extends Command

  /**
   * Internal message to handle adapter messages for connection.
   *
   * @param listing the listing of actors from the receptionist.
   */
  private case class AdapterMessageForConnection(listing: Receptionist.Listing) extends Command

  /**
   * Message to try registering a pluviometer.
   *
   * @param pluviometer     the pluviometer to register.
   * @param actorToRegister the reference to the actor to register.
   */
  case class PluviometerTryRegister(pluviometer: Pluviometer, actorToRegister: ActorRef[Message]) extends Command

  /**
   * Message representing the status of a pluviometer.
   *
   * @param pluviometer the pluviometer whose status is being reported.
   * @param pluvRef     the reference to the pluviometer actor.
   */
  case class PluviometerStatus(pluviometer: Pluviometer, pluvRef: ActorRef[Message]) extends Command

  /**
   * Message to make pluviometer enter in alarm state.
   *
   * @param zoneRef the reference of the zone actor that notifies it.
   */
  case class Alarm(zoneRef: ActorRef[Message]) extends Command

  /**
   * Message to make pluviometer exit from alarm state.
   *
   * @param zoneRef the reference of the zone actor that notifies it.
   */
  case class UnsetAlarm(zoneRef: ActorRef[Message]) extends Command

  def apply(pluviometer: Pluviometer): Behavior[Message] =
    new PluviometerActor(pluviometer).starting

private case class PluviometerActor(pluviometer: Pluviometer):

  import akka.actor.typed.receptionist.ServiceKey
  import scala.concurrent.duration.FiniteDuration
  import scala.util.Random

  import systemelements.SystemElements.{PluviometerState, PluviometerAlarm, PluviometerNotAlarm}

  import PluviometerActor.*

  import actors.commonbehaviors.MemberEventBehavior
  import actors.zone.ZoneActor.ElementConnectedAck

  private val searchZoneFrequency = FiniteDuration(5000, "milli")
  private val updateFrequency = FiniteDuration(3, "second")

  private val valuesBeforeUpdate = 5

  private var sensorValue = Random.nextInt(100)
  private var updateCounter = 0

  private def starting: Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(MemberEventBehavior.memberExitBehavior(ctx), s"${pluviometer.pluvCode}-member-event")
    connectToZone
  }

  private def connectToZone: Behavior[Message] =
    Behaviors.setup { ctx =>
      ctx.setLoggerName(s"actors-pluviometer-${pluviometer.pluvCode}-in-${pluviometer.zoneCode}")
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
            ctx.log.info(s"Received unset alarm from zone, passing to not alarm")
            ////JUST FOR TEST//////
            updateCounter = 0
            sensorValue = 0
            updateTestCounter()
            ///////////////////////
            val newPluviometer = pluviometer.copy(waterLevel = sensorValue, pluviometerState = PluviometerNotAlarm())
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
      ////JUST FOR TEST//////
      updateTestCounter()
      ///////////////////////
      // That's emulate registration data by sensor
      val newPluviometer = pluviometer.copy(waterLevel = sensorValue)
      ctx.log.info(s"Sending to zone $newPluviometer")
      zoneRef ! PluviometerStatus(newPluviometer, ctx.self)
      newBehavior(newPluviometer)

  private def updateTestCounter(): Unit = {
    updateCounter += 1
    if updateCounter >= valuesBeforeUpdate then
      updateCounter = 0
      sensorValue = Random.nextInt(400)
  }

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



