package zone

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import message.Message
import zone.Zone.Command

import scala.collection.mutable.Map


object Zone:
  sealed trait Command extends Message

  case class PluviometerRegistered(name: String, replyTo: ActorRef[Message]) extends Command

  case class Alarm(pluvRef: ActorRef[Message]) extends Command

  case class NoAlarm() extends Command

  case class UnderManagement() extends Command

  case class Solved() extends Command

  def apply(name: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info(s"Zone $name created")
    new Zone(ctx, name).creating
  }

private case class Zone(ctx: ActorContext[Message], name: String):

  import Zone.*
  import pluviometer.Pluviometer.UnsetAlarm
  import scala.collection.mutable.Set

  private val pluviometers: Map[ActorRef[Message], Boolean] = Map()

  private def creating: Behavior[Message] =
    this.ctx.log.info("Entering creating")
    this.working

  private def working: Behavior[Message] =
    //    this.ctx.log.info("Entering working")
    //    var alarmCount = 0
    //
    //    // To remove in the end
    //    def debugAlarmCounter(): Unit = {
    //      alarmCount = alarmCount + 1
    //      if alarmCount >= 3 then
    //        resetAlarm
    //        alarmCount = 0
    //    }

    Behaviors.receivePartial {
      pluvRegistered(Behaviors.same).orElse {
        case (ctx, Alarm(pluvRef)) =>
          ctx.log.info("RECEIVED ALARM")
          pluviometers(pluvRef) = true
          //          debugAlarmCounter()
          printPluvState
          if this.isZoneInAlarm then inAlarm else Behaviors.same
        case (ctx, NoAlarm()) =>
          ctx.log.info("RECEIVED NO ALARM")
          printPluvState
          Behaviors.same
      }
    }


  private def inAlarm: Behavior[Message] =
    Behaviors.receivePartial {
      pluvRegistered(Behaviors.same).orElse {
        case (ctx, UnderManagement()) =>
          ctx.log.info("Received under management")
          this.underManagement
      }
    }

  private def underManagement: Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, Solved()) =>
        ctx.log.info("Received solved")
        this.resetAlarm
        this.working
    }

  private def pluvRegistered(behavior: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] = {
    case (ctx, PluviometerRegistered(name, replyTo)) =>
      ctx.log.info(s"New pluv connected with name $name")
      pluviometers.put(replyTo, false)
      behavior
  }

  private def resetAlarm =
    this.ctx.log.info(s"Sending unsetting alarm")
    for ((pluvRef, _) <- pluviometers)
      pluvRef ! UnsetAlarm(ctx.self)
      pluviometers(pluvRef) = false

  private def isZoneInAlarm =
    pluviometers.foldLeft(0) {
      case (count, (_, value)) => if (value) count + 1 else count
    } >= (pluviometers.size / 2)

  private def printPluvState = this.ctx.log.info(s"pluviometers state: $pluviometers")
