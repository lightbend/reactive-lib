import sbt._

object Dependencies {
  val akkaVersion = "2.5.20"
  val freshReactiveLibVersion = sys.props.get("plugin.version").getOrElse {
    sys.error("""|The system property 'plugin.version' is not defined.
                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  }

  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaSlj4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % Test
}
