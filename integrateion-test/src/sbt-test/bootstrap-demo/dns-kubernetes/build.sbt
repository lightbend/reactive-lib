// https://github.com/akka/akka-management/tree/master/bootstrap-demo/kubernetes-dns

import Dependencies._
import scala.sys.process.Process
import scala.util.control.NonFatal

ThisBuild / version      := "0.1.5"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.7"

lazy val isOpenShift = {
  sys.props.get("test.openshift").isDefined
}

lazy val check = taskKey[Unit]("check")

lazy val root = (project in file("."))
  .enablePlugins(SbtReactiveAppPlugin)
  .settings(
    name := "bootstrap-dns-demo",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
    ),
    // This is where we wire the freshly baked reactive-lib
    reactiveLibVersion := freshReactiveLibVersion,
    libraryDependencies ++= Seq(
      akkaManagement,
      akkaClusterHttp,
      akkaCluster,
      akkaClusterSharding,
      akkaClusterTools,
      akkaDiscoveryDns,
      akkaSlj4j,
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

      try {
        if (!isOpenShift) {
          Process(s"$kubectl create namespace reactivelibtest1").!(s.log)
          Process(s"$kubectl apply -f kubernetes/akka-cluster.yml").!(s.log)

          waitForPods(3, 10, s.log)
          val p = findPodId(nm, s.log)
          checkMemberUp(p, 10, s.log)
        } else {
          // work around: /rp-start: line 60: /opt/docker/bin/bootstrap-kapi-demo: Permission denied
          Process(s"$kubectl adm policy add-scc-to-user anyuid -z default").!(s.log)
          Process(s"$kubectl policy add-role-to-user system:image-builder system:serviceaccount:reactivelibtest1:default").!(s.log)

          Process(s"docker tag $nm:$v docker-registry-default.centralpark.lightbend.com/reactivelibtest1/$nm:$v").!(s.log)
          Process(s"docker push docker-registry-default.centralpark.lightbend.com/reactivelibtest1/$nm").!(s.log)
          s.log.info("applying openshift.yml")
          Process(s"$kubectl apply -f kubernetes/openshift.yml").!(s.log)

          waitForPods(3, 10, s.log)
          val p = findPodId(nm, s.log)
          checkMemberUp(p, 10, s.log)
        }
      } finally {
        Process(s"$kubectl delete services,pods,deployment --all --namespace reactivelibtest1").!(s.log)
        // Process(s"$kubectl delete namespace reactivelibtest1").!(s.log)
        waitForPods(0, 10, s.log)
      }
    }
  )

  def kubectl: String = {
  if (isOpenShift) "oc"
  else "kubectl"
}

def waitForPods(expected: Int, attempt: Int, log: Logger): Unit = {
  if (attempt == 0) {
    val lines = try {
      Process(s"$kubectl describe pods --namespace reactivelibtest1").!!.lines.toList
    } catch {
      case NonFatal(_) => Nil
    }
    lines foreach { log.info(_: String) }
    sys.error("pods did not get ready in time")
  }
  else {
    log.info("waiting for pods to get ready...")
    val lines = try {
      Process(s"$kubectl get pods --namespace reactivelibtest1").!!.lines.toList
    } catch {
      case NonFatal(_) => Nil
    }
    lines foreach { log.info(_: String) }
    if ((lines filter { _.contains("Running") }).size == expected) ()
    else {
      Thread.sleep(4000)
      waitForPods(expected, attempt - 1, log)
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
    lines foreach {
      log.info(_: String)
    }
    if (lines.size == 3) ()
    else {
      if (attempt == 1)
        (try {
          Process(s"$kubectl logs $p --namespace reactivelibtest1").!!.lines.toList
        } catch {
          case NonFatal(_) => Nil
        }) foreach {
          log.info(_: String)
        }
      else ()
      Thread.sleep(3000)
      checkMemberUp(p, attempt - 1, log)
    }
  }
}
