package firestastion

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import firestastion.FireStationActor.AdapterMessageForConnection
import message.Message
import systemelements.SystemElements.FireStationState.{Busy, Free}
import systemelements.SystemElements.{FireStation, FireStationState, ZoneState}
import zone.ZoneActor
import zone.ZoneActor.ZoneStatus

object FireStationActor:

  sealed trait Command extends Message

  case class Managing() extends Command

  case class Solved() extends Command

  ///////////
  case class FindZone(zoneCode: String) extends Command

  case class ConnectedToZone(zoneRef: ActorRef[Message]) extends Command

  ///////////
  case class SendGetStatus(zoneRef: ActorRef[Message]) extends Command

  //////
  case class FireStationStatus(fireStationStatus: FireStation) extends Command

  //adapter use for recover the zoneActor reference
  private case class AdapterMessageForConnection(listing: Receptionist.Listing) extends Command


  def apply(name: String, fireStationCode: String, zoneCode: String): Behavior[Message] =
    new FireStationActor(name, fireStationCode, zoneCode).starting()

private case class FireStationActor(name: String, fireStationCode: String, zoneCode: String):
  
  import FireStationActor.{Managing, Solved, FindZone, SendGetStatus, FireStationStatus}
  import scala.concurrent.duration.FiniteDuration
  import akka.actor.typed.pubsub.Topic
  import akka.actor.typed.pubsub.PubSub

  private val searchingZoneFrequency = FiniteDuration(5, "second")
  private val updateGUIConnectedFrequency = FiniteDuration(5, "second")
  private val askZoneStatus = FiniteDuration(5, "second")
  private var lastZoneState: Option[ZoneState] = None
  private var fireState = Free

  private var zoneRef: Option[ActorRef[Message]] = None

  //First thing to do is retrieve the zoneActor reference
  private def starting(): Behavior[Message] = Behaviors.setup { ctx =>
    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("channel")
    topic ! Topic.Subscribe(ctx.self)

    Behaviors.withTimers { timers =>
      val zoneServiceKey = ServiceKey[Message](zoneCode)
      timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
      Behaviors.receiveMessagePartial {
        case FindZone(zoneCode) =>
          ctx.log.info(s"Timer tick, i'm trying to connect to zone $zoneCode")
          ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.messageAdapter[Receptionist.Listing](AdapterMessageForConnection.apply))
          Behaviors.same
        case AdapterMessageForConnection(zoneServiceKey.Listing(l))  =>
          if (l.nonEmpty) {
            // found the zone, move to work
            operating(l.head)
          } else Behaviors.same
      }
    }
  }

  private def operating(zoneRef: ActorRef[Message]): Behavior[Message] = Behaviors.setup { ctx =>

    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("channel")

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendGetStatus(zoneRef), askZoneStatus)
      Behaviors.receivePartial {
        requestZoneStatus(Behaviors.same)
          .orElse(askZoneUpdate(Behaviors.same, topic, true))
//        case SendGetStatus(zoneRef) =>
//          zoneRef ! ZoneActor.GetZoneStatus(ctx.self)
//          Behaviors.same
//        case ZoneStatus(zoneClass, zoneRef) =>
//          //send message to gui using pub/sub
//          topic ! Topic.publish(
//            FireStationStatus(FireStation(fireStationCode, fireState, zoneClass))
//          )
//          zoneClass.zoneState match
//            case ZoneState.Alarm => inAlarm(zoneRef)
//            case _ => Behaviors.same
      }
    }
  }

  private def inAlarm(zoneRef: ActorRef[Message]): Behavior[Message] = Behaviors.setup { ctx =>

    val pubSub = PubSub(ctx.system)
    val topic: ActorRef[Topic.Command[Message]] = pubSub.topic[Message]("channel")

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendGetStatus(zoneRef), askZoneStatus)

      Behaviors.receivePartial {
        requestZoneStatus(Behaviors.same)
          .orElse(askZoneUpdate(Behaviors.same, topic, false))
          .orElse {
            case (ctx, Managing()) =>
              zoneRef ! ZoneActor.UnderManagement(ctx.self)
              fireState = Busy
              Behaviors.same
            case (ctx, Solved()) =>
              zoneRef ! ZoneActor.Solved(ctx.self)
              fireState = Free
              operating(zoneRef)
          }
      }
//      Behaviors.receiveMessagePartial {
//        case SendGetStatus(zoneRef) =>
//          zoneRef ! ZoneActor.GetZoneStatus(ctx.self)
//          Behaviors.same
//        case ZoneActor.ZoneStatus(zoneClass, zoneRef) =>
//          //send message to gui using pub/sub
//          topic ! Topic.publish(
//            FireStationStatus(FireStation(fireStationCode, fireState, zoneClass))
//          )
//          Behaviors.same
//        case Managing() =>
//          zoneRef ! ZoneActor.UnderManagement(ctx.self)
//          fireState = Busy
//          Behaviors.same
//        case Solved() =>
//          zoneRef ! ZoneActor.Solved(ctx.self)
//          fireState = Free
//          operating(zoneRef)
//      }
    }
  }

  private def requestZoneStatus(behaviour: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, SendGetStatus(zoneRef)) =>
      zoneRef ! ZoneActor.GetZoneStatus(ctx.self)
      behaviour

  private def askZoneUpdate(behaviour: Behavior[Message], topic: ActorRef[Topic.Command[Message]], goIntoAlarm: Boolean): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, ZoneStatus(zoneClass, zoneRef)) =>
      topic ! Topic.publish(
        FireStationStatus(FireStation(fireStationCode, fireState, zoneClass))
      )
      zoneClass.zoneState match
        case ZoneState.Alarm =>  if goIntoAlarm then inAlarm(zoneRef) else behaviour
        case _ => behaviour


