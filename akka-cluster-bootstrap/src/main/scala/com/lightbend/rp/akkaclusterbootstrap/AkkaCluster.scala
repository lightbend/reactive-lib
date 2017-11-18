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

import akka.actor.{ ActorRef, Address }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.SubscriptionInitialStateMode

/**
 * Exposes only the methods used by ClusterManager of AkkaCluster so that a mock implementation can be provided
 */
trait AkkaCluster {
  def join(address: Address): Unit
  def selfAddress: Address
  def subscribe(subscriber: ActorRef, initialStateMode: SubscriptionInitialStateMode, to: Class[_]*): Unit
  def systemName: String
  def unsubscribe(subscriber: ActorRef): Unit
}

object AkkaCluster {
  def apply(cluster: Cluster): AkkaCluster = new AkkaCluster {
    def join(address: Address): Unit = cluster.join(address)

    def selfAddress: Address = cluster.selfAddress

    def subscribe(subscriber: ActorRef, initialStateMode: SubscriptionInitialStateMode, to: Class[_]*): Unit =
      cluster.subscribe(subscriber, initialStateMode, to: _*)

    def systemName: String = cluster.system.name

    def unsubscribe(subscriber: ActorRef): Unit = cluster.unsubscribe(subscriber)
  }
}