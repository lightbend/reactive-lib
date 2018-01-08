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

package com.lightbend.rp.common

sealed trait Module

case object AkkaClusterBootstrapModule extends Module
case object AkkaManagementModule extends Module
case object CommonModule extends Module
case object PlayHttpBindingModule extends Module
case object SecretsModule extends Module
case object ServiceDiscoveryModule extends Module
case object StatusModule extends Module

object Module {
  def enabled(m: Module): Boolean =
    active.contains(m)

  lazy val active: Set[Module] =
    parse(sys.env.getOrElse("RP_MODULES", ""))

  private[common] def parse(modules: String): Set[Module] = {
    val names = modules.split(',').toSet

    def ifActive(n: String, m: Module): Set[Module] =
      if (names.contains(n)) Set(m) else Set.empty

    ifActive("akka-cluster-bootstrapping", AkkaClusterBootstrapModule) ++
      ifActive("akka-management", AkkaManagementModule) ++
      ifActive("common", CommonModule) ++
      ifActive("play-http-binding", PlayHttpBindingModule) ++
      ifActive("secrets", SecretsModule) ++
      ifActive("service-discovery", ServiceDiscoveryModule) ++
      ifActive("status", StatusModule)
  }
}
