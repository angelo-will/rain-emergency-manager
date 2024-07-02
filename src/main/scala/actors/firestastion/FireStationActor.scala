package actors.firestastion

import actors.commonbehaviors.MemberEventBehavior
import actors.firestastion.FireStationActor.{AdapterMessageForConnection, ZoneNotFound}
import actors.zone.ZoneActor
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import message.Message
import systemelements.SystemElements.{FireStation, FireStationBusy, FireStationFree, FireStationState}

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

  // Message used to inform the gui that the firestation is not able to connect to its designed zone
  case class ZoneNotFound(fsCode: String) extends Command

  //adapter use for recover the zoneActor reference
  private case class AdapterMessageForConnection(listing: Receptionist.Listing) extends Command


  def apply(name: String, fireStationCode: String, zoneCode: String, PubSubChannelName: String): Behavior[Message] =
    new FireStationActor(fireStationCode, zoneCode, PubSubChannelName).starting

private case class FireStationActor(fireStationCode: String, zoneCode: String, pubSubChannelName: String):

  import FireStationActor.{Managing, Solved, FindZone, SendGetStatus, FireStationStatus}
  import scala.concurrent.duration.FiniteDuration
  import actors.zone.ZoneActor.ZoneStatus
  import akka.actor.typed.pubsub.Topic
  import akka.actor.typed.pubsub.PubSub

  private val searchingZoneFrequency = FiniteDuration(5, "second")
  private val askZoneStatus = FiniteDuration(1, "second")

  private def starting: Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(MemberEventBehavior.memberExitBehavior(ctx), s"$fireStationCode-member-event")
    retrievePubSubTopic(ctx, pubSubChannelName) ! Topic.Subscribe(ctx.self)
    connectToZone
  }

  //First thing to do is retrieve the zoneActor reference
  private def connectToZone: Behavior[Message] =

    Behaviors.withTimers { timers =>
      val zoneServiceKey = ServiceKey[Message](zoneCode)
      timers.startTimerAtFixedRate(FindZone(zoneCode), searchingZoneFrequency)
      Behaviors.receivePartial {
        case (ctx, FindZone(zoneCode)) =>
          ctx.log.info(s"Timer tick, i'm trying to connect to zone $zoneCode")
          ctx.system.receptionist ! Receptionist.Subscribe(zoneServiceKey, ctx.messageAdapter[Receptionist.Listing](AdapterMessageForConnection.apply))
          retrievePubSubTopic(ctx, pubSubChannelName) ! Topic.publish(ZoneNotFound(fireStationCode))
          Behaviors.same
        case (ctx, AdapterMessageForConnection(zoneServiceKey.Listing(l))) =>
          if (l.nonEmpty) {
            // found the zone, move to work
            ctx.log.info(s"Found $zoneCode, now start normal operations")
            timers.cancelAll()
            updateJob(l.head)
          } else Behaviors.same
      }
    }

  private def updateJob(zoneRef: ActorRef[Message]): Behavior[Message] =
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(SendGetStatus(zoneRef), askZoneStatus)
      free(zoneRef,timers)
    }

  private def free(zoneRef: ActorRef[Message], timerScheduler: TimerScheduler[Message]): Behavior[Message] = Behaviors.receivePartial {
    requestZoneStatus
      .orElse(receivedZoneStatus(FireStationFree()))
      .orElse(handlerZoneDisconnected(zoneRef, timerScheduler))
      .orElse({
        case (ctx, Managing(stationCode)) =>
          ctx.log.info(s"--MANAGING-- Received with code $stationCode")
          if isThisFireStation(stationCode) then
            zoneRef ! ZoneActor.UnderManagement(ctx.self)
            managing(zoneRef, timerScheduler)
          else
            Behaviors.same
      })
  }

  private def managing(zoneRef: ActorRef[Message], timerScheduler: TimerScheduler[Message]): Behavior[Message] = Behaviors.receivePartial {
    requestZoneStatus
      .orElse(receivedZoneStatus(FireStationBusy()))
      .orElse(handlerZoneDisconnected(zoneRef, timerScheduler))
      .orElse({
        case (ctx, Solved(stationCode)) =>
          ctx.log.info(s"--SOLVED-- Received with code $stationCode")
          if isThisFireStation(stationCode) then
            zoneRef ! ZoneActor.Solved(ctx.self)
            free(zoneRef, timerScheduler)
          else
            Behaviors.same
      })
  }

  private def isThisFireStation(stationCode: String) = fireStationCode equals stationCode

  private def retrievePubSubTopic(ctx: ActorContext[Message], pubSubChannelName: String) =
    PubSub(ctx.system).topic[Message](pubSubChannelName)

  private def requestZoneStatus: PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, SendGetStatus(zoneRef)) =>
      ctx.log.info(s"Timer TICK, request status to zone $zoneCode")
      zoneRef ! ZoneActor.GetZoneStatus(ctx.self)
      Behaviors.same

  private def receivedZoneStatus(fireStationState: FireStationState): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, ZoneStatus(zoneClass, zoneRef)) =>
      ctx.log.info(s"Received zone status from $zoneCode, sending it on PubSub")
      retrievePubSubTopic(ctx, pubSubChannelName) ! Topic.publish(
        FireStationStatus(FireStation(fireStationCode, fireStationState, zoneClass))
      )
      Behaviors.same

  private def handlerZoneDisconnected(zoneRef: ActorRef[Message], timerScheduler: TimerScheduler[Message]): PartialFunction[(ActorContext[Message], Message), Behavior[Message]] =
    case (ctx, MemberEventBehavior.MemberExit(address)) =>
      ctx.log.info(s"Received MemberExit")
      if zoneRef.path.address == address then
        timerScheduler.cancelAll()
        ctx.log.info(s"Zone disconnected, returning in connectToZone")
        connectToZone
      else
        Behaviors.same



