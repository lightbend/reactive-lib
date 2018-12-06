import sbt._

object Dependencies {
  val akkaVersion = "2.5.18"
  val akkaManagementVersion = "0.20.0"
  val freshReactiveLibVersion = sys.props.get("plugin.version").getOrElse {
    sys.error("""|The system property 'plugin.version' is not defined.
                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  }

  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaSlj4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val akkaBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion
  val akkaServiceDiscoveryK8Api = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion
  val akkaServiceDiscoveryConfig = "com.lightbend.akka.discovery" %% "akka-discovery-config" % akkaManagementVersion
  val akkaClusterHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % Test

  val serviceDeps = Seq(
    akkaBootstrap, akkaServiceDiscoveryK8Api, akkaServiceDiscoveryConfig, akkaClusterHttp,
    akkaCluster, akkaClusterSharding, akkaClusterTools, akkaSlj4j,
    logback,
    scalaTest)
}
