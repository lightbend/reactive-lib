import ReleaseTransformations._

val Versions = new {
  val akka24            = "2.4.12" // First version cross-compiled to 2.12
  val akka25            = "2.5.0"
  val lagom13           = "1.3.0"
  val lagom14           = "1.4.0-M2"
  val play25            = "2.5.0"
  val play26            = "2.6.0"
  val scala211          = "2.11.11"
  val scala212          = "2.12.3"
  val scalaTest         = "3.0.1"
  val serviceLocatorDns = "2.2.2"
}

lazy val sharedSettings = Vector(
  resolvers += Resolver.bintrayRepo("hajile", "maven"),
  organization := "com.lightbend.rp",
  organizationName := "Lightbend, Inc.",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalaVersion := Versions.scala211,
  scalacOptions ++= Vector("-deprecation"),
  homepage := Some(url("https://www.lightbend.com/")),
  developers := List(
    Developer("lightbend", "Lightbend Contributors", "", url("https://github.com/typesafehub/reactive-lib"))
  ),
  sonatypeProfileName := "com.lightbend.rp",
  scmInfo := Some(ScmInfo(url("https://github.com/typesafehub/reactive-lib"), "git@github.com:typesafehub/reactive-lib.git")),
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

lazy val root = (project in file("."))
  .aggregate(
    akka24,
    akka25,
    common,
    lagom13Java,
    lagom13Scala,
    lagom14Java,
    lagom14Scala,
    play25,
    play26
  )
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib",
    publishArtifact := false
  )

lazy val common = (project in file("common"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-common",
    libraryDependencies ++= Seq(
      "org.scalatest"     %% "scalatest"  % Versions.scalaTest % "test"
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val akka24 = (project in file("akka24"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-akka24",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"          % Versions.akka24,
      "com.lightbend"     %% "service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"     %% "scalatest"           % Versions.scalaTest % "test"
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val akka25 = (project in file("akka25"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-akka25",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"          % Versions.akka25,
      "com.lightbend"     %% "service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"     %% "scalatest"           % Versions.scalaTest % "test"
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val lagom13Java = (project in file("lagom13-java"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(akka24)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-lagom13-java",
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom13,
      "org.scalatest"       %% "scalatest"            % Versions.scalaTest % "test"
    )
  )

lazy val lagom13Scala = (project in file("lagom13-scala"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(akka24)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-lagom13-scala",
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client"            % Versions.lagom13,
      "com.lightbend"       %% "lagom13-java-service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"       %% "scalatest"                        % Versions.scalaTest % "test"
    )
  )

lazy val lagom14Java = (project in file("lagom14-java"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(akka25)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-lagom14-java",
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client"             % Versions.lagom14,
      "com.lightbend"       %% "lagom13-java-service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"       %% "scalatest"                        % Versions.scalaTest % "test"
    )
  )

lazy val lagom14Scala = (project in file("lagom14-scala"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(akka25)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-lagom14-scala",
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client"            % Versions.lagom14,
      "com.lightbend"       %% "lagom13-java-service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"       %% "scalatest"                        % Versions.scalaTest % "test"
    )
  )

lazy val play25 = (project in file("play25"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(akka24)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-play25",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws"             % Versions.play25,
      "com.lightbend"     %% "service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"     %% "scalatest"           % Versions.scalaTest % "test"
    )
  )

lazy val play26 = (project in file("play26"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(akka25)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-play26",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws"             % Versions.play26,
      "com.lightbend"     %% "service-locator-dns" % Versions.serviceLocatorDns,
      "org.scalatest"     %% "scalatest" % Versions.scalaTest % "test"
    ),
    crossScalaVersions := Vector(scalaVersion.value, Versions.scala211)
  )
