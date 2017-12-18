/*
 * Copyright 2017 Lightbend, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.rp.akkaclusterbootstrap.kubernetes

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.lightbend.rp.akkaclusterbootstrap.{ AkkaManagementPortName, Settings }
import com.lightbend.rp.common._
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.TrustStoreConfig
import java.nio.file.Paths

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import JsonFormat._
import ServiceDiscovery._

object ClusterServiceDiscovery {
  private[kubernetes] def targets(podList: PodList, name: String): Seq[ResolvedTarget] =
    for {
      item <- podList.items
      container <- item.spec.containers.find(_.name == name)
      port <- container.ports.find(_.name == AkkaManagementPortName)
      ip <- item.status.podIP
    } yield ResolvedTarget(ip, Some(port.containerPort))
}

/**
 * An alternative implementation that uses the Kubernetes API. Currently,
 * reactive-cli specifies this via arguments when it generates resources
 * for Kubernets targets. Thus, the default implementation for reactive-lib
 * remains one based on the service locator.
 *
 * The main advantage of this is that we can discover pods that aren't yet ready,
 * perhaps they have health checks that rely on Akka Cluster being up.
 */
class ClusterServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {
  import ClusterServiceDiscovery._
  import system.dispatcher

  private val http = Http()(system)

  private val settings = Settings(system)

  private implicit val mat: ActorMaterializer = ActorMaterializer()(system)

  private val sslConfig =
    AkkaSSLConfig()(system)
      .mapSettings(s =>
        s.withTrustManagerConfig(
          s.trustManagerConfig
            .withTrustStoreConfigs(
              Seq(
                TrustStoreConfig(data = None, filePath = Some(KubernetesCAPath))
                  .withStoreType("PEM")))))

  private val sslContext = http.createClientHttpsContext(sslConfig)

  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    for {
      token <- apiToken()

      request <- optionToFuture(
        podRequest(token, Namespace.active.getOrElse(kubernetes.DefaultNamespace), name),
        "Unable to form request; check Kubernetes environment")

      response <- http.singleRequest(request, sslContext)

      entity <- response.entity.toStrict(resolveTimeout)

      podList <- Unmarshal(entity).to[PodList]

    } yield Resolved(name, targets(podList, name))

  private def apiToken() =
    FileIO.fromPath(Paths.get(KubernetesToken))
      .runFold("")(_ + _.utf8String)
      .recover { case _: Throwable => "" }

  private def optionToFuture[T](option: Option[T], failMsg: String): Future[T] =
    option.fold(Future.failed[T](new NoSuchElementException(failMsg)))(Future.successful)

  private def podRequest(token: String, namespace: String, name: String) = {
    for {
      platform <- Platform.active

      if platform == Kubernetes

      host <- sys.env.get("KUBERNETES_SERVICE_HOST")
      portStr <- sys.env.get("KUBERNETES_SERVICE_PORT")
      port <- Try(portStr.toInt).toOption
    } yield {
      val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods"
      val query = Uri.Query("labelSelector" -> settings.podLabelSelector(name))
      val uri = Uri.from(scheme = "https", host = host, port = port)
        .withPath(path)
        .withQuery(query)

      HttpRequest(uri = uri, headers = Seq(Authorization(OAuth2BearerToken(token))))
    }
  }
}
