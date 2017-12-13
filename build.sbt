import scala.collection.immutable.Seq
import ReleaseTransformations._

lazy val Versions = new {
  val akka                      = "2.4.12" // First version cross-compiled to 2.12
  val akkaDns                   = "2.4.2"
  val akkaManagementClusterHttp = "0.4.1"
  val lagom13                   = "1.3.0"
  val lagom14                   = "1.4.0-M2"
  val play25                    = "2.5.0"
  val play26                    = "2.6.0"
  val scala211                  = "2.11.11"
  val scala212                  = "2.12.3"
  val scalaJava8Compat          = "0.8.0"
  val scalaTest                 = "3.0.1"
  val typesafeConfig            = "1.3.1"
}

/**
 * This method excludes any files with a subdirectory sequence of `names` in the path. This
 * is used to ensure that local project files from dependencies don't end up in the assembled
 * jar. For instance, you call this with "common" to ensure no "com/lightbend/rp/common"
 * directories end up in your jar.
 */
def assemblyExcludeLocal(names: Seq[String]*) =
  assemblyOption in assembly := {
    val options = (assemblyOption in assembly).value

    options.copy(
      excludedFiles = { files =>
        options.excludedFiles(files) ++ files
          .filter(_.isDirectory)
          .map(dir => (dir, (dir ** "*").get))
          .flatMap { case (dir, files) =>
            names.flatMap { names =>
              val path = (Seq("com", "lightbend", "rp") ++ names).mkString(s"${Path.sep}")

              if (files.exists(_.getAbsolutePath.contains(path)))
                dir +: files
              else
                Seq.empty
            }
          }
      }
    )
  }

def assemblyInclude(names: String*) =
  assemblyExcludedJars in assembly :=
    (fullClasspath in assembly)
      .value
      .filterNot(f => names.nonEmpty && names.exists(f.data.getName.startsWith))

def semanticVersioningMajor(version: String) =
  version
    .reverse
    .dropWhile(_ != '.')
    .dropWhile(_ == '.')
    .reverse

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

lazy val serviceDiscoveryAssemblySettings = Vector(
  libraryDependencies ++= Seq(
    "com.typesafe.akka"        %% "akka-actor"          % Versions.akka              % "provided",
    "ru.smslv.akka"            %% "akka-dns"            % Versions.akkaDns           % "provided"
  ),
  assemblyShadeRules in assembly ++= Seq(
    ShadeRule.rename("akka.io.AsyncDnsResolver**" -> "com.lightbend.rp.internal.@0").inAll,
    ShadeRule.rename("ru.smslv**" -> "com.lightbend.rp.internal.@0").inAll
  ),

  addArtifact(artifact in (Compile, assembly), assembly),

  assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),

  assemblyJarName in assembly :=
    s"${name.value}_${semanticVersioningMajor(scalaVersion.value)}-${version.value}.jar",

  packageBin in Compile := (assembly in Compile).value)

lazy val serviceDiscovery = createProject("reactive-lib-service-discovery", "service-discovery")
  .enablePlugins(AssemblyPlugin)
  .dependsOn(common)
  .settings(serviceDiscoveryAssemblySettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    assemblyInclude("akka-dns"),
    assemblyExcludeLocal(Seq("common"))
  )

lazy val serviceDiscoveryLagom13Java = createProject("reactive-lib-service-discovery-lagom13-java", "service-discovery-lagom13-java")
  .enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings: _*)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom13 % "provided"
    ),
    assemblyInclude(),
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom13Scala = createProject("reactive-lib-service-discovery-lagom13-scala", "service-discovery-lagom13-scala")
  .enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client"   % Versions.lagom13 % "provided"
    ),
    assemblyInclude(),
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom14Java = createProject("reactive-lib-service-discovery-lagom14-java", "service-discovery-lagom14-java")
  .enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings: _*)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom14 % "provided"
    ),
    assemblyInclude(),
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom14Scala = createProject("reactive-lib-service-discovery-lagom14-scala", "service-discovery-lagom14-scala")
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom14 % "provided",
    ),
    assemblyInclude(),
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val akkaClusterBootstrap = createProject("reactive-lib-akka-cluster-bootstrap", "akka-cluster-bootstrap")
  .dependsOn(serviceDiscovery)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.akka"      %% "akka-management-cluster-http" % Versions.akkaManagementClusterHttp,
      "com.typesafe.akka"       %% "akka-cluster"                 % Versions.akka     % "provided",
      "ru.smslv.akka"           %% "akka-dns"                     % Versions.akkaDns  % "provided"
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
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"  % Versions.akka % "provided",
      "com.typesafe.akka" %% "akka-stream" % Versions.akka % "provided"
    )
  )
