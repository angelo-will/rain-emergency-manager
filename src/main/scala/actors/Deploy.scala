package actors

import actors.firestastion.FireStationActor
import actors.pluviometer.PluviometerActor
import actors.view.ViewActor
import actors.zone.ZoneActor
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import actors.message.Message
import systemelements.SystemElements.{Pluviometer, Zone}

object Deploy:
  def zone(zone: Zone, zoneActorName: String): Behavior[Message] =
    deploy(ZoneActor(zone))(zoneActorName)

  def pluviometer(pluviometer: Pluviometer, pluviometerActorName: String): Behavior[Message] =
    deploy(PluviometerActor(pluviometer))(pluviometerActorName)

  def fireStation(zoneCode: String, fireStationCode: String, PubSubChannelName: String): Behavior[Message] =
    deploy(FireStationActor(
      fireStationCode,
      fireStationCode,
      zoneCode,
      PubSubChannelName
    ))(s"actor-$fireStationCode")

  def view(fsCode:String, allFSCodes: Seq[String], topicName: String): Behavior[Message] =
    deploy(ViewActor(fsCode, allFSCodes, topicName))("actor-view")

  private def deploy(behavior: Behavior[Message])(actorName: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(behavior, actorName)
    Behaviors.empty
  }