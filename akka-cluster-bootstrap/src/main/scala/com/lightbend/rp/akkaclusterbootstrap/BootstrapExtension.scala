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
import akka.http.scaladsl.Http
import com.lightbend.rp.servicediscovery.scaladsl._

final class BootstrapExtensionImpl(system: ExtendedActorSystem) extends Extension {
  val cluster = Cluster(system)
  val http = Http(system)
  val serviceLocator = ServiceLocator(system)
}

object BootstrapExtension extends ExtensionId[BootstrapExtensionImpl] with ExtensionIdProvider {
  override def lookup = BootstrapExtension
  override def createExtension(system: ExtendedActorSystem) = new BootstrapExtensionImpl(system)
  override def get(system: ActorSystem): BootstrapExtensionImpl = super.get(system)
}