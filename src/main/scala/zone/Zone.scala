package zone

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import message.Message
import zone.Zone.Command

import scala.collection.mutable.Set


object Zone:
  sealed trait Command extends Message

  case class Alarm() extends Command

  case class NewPluvConnected(name: String, replyTo: ActorRef[Message]) extends Command

  case class SendData() extends Command

  case class NoAlarm() extends Command

  def apply(name: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info(s"Zone $name created")
    new Zone(ctx, name).creating
  }

class Zone(ctx: ActorContext[Message], name: String):

  import Zone.*
  import pluviometer.Pluviometer.UnsetAlarm

  val pluviometers: scala.collection.mutable.Set[ActorRef[Message]] = Set()

  private val creating: Behavior[Message] =
    this.ctx.log.info("Entering creating")
    this.working
  //    Behaviors.receiveMessagePartial {
  //      case Alarm() => this.working
  //    }

  private lazy val working: Behavior[Message] =
    this.ctx.log.info("Entering working")
    var alarmCount = 0
    Behaviors.receiveMessagePartial {
      case Alarm() =>
        this.ctx.log.info("RECEIVED ALARM")
        alarmCount = alarmCount + 1
        //        if alarmCount >= 3 then
//        ctx.log.info("Sending unsetting alarm")
        pluviometers.foreach(pluv =>
          ctx.log.info(s"Sending unset alarm to $pluv")
          pluv ! UnsetAlarm(ctx.self))
        //          alarmCount = 0
        Behaviors.same
      case NoAlarm() =>
        this.ctx.log.info("RECEIVED NO ALARM")
        Behaviors.same
      case NewPluvConnected(name, replyTo) =>
        this.ctx.log.info(s"New pluv connected with name $name")
        pluviometers.add(replyTo)
        Behaviors.same
    }

