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

package com.lightbend.rp.servicediscovery.scaladsl

import akka.actor._
import com.typesafe.config.Config
import java.net.URI
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.duration.{ Duration, FiniteDuration, MILLISECONDS }

final class Settings(system: ExtendedActorSystem) extends Extension {
  private val serviceDiscovery = system.settings.config.getConfig("com.lightbend.platform-tooling.service-discovery")

  val askTimeout: FiniteDuration = duration(serviceDiscovery, "ask-timeout")

  val externalServiceAddresses: Map[String, Seq[URI]] = {
    val data = serviceDiscovery.getObject("external-service-addresses")
    val config = data.toConfig

    data
      .keySet()
      .asScala
      .map(k => k -> config.getStringList(k).asScala.toVector.map(new URI(_)))
      .toMap
  }

  val externalServiceAddressLimit: Int = serviceDiscovery.getInt("external-service-address-limit")

  val retryDelays: Seq[FiniteDuration] =
    serviceDiscovery
      .getDurationList("retry-delays", MILLISECONDS)
      .asScala
      .toVector
      .map(Duration(_, MILLISECONDS))

  private def duration(config: Config, key: String): FiniteDuration =
    Duration(config.getDuration(key, MILLISECONDS), MILLISECONDS)
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def get(system: ActorSystem): Settings = super.get(system)

  override def lookup: Settings.type = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system)
}
