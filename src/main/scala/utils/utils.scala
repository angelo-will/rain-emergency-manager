package utils

import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

val seeds = List(2551, 2552) // seed used in the configuration

def startup[X](file: String = "base-cluster", port: Int)(root: => Behavior[X]): ActorSystem[X] =
  // Override the configuration of the port
  // Siccome sono nella stessa macchina devo usare porte diverse per simulare nodi diversi
  val config = ConfigFactory
    .parseString(s"""akka.remote.artery.canonical.port=$port""")
    .withFallback(ConfigFactory.load(file))

  // Create an Akka system
  ActorSystem(root, "ClusterSystem", config)

def startupWithRole[X](role: String, port: Int)(root: => Behavior[X]): ActorSystem[X] =
  val config = ConfigFactory
    .parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [$role]
      """)
    .withFallback(ConfigFactory.load("base-cluster"))

  // Create an Akka system
  ActorSystem(root, "ClusterSystem", config)
