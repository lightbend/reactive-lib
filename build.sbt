import scala.collection.{ Seq => DefaultSeq }
import scala.collection.immutable.Seq
import scala.xml.{ Node => XmlNode, NodeSeq => XmlNodeSeq, _ }
import scala.xml.transform.{ RewriteRule, RuleTransformer }
import ReleaseTransformations._
import sbtassembly.MergeStrategy

lazy val Versions = new {
  val akka                      = "2.5.7"
  val akkaDns                   = "2.4.2"
  val akkaManagement            = "0.8.0"
  val lagom13                   = "1.3.0"
  val lagom14                   = "1.4.0-RC1"
  val play25                    = "2.5.0"
  val play26                    = "2.6.0"
  val scala211                  = "2.11.11"
  val scala212                  = "2.12.3"
  val scalaJava8Compat          = "0.8.0"
  val scalaTest                 = "3.0.1"
  val typesafeConfig            = "1.3.1"
}

lazy val scalaVersionMajor = SettingKey[String]("scala-version-major")

scalaVersionMajor in ThisBuild := (scalaVersion.value).split('.').dropRight(1).mkString(".")

//lazy val pomExclude = SettingKey[DefaultSeq[(String, String, String)]]("pom-exclude")

/**
 * This method excludes any files with a subdirectory sequence of `names` in the path. This
 * is used to ensure that local project files from dependencies don't end up in the assembled
 * jar. For instance, you call this with "common" to ensure no "com/lightbend/rp/common"
 * directories end up in your jar.
 */
/*
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
*/

def semanticVersioningMajor(version: String) =
  version
    .reverse
    .dropWhile(_ != '.')
    .dropWhile(_ == '.')
    .reverse

def createProject(id: String, path: String) = Project(id, file(path))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
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
    /*
    pomExclude := Vector.empty,
    pomPostProcess := { (node: XmlNode) =>
      new RuleTransformer(
        new RewriteRule {
          val excluded = pomExclude.value

          override def transform(node: XmlNode): XmlNodeSeq = node match {
            case e: Elem if e.label == "dependency" =>
              val organization = e.child.filter(_.label == "groupId").flatMap(_.text).mkString
              val artifact = e.child.filter(_.label == "artifactId").flatMap(_.text).mkString
              val version = e.child.filter(_.label == "version").flatMap(_.text).mkString

              if (excluded.contains((organization, artifact, version)))
                Comment(s"provided dependency $organization#$artifact;$version has been omitted")
              else
                node
            case _ => node
          }
        })
        .transform(node)
        .head
    }
    */
  )

lazy val root = createProject("reactive-lib", ".")
/* FIXME: enabling this causes publishLocal to fail with "Ivy file not found in cache" error message
  .settings(
    publishArtifact := false
  )
*/
  .aggregate(
    akkaClusterBootstrap,
    akkaManagement,
    common,
    playHttpBinding,
    secrets,
    serviceDiscovery,
    serviceDiscoveryLagom13Java,
    serviceDiscoveryLagom13Scala,
    serviceDiscoveryLagom14Java,
    serviceDiscoveryLagom14Scala,
    status
  )

lazy val shadedAkkaDns = Project(id = "shaded-akka-dns", base = file("shaded-akka-dns"))
  .settings(
    organization := "com.lightbend.rp",
    organizationName := "Lightbend, Inc.",
    scalaVersion := Versions.scala211,
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212),
    test in assembly := {},
    assemblyOption in assembly ~= {
      _.copy(includeScala = false)
    },
    assemblyJarName in assembly := {
      s"${name.value}-${scalaVersionMajor.value}-${version.value}.jar"
    },
    target in assembly := {
      baseDirectory.value / "target" / scalaVersionMajor.value
    },
    packageBin in Compile := (assembly in Compile).value,
    addArtifact(Artifact("shaded-akka-dns", "assembly"), sbtassembly.AssemblyKeys.assembly),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("akka.io.AsyncDnsResolver**" -> "com.lightbend.rp.internal.@0").inAll,
      ShadeRule.rename("ru.smslv**" -> "com.lightbend.rp.internal.@0").inAll
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"        %% "akka-actor"          % Versions.akka              % "provided",
      "ru.smslv.akka"            %% "akka-dns"            % Versions.akkaDns
    ),
    assemblyExcludedJars in assembly := {
      (fullClasspath in assembly).value.filterNot(_.data.getName.startsWith("akka-dns"))
    },
    assemblyMergeStrategy in assembly := {
      case v @ "reference.conf" =>
        // Apply custom merge strategy to `reference.conf` within Akka DNS jar to rename configured classes from
        // `ru.smslv` package to `com.lightbend.rp.internal.ru.smslv`.
        new MergeStrategy {
          override val name: String = "Akka DNS reference.conf merge"
          override def apply(tempDir: File, path: String, files: scala.Seq[File]): Either[String, Seq[(File, String)]] = {
            val (source, _, _, _) = sbtassembly.AssemblyUtils.sourceOfFileForMerge(tempDir, files.head)

            // Only apply this strategy if the reference.conf has indeed come from the Akka DNS jar, else fallback to
            // existing strategy
            if (source.getName.startsWith("akka-dns") && source.getName.endsWith(".jar")) {
              import scala.collection.JavaConverters._
              val file = MergeStrategy.createMergeTarget(tempDir, path)
              val lines = java.nio.file.Files.readAllLines(files.head.toPath).asScala
              val linesShaded = lines.map(_.replace("ru.smslv", "com.lightbend.rp.internal.ru.smslv"))
              linesShaded.foreach { v =>
                IO.append(file, v)
                IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
              }
              Right(Seq(file -> path))
            } else {
              val existingStrategy = (assemblyMergeStrategy in assembly).value
              existingStrategy(v).apply(tempDir, path, files).map(_.toList)
            }
          }
        }

      case v =>
        val existingStrategy = (assemblyMergeStrategy in assembly).value
        existingStrategy(v)
    },
    pomPostProcess := { (node: XmlNode) =>
      new RuleTransformer(
        new RewriteRule {
          override def transform(node: XmlNode): XmlNodeSeq = node match {
            case e: Elem if e.label == "dependency" =>
              val organization = get(e, "groupId")
              val artifact = get(e, "artifactId")
              val version = e.child.filter(_.label == "version").flatMap(_.text).mkString

              if (organization == "ru.smslv.akka" && artifact.startsWith("akka-dns"))
                Comment(s"provided dependency $organization#$artifact;$version has been omitted")
              else
                node
            case _ => node
          }

          private def get(current: Elem, childElementName: String): String =
            current.child.filter(_.label == childElementName).flatMap(_.text).mkString
        })
        .transform(node)
        .head
    }
  )

