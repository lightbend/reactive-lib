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
import akka.cluster.ClusterEvent.SubscriptionInitialStateMode
import scala.collection.immutable.Seq

object TestAkkaCluster {
  case class Call(name: String, args: Any*)
}

case class TestAkkaCluster(receiver: ActorRef, selfAddress: Address) extends AkkaCluster {
  import TestAkkaCluster._

  def join(address: Address): Unit =
    receiver ! Call("join", address)

  def subscribe(subscriber: ActorRef, initialStateMode: SubscriptionInitialStateMode, to: Class[_]*): Unit =
    receiver ! Call("subscribe", Seq(subscriber, initialStateMode) ++ to: _*)

  def systemName: String =
    selfAddress.system

  def unsubscribe(subscriber: ActorRef): Unit =
    receiver ! Call("unsubscribe", subscriber)
}