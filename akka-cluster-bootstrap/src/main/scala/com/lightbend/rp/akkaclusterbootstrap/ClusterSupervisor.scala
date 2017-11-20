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
import akka.cluster._
import com.lightbend.rp.akkaclusterbootstrap.registrar._
import java.util.UUID

object ClusterSupervisor {
  def props(id: UUID, client: Client, cluster: Cluster): Props =
    Props(new ClusterSupervisor(id, client, cluster))
}

/**
 * Watches the ClusterManager actor and terminates if it fails.
 */
class ClusterSupervisor(id: UUID, client: Client, cluster: Cluster) extends Actor with ActorSettings {
  private val manager = context.watch(context.actorOf(ClusterManager.props(id, client, AkkaCluster(cluster))))

  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def receive: Receive = {
    case Terminated(`manager`) =>
      cluster.leave(cluster.selfAddress)

      cluster.registerOnMemberRemoved {
        context.system.terminate()
      }
  }
}
