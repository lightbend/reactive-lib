import sbt._

object Dependencies {
  val akkaVersion = "2.5.19"
  val akkaManagementVersion = "0.20.0+27-a631d10c" // 1.0.0-RC1
  val freshReactiveLibVersion = sys.props.get("plugin.version").getOrElse {
    sys.error("""|The system property 'plugin.version' is not defined.
                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  }

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % akkaVersion
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaSlj4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val akkaManagement = "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion
  val akkaBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion
  val akkaClusterHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % Test
}
