import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import firestastion.FireStationActor
import message.Message
import pluviometer.PluviometerActor
import systemelements.SystemElements.{Pluviometer, Zone}
import view.ViewActor
import zone.ZoneActor

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

  def view(fsCodes: Seq[String]): Behavior[Message] =
    deploy(ViewActor(fsCodes))("actor-view")

  private def deploy(behavior: Behavior[Message])(actorName: String): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.spawn(behavior, actorName)
    Behaviors.empty
  }