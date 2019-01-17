// https://github.com/akka/akka-management/tree/master/bootstrap-demo/kubernetes-api

import Dependencies._
import scala.sys.process.Process
import scala.util.control.NonFatal
import com.typesafe.sbt.packager.docker._

ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.7"

// This is where we wire the freshly baked reactive-lib
ThisBuild / reactiveLibVersion := freshReactiveLibVersion

lazy val check = taskKey[Unit]("check")

lazy val root = (project in file("."))
  .enablePlugins(SbtReactiveAppPlugin)
  .settings(
    name := "bootstrap-kapi-demo",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
    ),
    libraryDependencies ++= Seq(
      akkaCluster,
      akkaClusterSharding,
      akkaClusterTools,

      // Theses are currently introduced by reactive-lib
      // akkaBootstrap,
      // akkaServiceDiscoveryK8Api,

      // akkaServiceDiscoveryConfig,
      // akkaClusterHttp,

      akkaSlj4j,
      logback,
      scalaTest
    ),
    enableAkkaClusterBootstrap := true,
    akkaClusterBootstrapSystemName := "hoboken1",
    dockerCommands := {
      val xs = dockerCommands.value
      xs flatMap {
        case x @ Cmd("ADD", _*) =>
          Vector(x, ExecCmd("RUN", "chmod", "a+x",
            s"${(Docker / defaultLinuxInstallLocation).value}/bin/${executableScriptName.value}"))
        case x => Vector(x)
      }
    },

    // this logic was taken from test.sh
    check := {
      val s = streams.value
      val nm = name.value
      val v = version.value
      val namespace = "reactivelibtest1"
      val kubectl = Deckhand.kubectl(s.log)
      val docker = Deckhand.docker(s.log)
      val yamlDir = baseDirectory.value / "kubernetes"

      try {
        if (!Deckhand.isOpenShift) {
          kubectl.tryCreate(s"namespace $namespace")
          kubectl.setCurrentNamespace(namespace)
          kubectl.apply(Deckhand.mustache(yamlDir / "rbac.mustache"),
            Map(
              "namespace"       -> namespace
            ))
          kubectl.apply(Deckhand.mustache(yamlDir / "akka-cluster.mustache"),
            Map(
              "image"           -> s"$nm:$v",
              "imagePullPolicy" -> "Never"
            ))
        } else {
          kubectl.command(s"policy add-role-to-user system:image-builder system:serviceaccount:$namespace:default")
          kubectl.apply(Deckhand.mustache(yamlDir / "rbac.mustache"),
            Map(
              "namespace"       -> namespace
            ))
          docker.tag(s"$nm:$v docker-registry-default.centralpark.lightbend.com/$namespace/$nm:$v")
          docker.push(s"docker-registry-default.centralpark.lightbend.com/$namespace/$nm")
          s.log.info("applying openshift.yml")
          kubectl.apply(Deckhand.mustache(yamlDir / "akka-cluster.mustache"),
            Map(
              "image"           -> s"docker-registry-default.centralpark.lightbend.com/$namespace/$nm:$v",
              "imagePullPolicy" -> "Always"
            ))
        }
        kubectl.waitForPods(3)
        kubectl.describe("pods")
        kubectl.checkAkkaCluster(3, _.contains(nm))
      } finally {
        kubectl.delete(s"services,pods,deployment --all --namespace $namespace")
        kubectl.waitForPods(0)
      }

    }
  )
