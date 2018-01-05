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

import org.scalatest.{ Matchers, WordSpec }

class ModuleSpec extends WordSpec with Matchers {
  "parse" should {
    "work" in {
      Module.parse("") shouldBe Set.empty

      Module.parse("potato") shouldBe Set.empty

      Module.parse("akka-cluster-bootstrapping") shouldBe Set(AkkaClusterBootstrapModule)

      Module.parse("akka-cluster-bootstrapping,akka-management,common,play-http-binding,secrets,service-discovery,status") shouldBe Set(
        AkkaClusterBootstrapModule,
        AkkaManagementModule,
        CommonModule,
        PlayHttpBindingModule,
        SecretsModule,
        ServiceDiscoveryModule,
        StatusModule)
    }
  }
}