lazy val common = createProject("reactive-lib-common", "common")
  .settings(
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

/*
lazy val serviceDiscoveryAssemblySettings = Vector(
  libraryDependencies ++= Seq(
    "com.typesafe.akka"        %% "akka-actor"          % Versions.akka              % "provided",
    "ru.smslv.akka"            %% "akka-dns"            % Versions.akkaDns
  ),

  assemblyShadeRules in assembly ++= Seq(
    ShadeRule.rename("akka.io.AsyncDnsResolver**" -> "com.lightbend.rp.internal.@0").inAll,
    ShadeRule.rename("ru.smslv**" -> "com.lightbend.rp.internal.@0").inAll
  ),

  pomExclude += ("ru.smslv.akka", s"akka-dns_${semanticVersioningMajor(scalaVersion.value)}", Versions.akkaDns),

  addArtifact(artifact in (Compile, assembly), assembly),

  assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),

  assemblyJarName in assembly :=
    s"${name.value}_${semanticVersioningMajor(scalaVersion.value)}-${version.value}.jar",

  packageBin in Compile := (assembly in Compile).value)
*/

lazy val temporaryDependenciesSettings = Vector(
  libraryDependencies ++= Seq(
    "com.typesafe.akka"        %% "akka-actor"          % Versions.akka              % "provided",
    "ru.smslv.akka"            %% "akka-dns"            % Versions.akkaDns
  ))

lazy val serviceDiscovery = createProject("reactive-lib-service-discovery", "service-discovery")
  .dependsOn(common)
  .settings(
    unmanagedJars in Compile ++= Seq(
      (assembly in Compile in shadedAkkaDns).value
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"        %% "akka-actor"          % Versions.akka    % "provided",
      "com.typesafe.akka"        %% "akka-testkit"        % Versions.akka    % "test",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
  )

lazy val serviceDiscoveryLagom13Java = createProject("reactive-lib-service-discovery-lagom13-java", "service-discovery-lagom13-java")
  //.enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  //.settings(serviceDiscoveryAssemblySettings: _*)
  .settings(temporaryDependenciesSettings: _*)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom13 % "provided"
    )
    //assemblyInclude(),
    //assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom13Scala = createProject("reactive-lib-service-discovery-lagom13-scala", "service-discovery-lagom13-scala")
  //.enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  //.settings(serviceDiscoveryAssemblySettings: _*)
  .settings(temporaryDependenciesSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client"   % Versions.lagom13 % "provided"
    )
    //assemblyInclude(),
    //assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom14Java = createProject("reactive-lib-service-discovery-lagom14-java", "service-discovery-lagom14-java")
  //.enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  //.settings(serviceDiscoveryAssemblySettings: _*)
  .settings(temporaryDependenciesSettings: _*)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom14 % "provided"
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
    //assemblyInclude(),
    //assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val serviceDiscoveryLagom14Scala = createProject("reactive-lib-service-discovery-lagom14-scala", "service-discovery-lagom14-scala")
  //.enablePlugins(AssemblyPlugin)
  .dependsOn(serviceDiscovery)
  //.settings(serviceDiscoveryAssemblySettings: _*)
  .settings(temporaryDependenciesSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.lagom14 % "provided",
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
    //assemblyInclude(),
    //assemblyExcludeLocal(Seq("common"), Seq("servicediscovery", "javadsl"), Seq("servicediscovery", "scaladsl"))
  )

lazy val akkaClusterBootstrap = createProject("reactive-lib-akka-cluster-bootstrap", "akka-cluster-bootstrap")
  .dependsOn(akkaManagement, serviceDiscovery, status)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % Versions.akkaManagement,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.akkaManagement,
      "com.typesafe.akka"             %% "akka-testkit"                      % Versions.akka              % "test",
      "com.typesafe.akka"             %% "akka-cluster"                      % Versions.akka              % "provided",
      "ru.smslv.akka"                 %% "akka-dns"                          % Versions.akkaDns
    ),
    crossScalaVersions := Vector(Versions.scala211, Versions.scala212)
    //pomExclude += ("ru.smslv.akka", s"akka-dns_${semanticVersioningMajor(scalaVersion.value)}", Versions.akkaDns)
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
