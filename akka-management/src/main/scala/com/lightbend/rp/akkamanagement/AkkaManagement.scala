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

package com.lightbend.rp.akkamanagement

import akka.actor._
import akka.management.{ AkkaManagement => AkkaMgmt }
import com.lightbend.rp.common.{ AkkaManagementModule, Module, Platform }

final class AkkaManagement(system: ExtendedActorSystem) extends Extension {
  if (Module.enabled(AkkaManagementModule)) {
    if (Platform.active.isEmpty) {
      system.log.info("AkkaManagement is enabled but no active platform detected (i.e. running locally), no action will be taken")
    } else {
      AkkaMgmt(system).start()
    }
  }
}

object AkkaManagement extends ExtensionId[AkkaManagement] with ExtensionIdProvider {
  override def lookup: AkkaManagement.type = AkkaManagement
  override def get(system: ActorSystem): AkkaManagement = super.get(system)
  override def createExtension(system: ExtendedActorSystem): AkkaManagement = new AkkaManagement(system)
}
