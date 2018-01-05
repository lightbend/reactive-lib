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

import akka.actor._
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.{ ClusterBootstrap => AkkaClusterBootstrap }
import com.lightbend.rp.common.{ AkkaClusterBootstrapModule, Module, Platform }

final class ClusterBootstrap(system: ExtendedActorSystem) extends Extension {
  if (Module.enabled(AkkaClusterBootstrapModule)) {
    val cluster = Cluster(system)

    if (cluster.settings.SeedNodes.nonEmpty) {
      system.log.warning("ClusterBootstrap is enabled but seed nodes are defined, no action will be taken")
    } else if (Platform.active.isEmpty) {
      system.log.info("ClusterBootstrap is enabled but no active platform detected (i.e. running locally), no action will be taken")
    } else {
      AkkaClusterBootstrap(system).start()
    }
  }
}

object ClusterBootstrap extends ExtensionId[ClusterBootstrap] with ExtensionIdProvider {
  override def lookup: ClusterBootstrap.type = ClusterBootstrap
  override def get(system: ActorSystem): ClusterBootstrap = super.get(system)
  override def createExtension(system: ExtendedActorSystem): ClusterBootstrap = new ClusterBootstrap(system)
}
