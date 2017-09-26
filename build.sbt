val Versions = new {
  val akka24   = "2.4.12" // First version cross-compiled to 2.12
  val akka25   = "2.5.0"
  val lagom13  = "1.3.0"
  val lagom14  = "1.4.0-M2"
  val play25   = "2.5.0"
  val play26   = "2.6.0"
  val scala211 = "2.11.11"
  val scala212 = "2.12.3"
}

lazy val sharedSettings = Vector(
  organization := "com.lightbend.rp",
  organizationName := "Lightbend, Inc.",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalaVersion := Versions.scala212,
  crossScalaVersions := Vector(scalaVersion.value, Versions.scala211),
  scalacOptions ++= Vector("-deprecation")
)

lazy val scala211Settings = Vector(
  scalaVersion := Versions.scala211,
  crossScalaVersions := Vector(Versions.scala211)
)

lazy val reactiveLib = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(name := "reactive-lib")
  .aggregate(
    akka24,
    akka25,
    lagom13Java,
    lagom13Scala,
    lagom14Java,
    lagom14Scala,
    play25,
    play26
  )

lazy val common = (project in file("common"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(name := "reactive-lib-common")

lazy val akka24 = (project in file("akka24"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-akka24",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % Versions.akka24
    )
  )

lazy val akka25 = (project in file("akka25"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-akka25",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % Versions.akka25
    )
  )

lazy val lagom13Java = (project in file("lagom13-java"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(scala211Settings)
  .settings(
    name := "reactive-lib-lagom13-java",

    sourceDirectories in Compile ++= Vector(
      (sourceDirectory in (common, Compile)).value,
      (sourceDirectory in (akka24, Compile)).value
    ),

    sourceDirectories in Test ++= Vector(
      (sourceDirectory in (common, Test)).value,
      (sourceDirectory in (akka24, Test)).value
    ),

    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom13
    )
  )

lazy val lagom13Scala = (project in file("lagom13-scala"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(scala211Settings)
  .settings(
    name := "reactive-lib-lagom13-scala",

    sourceDirectories in Compile ++= Vector(
      (sourceDirectory in (common, Compile)).value,
      (sourceDirectory in (akka24, Compile)).value
    ),

    sourceDirectories in Test ++= Vector(
      (sourceDirectory in (common, Test)).value,
      (sourceDirectory in (akka24, Test)).value
    ),

    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom13
    )
  )

lazy val lagom14Java = (project in file("lagom14-java"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(scala211Settings /* @FIXME until a new 2.12 milestone is published */)
  .settings(
    name := "reactive-lib-lagom14-java",

    // @FIXME remove the sourceDirectories settings when a new 2.12 milestone is published

    sourceDirectories in Compile ++= Vector(
      (sourceDirectory in (common, Compile)).value,
      (sourceDirectory in (akka25, Compile)).value
    ),

    sourceDirectories in Test ++= Vector(
      (sourceDirectory in (common, Test)).value,
      (sourceDirectory in (akka25, Test)).value
    ),

    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom14
    )
  )

lazy val lagom14Scala = (project in file("lagom14-scala"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(scala211Settings /* @FIXME until 2.12 Milestone is published */)
  .settings(
    name := "reactive-lib-lagom14-scala",

    // @FIXME remove the sourceDirectories settings when a new 2.12 milestone is published

    sourceDirectories in Compile ++= Vector(
      (sourceDirectory in (common, Compile)).value,
      (sourceDirectory in (akka25, Compile)).value
    ),

    sourceDirectories in Test ++= Vector(
      (sourceDirectory in (common, Test)).value,
      (sourceDirectory in (akka25, Test)).value
    ),

    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom14
    )
  )

lazy val play25 = (project in file("play25"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(sharedSettings)
  .settings(scala211Settings)
  .settings(
    name := "reactive-lib-play25",

    sourceDirectories in Compile ++= Vector(
      (sourceDirectory in (common, Compile)).value,
      (sourceDirectory in (akka24, Compile)).value
    ),

    sourceDirectories in Test ++= Vector(
      (sourceDirectory in (common, Test)).value,
      (sourceDirectory in (akka24, Test)).value
    ),

    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % Versions.play25
    )
  )

lazy val play26 = (project in file("play26"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common, akka25)
  .settings(sharedSettings)
  .settings(
    name := "reactive-lib-play26",

    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % Versions.play26
    )
  )