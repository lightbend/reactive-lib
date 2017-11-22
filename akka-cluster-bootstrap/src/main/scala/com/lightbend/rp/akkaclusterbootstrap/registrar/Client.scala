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

package com.lightbend.rp.akkaclusterbootstrap.registrar

import akka.actor.ActorSystem
import com.lightbend.rp.akkaclusterbootstrap.Settings
import com.lightbend.rp.servicediscovery.scaladsl.ServiceLocator
import dispatch._
import java.nio.charset.StandardCharsets
import java.util.UUID

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import spray.json._
import JsonSupport._

trait Client {
  def refresh(topic: String, id: UUID, name: String): Future[RefreshResult]
  def register(topic: String, id: UUID, name: String): Future[Option[Record]]
  def remove(topic: String, id: UUID, name: String): Future[Boolean]
}

class DispatchClient(system: ActorSystem) extends Client {
  private implicit val actorSystem = system
  private implicit val settings = Settings(system)

  import dispatch.Defaults._

  def refresh(topic: String, id: UUID, name: String): Future[RefreshResult] =
    for {
      registrar <- findRegistrar()

      body = JsObject("registrations" -> JsArray(JsObject("id" -> JsString(id.toString), "name" -> JsString(name))))

      req = (registrar.POST / "topics" / topic / "refresh")
        .setContentType("application/json", StandardCharsets.UTF_8)
        .setBody(body.compactPrint)

      response <- Http.default(req OK as.String)

      parsed <- Try(response.parseJson.convertTo[RefreshResult]) match {
        case Failure(f) =>
          Future.failed(new IllegalArgumentException(s"Failed to parse into Record: $response", f))
        case Success(s) =>
          Future.successful(s)
      }
    } yield parsed

  def register(topic: String, id: UUID, name: String): Future[Option[Record]] =
    for {
      registrar <- findRegistrar()

      body = JsObject("id" -> JsString(id.toString), "name" -> JsString(name))

      req = (registrar.POST / "topics" / topic / "register")
        .setContentType("application/json", StandardCharsets.UTF_8)
        .setBody(body.compactPrint)

      response <- Http.default(req)

      // if the request was successful (indicated via status code), the holding period
      // isn't active. thus, we want to fail the Future if parsing fails indicating a
      // general registrar issue

      // if the request was unsuccessful (indicated via status code), the holding period
      // is active thus we're a successful `None` so that ClusterManager can act accordingly

      parsed <- if (response.getStatusCode == 200)
        Try(response.getResponseBody(StandardCharsets.UTF_8).parseJson.convertTo[Record]) match {
          case Failure(f) =>
            Future.failed(new IllegalArgumentException(s"Failed to parse into Record: $response", f))
          case Success(s) =>
            Future.successful(Some(s))
        }
      else
        Future.successful(None)

    } yield parsed

  def remove(topic: String, id: UUID, name: String): Future[Boolean] =
    for {
      registrar <- findRegistrar()

      body = JsObject("id" -> JsString(id.toString), "name" -> JsString(name))

      request = (registrar.DELETE / "topics" / topic / "delete")
        .setContentType("application/json", StandardCharsets.UTF_8)
        .setBody(body.compactPrint)

      response <- Http.default(request)
    } yield response.getStatusCode == 200

  private def findRegistrar() =
    ServiceLocator
      .lookupOne(settings.registrarServiceName, settings.registrarEndpointName)
      .filter { uri =>
        if (uri.isEmpty) {
          system.log.error(s"Unable to find registrar: ${settings.registrarServiceName} ${settings.registrarEndpointName}")
        }

        uri.nonEmpty
      }
      .map(service => Req(_.setUrl(RawUri(service.get.uri).toString)))
}