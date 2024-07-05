package utilsduringdebug

import akka.actor.typed.scaladsl.ActorContext
import actors.message.Message

object PrintInfo:
  def printActorInfo(intro: String, ctx: ActorContext[Message]): Unit =
//    ctx.log.info(s"Received context $ctx")
    ctx.log.info(s"$intro - Received context.self: ${ctx.self}")
    ctx.log.info(s"$intro - Received context.self.path: ${ctx.self.path}")

  def printActorInfoG(intro: String, ctx: ActorContext[Any]): Unit =
//    ctx.log.info(s"Received context $ctx")
    ctx.log.info(s"$intro - Received context.self: ${ctx.self}")
    ctx.log.info(s"$intro - Received context.self.path: ${ctx.self.path}")
