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

class SocketBindingSpec extends WordSpec with Matchers {
  val reader = new SocketBinding.EnvironmentReader(
    Map(
      "RP_ENDPOINTS" -> "EP0-V9,EP1-V10",
      "RP_ENDPOINT_EP0-V9_PORT" -> "443",
      "RP_ENDPOINT_EP1-V10_PORT" -> "80",
      "RP_ENDPOINT_EP1-V10_BIND_PORT" -> "81",
      "RP_ENDPOINT_EP0-V9_HOST" -> "192.168.1.5",
      "RP_ENDPOINT_EP1-V10_HOST" -> "192.168.1.10",
      "RP_ENDPOINT_EP1-V10_BIND_HOST" -> "0.0.0.0"))

  "bindHost" should {
    "fallback to host when missing" in {
      reader.bindHost("ep0", "localhost") shouldBe "192.168.1.5"
      reader.bindHost("ep0-v9", "localhost") shouldBe "192.168.1.5"
    }

    "work when present" in {
      reader.bindHost("ep1", "localhost") shouldBe "0.0.0.0"
      reader.bindHost("ep1-v10", "localhost") shouldBe "0.0.0.0"
    }

    "fallback to default when both missing" in {
      reader.bindHost("ep3", "localhost") shouldBe "localhost"
    }
  }

  "host" should {
    "work when present" in {
      reader.host("ep0", "localhost") shouldBe "192.168.1.5"
      reader.host("ep0-v9", "localhost") shouldBe "192.168.1.5"
      reader.host("ep1", "localhost") shouldBe "192.168.1.10"
      reader.host("ep1-v10", "localhost") shouldBe "192.168.1.10"
    }

    "fallback to default when both missing" in {
      reader.bindHost("ep3", "localhost") shouldBe "localhost"
    }
  }

  "bindPort" should {
    "fallback to port when missing" in {
      reader.bindPort("ep0", 123) shouldBe 443
      reader.bindPort("ep0-v9", 123) shouldBe 443
    }

    "work when present" in {
      reader.bindPort("ep1", 456) shouldBe 81
      reader.bindPort("ep1-v10", 456) shouldBe 81
    }

    "fallback to default when both missing" in {
      reader.bindPort("ep3", 789) shouldBe 789
    }
  }

  "port" should {
    "fallback to port when missing" in {
      reader.port("ep0", 123) shouldBe 443
      reader.port("ep0-v9", 123) shouldBe 443
      reader.port("ep1", 123) shouldBe 80
      reader.port("ep1-v10", 123) shouldBe 80
    }

    "work when present" in {
      reader.port("ep1", 456) shouldBe 80
      reader.port("ep1-v10", 456) shouldBe 80
    }

    "fallback to default when both missing" in {
      reader.port("ep3", 789) shouldBe 789
    }
  }
}
