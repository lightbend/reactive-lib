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

import akka.actor._
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

final class Settings(system: ExtendedActorSystem) extends Extension {
  private val status = system.settings.config.getConfig("com.lightbend.platform-tooling.status")

  val healthChecks: Seq[String] = status.getStringList("health-checks").asScala.toVector

  val readinessChecks: Seq[String] = status.getStringList("readiness-checks").asScala.toVector
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def get(system: ActorSystem): Settings = super.get(system)

  override def lookup: Settings.type = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system)
}
