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

import akka.actor.{ Actor, ExtendedActorSystem, Extension, ExtensionKey }
import com.typesafe.config.Config

import scala.concurrent.duration.{ Duration, FiniteDuration, MILLISECONDS }

object Settings extends ExtensionKey[Settings]

class Settings private (system: ExtendedActorSystem) extends Extension {
  private val config = system.settings.config
  private val settings = config.getConfig("rp.akka-cluster-bootstrap")

  val registrarJoinTimeout: FiniteDuration = duration(settings, "registrar-join-timeout")
  val registrarJoinTries: Int = settings.getInt("registrar-join-tries")
  val registrarRegisterTimeout: FiniteDuration = duration(settings, "registrar-registration-timeout")
  val registrarRegistrationAttempts: Int = settings.getInt("registrar-registration-attempts")
  val registrarRegistrationFailTimeout: FiniteDuration = duration(settings, "registrar-registration-fail-timeout")
  val registrarServiceName: String = settings.getString("registrar-service-name")
  val registrarEndpointName: String = settings.getString("registrar-endpoint-name")
  val registrarTopicPattern: String = settings.getString("registrar-topic-pattern")

  private def duration(config: Config, key: String): FiniteDuration =
    Duration(config.getDuration(key, MILLISECONDS), MILLISECONDS)
}

trait ActorSettings { this: Actor =>
  protected val settings: Settings = Settings(context.system)
}
