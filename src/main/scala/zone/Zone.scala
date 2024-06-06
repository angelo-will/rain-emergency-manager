package zone

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import message.Message
import zone.Zone.Command

import scala.collection.mutable.Map


object Zone:
  sealed trait Command extends Message

  case class Alarm(pluvRef: ActorRef[Message]) extends Command

  case class NewPluvConnected(name: String, replyTo: ActorRef[Message]) extends Command

  case class SendData() extends Command

  case class NoAlarm() extends Command

  def apply(name: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info(s"Zone $name created")
    new Zone(ctx, name).creating
  }

private case class Zone(ctx: ActorContext[Message], name: String):

  import Zone.*
  import pluviometer.Pluviometer.UnsetAlarm
  import firestastion.FireStation.DeactivateAlarm
  import scala.collection.mutable.Set

  private val pluviometers: Map[ActorRef[Message], Boolean] = Map()

  private def creating: Behavior[Message] =
    this.ctx.log.info("Entering creating")
    this.working

  private def working: Behavior[Message] =
    this.ctx.log.info("Entering working")
    var alarmCount = 0
    Behaviors.receivePartial {
      case (ctx, Alarm(pluvRef)) =>
        ctx.log.info("RECEIVED ALARM")
        pluviometers(pluvRef) = true
        alarmCount = alarmCount + 1
        if alarmCount >= 3 then
          resetAlarm
          alarmCount = 0
        printPluvState
        Behaviors.same
      case (ctx, NoAlarm()) =>
        ctx.log.info("RECEIVED NO ALARM")
        printPluvState
        Behaviors.same
      case (ctx, NewPluvConnected(name, replyTo)) =>
        ctx.log.info(s"New pluv connected with name $name")
        pluviometers.put(replyTo, false)
        Behaviors.same
    }

  private def resetAlarm =
    this.ctx.log.info(s"Sending unsetting alarm")
    for ((pluvRef, _) <- pluviometers)
      pluvRef ! UnsetAlarm(ctx.self)
      pluviometers(pluvRef) = false



  private def printPluvState = this.ctx.log.info(s"pluviometers state: $pluviometers")
