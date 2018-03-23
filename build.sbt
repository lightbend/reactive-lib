import scala.collection.immutable.Seq
import ReleaseTransformations._

lazy val Versions = new {
  val akka                      = "2.5.7"
  val akkaDns                   = "2.4.2"
  val akkaManagement            = "0.9.0"
  val lagom14                   = "1.4.0"
  val play25                    = "2.5.0"
  val play26                    = "2.6.0"
  val scala211                  = "2.11.11"
  val scala212                  = "2.12.3"
  val scalaJava8Compat          = "0.8.0"
  val scalaTest                 = "3.0.1"
  val typesafeConfig            = "1.3.1"
}

def semanticVersioningMajor(version: String) =
  version
    .reverse
    .dropWhile(_ != '.')
    .dropWhile(_ == '.')
    .reverse

def createProject(id: String, path: String, headers: Boolean = true) =
  (
    if (headers)
      Project(id, file(path)).enablePlugins(AutomateHeaderPlugin)
    else
      Project(id, file(path)))
  .settings(
    name := id,
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
    akkaManagement,
    asyncDns,
    common,
    playHttpBinding,
    secrets,
    serviceDiscovery,
    serviceDiscoveryLagom14Java,
    serviceDiscoveryLagom14Scala,
    status
  )

lazy val common = createProject("reactive-lib-common", "common")
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val asyncDns = createProject("reactive-lib-async-dns", "async-dns", headers = false)
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"   % Versions.akka,
      "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val serviceDiscovery = createProject("reactive-lib-service-discovery", "service-discovery")
  .dependsOn(common, asyncDns)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val serviceDiscoveryLagom14Java = createProject("reactive-lib-service-discovery-lagom14-java", "service-discovery-lagom14-java")
  .dependsOn(serviceDiscovery)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom14 % "provided"
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val serviceDiscoveryLagom14Scala = createProject("reactive-lib-service-discovery-lagom14-scala", "service-discovery-lagom14-scala")
  .dependsOn(serviceDiscovery)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom14 % "provided",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val akkaClusterBootstrap = createProject("reactive-lib-akka-cluster-bootstrap", "akka-cluster-bootstrap")
  .dependsOn(akkaManagement, serviceDiscovery, status)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % Versions.akkaManagement,
      "com.lightbend.akka.discovery"  %% "akka-discovery-marathon-api"       % Versions.akkaManagement,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.akkaManagement,
      "com.typesafe.akka"             %% "akka-testkit"                      % Versions.akka              % "test",
      "com.typesafe.akka"             %% "akka-cluster"                      % Versions.akka              % "provided"
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val akkaManagement = createProject("reactive-lib-akka-management", "akka-management")
  .dependsOn(common)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    libraryDependencies ++= Seq(
      "com.lightbend.akka.management" %% "akka-management" % Versions.akkaManagement,
      "com.typesafe.akka"             %% "akka-actor"      % Versions.akka              % "provided"
    )
  )

lazy val playHttpBinding = createProject("reactive-lib-play-http-binding", "play-http-binding")
  .dependsOn(common, status)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val secrets = createProject("reactive-lib-secrets", "secrets")
  .dependsOn(common)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"  % Versions.akka % "provided",
      "com.typesafe.akka" %% "akka-stream" % Versions.akka % "provided"
    )
  )

lazy val status = createProject("reactive-lib-status", "status")
  .dependsOn(akkaManagement)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )
