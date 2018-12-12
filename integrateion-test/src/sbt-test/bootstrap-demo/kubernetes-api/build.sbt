// https://github.com/akka/akka-management/tree/master/bootstrap-demo/kubernetes-api

import Dependencies._
import scala.sys.process.Process
import scala.util.control.NonFatal

ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.7"

// This is where we wire the freshly baked reactive-lib
ThisBuild / reactiveLibVersion := freshReactiveLibVersion

lazy val isOpenShift = {
  sys.props.get("test.openshift").isDefined
}

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
      akkaBootstrap, akkaServiceDiscoveryK8Api, akkaServiceDiscoveryConfig, akkaClusterHttp,
      akkaCluster, akkaClusterSharding, akkaClusterTools, akkaSlj4j,
      logback,
      scalaTest
    ),
    enableAkkaClusterBootstrap := true,
    akkaClusterBootstrapSystemName := "hoboken1",
    // this logic was taken from test.sh
    check := {
      val s = streams.value
      val nm = name.value
      val v = version.value

      if (!isOpenShift) {
        Process(s"$kubectl create namespace reactivelibtest1").!(s.log)
        Process(s"$kubectl apply -f kubernetes/rbac.yml").!(s.log)
        Process(s"$kubectl apply -f kubernetes/rp.yml").!(s.log)

        waitForPods(10, s.log)
        val p = findPodId(nm, s.log)
        checkMemberUp(p, 10, s.log)

        Process(s"$kubectl delete namespace reactivelibtest1").!(s.log)
      } else {
        Process(s"$kubectl apply -f kubernetes/rbac.yml").!(s.log)
        // work around: /rp-start: line 60: /opt/docker/bin/bootstrap-kapi-demo: Permission denied
        Process(s"$kubectl adm policy add-scc-to-user anyuid -z default").!(s.log)
        Process(s"$kubectl policy add-role-to-user system:image-builder system:serviceaccount:reactivelibtest1:default").!(s.log)

        Process(s"docker tag $nm:$v docker-registry-default.centralpark.lightbend.com/reactivelibtest1/$nm:$v").!(s.log)
        Process(s"docker push docker-registry-default.centralpark.lightbend.com/reactivelibtest1/$nm").!(s.log)
        s.log.info("applying openshift.yml")
        Process(s"$kubectl apply -f kubernetes/openshift.yml").!(s.log)

        waitForPods(10, s.log)
        val p = findPodId(nm, s.log)
        checkMemberUp(p, 10, s.log)
      }
    }
  )

def kubectl: String = {
  if (isOpenShift) "oc"
  else "kubectl"
}

def waitForPods(attempt: Int, log: Logger): Unit = {
  if (attempt == 0) sys.error("pods did not get ready in time")
  else {
    log.info("waiting for pods to get ready...")
    val lines = try {
      Process(s"$kubectl get pods --namespace reactivelibtest1").!!.lines.toList
    } catch {
      case NonFatal(_) => Nil
    }
    lines foreach { log.info(_: String) }
    if ((lines filter { _.contains("Running") }).size == 3) ()
    else {
      Thread.sleep(4000)
      waitForPods(attempt - 1, log)
    }
  }
}

def findPodId(nm: String, log: Logger): String = {
  val lines = Process(s"$kubectl get pods --namespace reactivelibtest1").!!.lines.toList
  lines foreach { log.info(_: String) }
  val xs = lines filter { s => s.contains("Running") && s.contains(nm) }
  val firstRow = xs.headOption.getOrElse(sys.error("pods not found!"))
  val firstColumn = firstRow.trim.split(" ").toList
    .headOption.getOrElse(sys.error("pods not found!"))
  firstColumn
}

def checkMemberUp(p: String, attempt: Int, log: Logger): Unit = {
  if (attempt == 0) sys.error("3 MemberUp log events were not found")
  else {
    log.info("checking for MemberUp logs...")
    val lines = try {
      Process(s"$kubectl logs $p --namespace reactivelibtest1").#|(Process("grep MemberUp")).!!.lines.toList
    } catch {
      case NonFatal(_) => Nil
    }
    lines foreach { log.info(_: String) }
    if (lines.size == 3) ()
    else {
      Thread.sleep(3000)
      checkMemberUp(p, attempt - 1, log)
    }
  }
}
