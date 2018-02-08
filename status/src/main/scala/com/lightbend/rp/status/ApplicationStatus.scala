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

package com.lightbend.rp.status

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.management.http.{ ManagementRouteProvider, ManagementRouteProviderSettings }
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }

import Directives._

class ApplicationStatus(system: ExtendedActorSystem) extends Extension with ManagementRouteProvider {
  private val settings = Settings(system)

  private val healthChecks =
    settings
      .healthChecks
      .map(c =>
        system
          .dynamicAccess
          .createInstanceFor[HealthCheck](c, Seq.empty)
          .getOrElse(throw new IllegalArgumentException(s"Incompatible HealthCheck class definition: $c")))

  private val readinessChecks =
    settings
      .readinessChecks
      .map(c =>
        system
          .dynamicAccess
          .createInstanceFor[ReadinessCheck](c, Seq.empty)
          .getOrElse(throw new IllegalArgumentException(s"Incompatible ReadinessCheck class definition: $c")))

  def routes(settings: ManagementRouteProviderSettings): Route = pathPrefix("platform-tooling") {
    import system.dispatcher

    concat(
      path("ping")(complete("pong!")),
      path("healthy")(complete(isHealthy.map(h => if (h) StatusCodes.OK else StatusCodes.ServiceUnavailable))),
      path("ready")(complete(isReady.map(r => if (r) StatusCodes.OK else StatusCodes.ServiceUnavailable))))
  }

  def isHealthy(implicit ec: ExecutionContext): Future[Boolean] =
    Future
      .sequence(healthChecks.map(_.healthy(system)))
      .map(_.forall(identity))

  def isReady(implicit ec: ExecutionContext): Future[Boolean] =
    Future
      .sequence(readinessChecks.map(_.ready(system)))
      .map(_.forall(identity))
}

object ApplicationStatus extends ExtensionId[ApplicationStatus] with ExtensionIdProvider {
  override def lookup: ApplicationStatus.type = ApplicationStatus
  override def get(system: ActorSystem): ApplicationStatus = super.get(system)
  override def createExtension(system: ExtendedActorSystem): ApplicationStatus = new ApplicationStatus(system)
}
