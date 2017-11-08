import ReleaseTransformations._

lazy val Versions = new {
  val akka              = "2.4.12" // First version cross-compiled to 2.12
  val akkaHttp          = "10.0.10"
  val lagom13           = "1.3.0"
  val lagom14           = "1.4.0-M2"
  val play25            = "2.5.0"
  val play26            = "2.6.0"
  val scala211          = "2.11.11"
  val scala212          = "2.12.3"
  val scalaTest         = "3.0.1"
  val serviceLocatorDns = "2.2.2"
}

def createProject(id: String, path: String) = Project(id, file(path))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := id,
    resolvers += Resolver.bintrayRepo("hajile", "maven"),
    organization := "com.lightbend.rp",
    organizationName := "Lightbend, Inc.",
    startYear := Some(2017),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion := Versions.scala211,
    scalacOptions ++= Vector("-deprecation"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"
    ),
    homepage := Some(url("https://www.lightbend.com/")),
    developers := List(
      Developer("lightbend", "Lightbend Contributors", "", url("https://github.com/lightbend/reactive-lib"))
    ),
    sonatypeProfileName := "com.lightbend.rp",
    scmInfo := Some(ScmInfo(url("https://github.com/lightbend/reactive-lib"), "git@github.com:lightbend/reactive-lib.git")),
    scalaVersion := Versions.scala211,
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    releaseCrossBuild := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

lazy val root = createProject("reactive-lib", ".")
  .aggregate(
    akkaClusterBootstrap,
    common,
    playHttpBinding,
    secrets,
    serviceDiscovery,
    serviceDiscoveryLagom13Java,
    serviceDiscoveryLagom13Scala,
    serviceDiscoveryLagom14Java,
    serviceDiscoveryLagom14Scala
  )
  .settings(
    publishArtifact := false
  )

lazy val common = createProject("reactive-lib-common", "common")
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val serviceDiscovery = createProject("reactive-lib-service-discovery", "service-discovery")
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend"     %% "service-locator-dns" % Versions.serviceLocatorDns
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val serviceDiscoveryLagom13Java = createProject("reactive-lib-service-discovery-lagom13-java", "service-discovery-lagom13-java")
  .dependsOn(serviceDiscovery)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom13
    )
  )

lazy val serviceDiscoveryLagom13Scala = createProject("reactive-lib-service-discovery-lagom13-scala", "service-discovery-lagom13-scala")
  .dependsOn(serviceDiscovery)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom13
    )
  )

lazy val serviceDiscoveryLagom14Java = createProject("reactive-lib-service-discovery-lagom14-java", "service-discovery-lagom14-java")
  .dependsOn(serviceDiscovery)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom14
    )
  )

lazy val serviceDiscoveryLagom14Scala = createProject("reactive-lib-service-discovery-lagom14-scala", "service-discovery-lagom14-scala")
  .dependsOn(serviceDiscovery)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom14
    )
  )

lazy val akkaClusterBootstrap = createProject("reactive-lib-akka-cluster-bootstrap", "akka-cluster-bootstrap")
  .dependsOn(serviceDiscovery)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"          % Versions.akka,
      "com.typesafe.akka" %% "akka-cluster"        % Versions.akka,
      "com.typesafe.akka" %% "akka-http"           % Versions.akkaHttp
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val playHttpBinding = createProject("reactive-lib-play-http-binding", "play-http-binding")
  .dependsOn(common)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val secrets = createProject("reactive-lib-secrets", "secrets")
  .dependsOn(common)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )