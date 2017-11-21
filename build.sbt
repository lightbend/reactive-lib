import scala.collection.immutable.Seq
import ReleaseTransformations._

lazy val Versions = new {
  val akka              = "2.4.12" // First version cross-compiled to 2.12
  val akkaDns           = "2.4.2"
  val dispatch          = "0.13.2"
  val lagom13           = "1.3.0"
  val lagom14           = "1.4.0-M2"
  val play25            = "2.5.0"
  val play26            = "2.6.0"
  val scala211          = "2.11.11"
  val scala212          = "2.12.3"
  val scalaJava8Compat  = "0.8.0"
  val scalaTest         = "3.0.1"
  val serviceLocatorDns = "2.2.2"
  val sprayJson         = "1.3.3"
  val typesafeConfig    = "1.3.1"
}

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
  .enablePlugins(AutomateHeaderPlugin, AssemblyPlugin)
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
    ),

    addArtifact(artifact in (Compile, assembly), assembly),

    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),

    assemblyJarName in assembly :=
      s"${name.value}_${semanticVersioningMajor(scalaVersion.value)}-${version.value}.jar",

    packageBin in Compile := (assembly in Compile).value
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
    "com.typesafe"              % "config"              % Versions.typesafeConfig    % "provided",
    "org.scala-lang.modules"   %% "scala-java8-compat"  % Versions.scalaJava8Compat  % "provided",
    "com.lightbend"            %% "service-locator-dns" % Versions.serviceLocatorDns,
    "ru.smslv.akka"            %% "akka-dns"            % Versions.akkaDns
  ),
  assemblyShadeRules in assembly ++= Seq(
    ShadeRule.rename("akka.io.AsyncDnsResolver**" -> "com.lightbend.rp.internal.@0").inAll,
    ShadeRule.rename("com.lightbend.dns.locator.**" -> "com.lightbend.rp.internal.@0").inAll,
    ShadeRule.rename("ru.smslv**" -> "com.lightbend.rp.internal.@0").inAll
  )
)

lazy val serviceDiscovery = createProject("reactive-lib-service-discovery", "service-discovery")
  .dependsOn(common)
  .settings(serviceDiscoveryAssemblySettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    assemblyInclude("akka-dns", "service-locator-dns"),
    assemblyExcludeLocal(Seq("common"))
  )

lazy val serviceDiscoveryLagom13Java = createProject("reactive-lib-service-discovery-lagom13-java", "service-discovery-lagom13-java")
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings)
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
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client"   % Versions.lagom13 % "provided"
    ),
    assemblyInclude(),
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom14Java = createProject("reactive-lib-service-discovery-lagom14-java", "service-discovery-lagom14-java")
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings)
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
  .settings(serviceDiscoveryAssemblySettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom14 % "provided",
    ),
    assemblyInclude(),
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val akkaClusterBootstrap = createProject("reactive-lib-akka-cluster-bootstrap", "akka-cluster-bootstrap")
  .dependsOn(serviceDiscovery)
  .settings(serviceDiscoveryAssemblySettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"       %% "akka-cluster"        % Versions.akka      % "provided",
      "com.typesafe.akka"       %% "akka-testkit"        % Versions.akka      % "test",
      "net.databinder.dispatch" %% "dispatch-core"       % Versions.dispatch,
      "io.spray"                %% "spray-json"          % Versions.sprayJson
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    assemblyShadeRules in assembly ++= Seq(
      ShadeRule.rename("com.typesafe.netty.**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("dispatch.**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("io.netty.**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("org.asynchttpclient**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("org.reactivestreams**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("org.slf4j**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("spray.**" -> "com.lightbend.rp.internal.@0").inAll
    ),
    assemblyMergeStrategy in assembly := {
      case PathList(ps @ _*) if ps.last == "io.netty.versions.properties" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    assemblyExcludeLocal(Seq("common"), Seq("servicediscovery")),
    assemblyInclude(
      "async-http-client",
      "async-http-client-netty-utils",
      "dispatch-core",
      "netty-buffer",
      "netty-codec",
      "netty-codec-dns",
      "netty-codec-http",
      "netty-common",
      "netty-handler",
      "netty-reactive-streams",
      "netty-resolver",
      "netty-resolver-dns",
      "netty-transport",
      "netty-transport-native-epoll",
      "reactive-streams",
      "slf4j-api",
      "spray-json")
  )

lazy val playHttpBinding = createProject("reactive-lib-play-http-binding", "play-http-binding")
  .dependsOn(common)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    assemblyExcludeLocal(Seq("common"))
  )

lazy val secrets = createProject("reactive-lib-secrets", "secrets")
  .dependsOn(common)
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    assemblyExcludeLocal(Seq("common"))
  )
