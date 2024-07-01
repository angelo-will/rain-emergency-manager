package actors.commonbehaviors

import akka.actor.Address
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.ClusterEvent.{MemberEvent, MemberExited}
import akka.cluster.typed.{Cluster, Subscribe}
import message.Message

import scala.reflect.ClassTag

object MemberEventBehavior:
  sealed trait Command extends Message

  private case class MemberEventAdapter(event: MemberEvent) extends Command

  case class MemberExit(address: Address) extends Command

  def memberExitBehavior(actorToNotify: ActorContext[Message]): Behavior[Message] =
    memberExitBehavior(actorToNotify, address => MemberExit(address))

  def memberExitBehavior(actorToNotify: ActorContext[Message], msgToSend: Address => Message): Behavior[Message] =
    memberEventBehavior[MemberExited](actorToNotify, msgToSend)

  private def memberEventBehavior[T <: MemberEvent](using ct: ClassTag[T])(actorToNotify: ActorContext[Message], msgToSend: Address => Message): Behavior[Message] =
    Behaviors.setup { ctx2 =>
      Cluster(actorToNotify.system).subscriptions ! Subscribe(
        ctx2.messageAdapter[T](MemberEventAdapter.apply),
        // Instead of ClassOf[T] because generic
        ct.runtimeClass.asInstanceOf[Class[T]]
      )
      Behaviors.receivePartial {
        case (memberEventCtx, MemberEventAdapter(event)) =>
          memberEventCtx.log.info(s"MemberEventAdapter received with event ${event.member.address}")
          actorToNotify.self ! msgToSend(event.member.address)
          Behaviors.same
      }
    }




