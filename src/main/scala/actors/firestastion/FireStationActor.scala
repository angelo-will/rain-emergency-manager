package actors.firestastion

import actors.firestastion.FireStationActor.AdapterMessageForConnection
import actors.zone.ZoneActor
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import message.Message
import systemelements.SystemElements.{FireStation, FireStationBusy, FireStationFree, FireStationState}
import systemelements.SystemElements.ZoneAlarm

object FireStationActor:

  sealed trait Command extends Message

  case class Managing(stationCode: String) extends Command

  case class Solved(stationCode: String) extends Command

  ///////////
  case class FindZone(zoneCode: String) extends Command

  case class ConnectedToZone(zoneRef: ActorRef[Message]) extends Command

  ///////////
  case class SendGetStatus(zoneRef: ActorRef[Message]) extends Command

  //////
  case class FireStationStatus(fireStationStatus: FireStation) extends Command

  //adapter use for recover the zoneActor reference
  private case class AdapterMessageForConnection(listing: Receptionist.Listing) extends Command


  def apply(name: String, fireStationCode: String, zoneCode: String, PubSubChannelName: String): Behavior[Message] =
    new FireStationActor(name, fireStationCode, zoneCode, PubSubChannelName).starting()

private case class FireStationActor(name: String, fireStationCode: String, zoneCode: String, PubSubChannelName: String):

  import FireStationActor.{Managing, Solved, FindZone, SendGetStatus, FireStationStatus}
  import scala.concurrent.duration.FiniteDuration
  import actors.zone.ZoneActor.ZoneStatus
  import akka.actor.typed.pubsub.Topic
  import akka.actor.typed.pubsub.PubSub

  private val searchingZoneFrequency = FiniteDuration(5, "second")
  private val updateGUIConnectedFrequency = FiniteDuration(1, "second")
  private val askZoneStatus = FiniteDuration(1, "second")
  private var fireState: FireStationState = FireStationFree()


  //First thing to do is retrieve the zoneActor reference
  private def starting(): Behavior[Message] = Behaviors.setup { ctx =>
    val topic = retrievePubSubTopic(ctx)
    topic ! Topic.Subscribe(ctx.self)

    Behaviors.withTimers { timers =>
      val zoneServiceKey = ServiceKey[Message](zoneCode)
      timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
      Behaviors.receiveMessagePartial {
        case FindZone(zoneCode) =>
          ctx.log.info(s"Timer tick, i'm trying to connect to zone $zoneCode")
          ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.messageAdapter[Receptionist.Listing](AdapterMessageForConnection.apply))
          Behaviors.same
        case AdapterMessageForConnection(zoneServiceKey.Listing(l)) =>
          if (l.nonEmpty) {
            // found the zone, move to work
            ctx.log.info(s"Found $zoneCode, now start normal operations")
            timers.cancelAll()
            operating(l.head)
          } else Behaviors.same
      }
    }
  }

  private def operating(zoneRef: ActorRef[Message]): Behavior[Message] = Behaviors.setup { ctx =>

    val topic = retrievePubSubTopic(ctx)

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendGetStatus(zoneRef), askZoneStatus)
      Behaviors.receivePartial {
        requestZoneStatus(Behaviors.same)
          .orElse(publishZoneStatus(Behaviors.same, topic, true))
      }
    }
  }

  private def inAlarm(zoneRef: ActorRef[Message]): Behavior[Message] = Behaviors.setup { ctx =>

    val topic = retrievePubSubTopic(ctx)

    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendGetStatus(zoneRef), askZoneStatus)

      Behaviors.receivePartial {
        requestZoneStatus(Behaviors.same)
          .orElse(publishZoneStatus(Behaviors.same, topic, false))
          .orElse {
            case (ctx, Managing(stationCode)) =>
              ctx.log.info(s"Received Managing with code $stationCode")
              if stationCode equals fireStationCode then
                zoneRef ! ZoneActor.UnderManagement(ctx.self)
                fireState = FireStationBusy()
              Behaviors.same
            case (ctx, Solved(stationCode)) =>
              ctx.log.info(s"Received Solved with code $stationCode")
              if stationCode equals fireStationCode then
                zoneRef ! ZoneActor.Solved(ctx.self)
                fireState = FireStationFree()
              timers.cancelAll()
              operating(zoneRef)
          }
      }
    }
  }

  private def retrievePubSubTopic(ctx: ActorContext[Message]): ActorRef[Topic.Command[Message]] =
    val pubSub = PubSub(ctx.system)
    pubSub.topic[Message](PubSubChannelName)

  private def requestZoneStatus(behaviour: Behavior[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, SendGetStatus(zoneRef)) =>
      ctx.log.info(s"Timer tick, i'm asking to $zoneCode its status")
      zoneRef ! ZoneActor.GetZoneStatus(ctx.self)
      behaviour

  private def publishZoneStatus(behaviour: Behavior[Message], topic: ActorRef[Topic.Command[Message]], goIntoAlarm: Boolean): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, ZoneStatus(zoneClass, zoneRef)) =>
      ctx.log.info(s"Received status from $zoneCode, sending it on PubSub")
      topic ! Topic.publish(
        FireStationStatus(FireStation(fireStationCode, fireState, zoneClass))
      )
      zoneClass.zoneState match
        case zState if zState.equals(ZoneAlarm()) =>
          ctx.log.info(s"Going into Alarm")
          if goIntoAlarm then inAlarm(zoneRef) else behaviour
        case _ => behaviour


