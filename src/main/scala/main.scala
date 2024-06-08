import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import message.Message
import utils.{seeds, startup}
import zone.Zone

object ZoneDeploy:
  //  def apply(zoneServiceKey: ServiceKey[Zone.Command]): Behavior[Unit] =
  def apply(zoneCode: String, zoneName: String): Behavior[Message] =
    val zoneServiceKey = ServiceKey[Message](zoneCode)
    Behaviors.setup { ctx =>
      val actorRef = ctx.spawn(Zone(zoneName), zoneServiceKey.id)
      // ogni attore deve essere registrato al receptionist
      ctx.system.receptionist ! Receptionist.Register(zoneServiceKey, actorRef)
      ctx.log.info(s"${zoneServiceKey.id} register zone")
      Behaviors.empty
    }

object PluviometerDeploy:

  import pluviometer.Pluviometer

  def apply(zoneCode: String, pluviometerName: String): Behavior[Message] =
    Behaviors.setup { ctx =>
      // Eventualmente si può provare a far autodeterminare il pluviometro a quale zona collegarsi
      val actorRef = ctx.spawn(Pluviometer(pluviometerName, zoneCode), s"actor-$pluviometerName")
      Behaviors.empty
    }

@main def startZone01(): Unit =
  startup(port = 2551)(ZoneDeploy("zone-01", "zone-01"))
//  startup(port = seeds.head)(ZoneDeploy("zone-01", "zone-01"))

//@main def startZone02(): Unit =

@main def deploySensor01(): Unit =
  startup(port = 8080)(PluviometerDeploy("zone-01", "esp32-001"))

@main def deploySensor02(): Unit =
  startup(port = 8081)(PluviometerDeploy("zone-01", "esp32-002"))

@main def deploySensor03(): Unit =
  startup(port = 8082)(PluviometerDeploy("zone-01", "esp32-003"))