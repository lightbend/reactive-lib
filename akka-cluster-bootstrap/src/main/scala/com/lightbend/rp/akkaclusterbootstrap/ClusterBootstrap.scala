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

package com.lightbend.rp.akkaclusterbootstrap

import java.util.UUID

import akka.actor._
import akka.cluster.Cluster
import com.lightbend.rp.akkaclusterbootstrap.registrar.DispatchClient

final class ClusterBootstrapImpl(system: ExtendedActorSystem) extends Extension {
  val cluster = Cluster(system)
  val id = UUID.randomUUID()

  if (cluster.settings.SeedNodes.nonEmpty) {
    system.log.warning("ClusterBootstrap is enabled but seed nodes are defined, no action will be taken")
  } else {
    system.log.info(s"Starting ClusterSupervisor: $id")

    system.actorOf(ClusterSupervisor.props(id, new DispatchClient(system), Cluster(system)))
  }
}

object ClusterBootstrap extends ExtensionId[ClusterBootstrapImpl] with ExtensionIdProvider {
  override def lookup: ClusterBootstrap.type = ClusterBootstrap
  override def createExtension(system: ExtendedActorSystem): ClusterBootstrapImpl = new ClusterBootstrapImpl(system)
}
