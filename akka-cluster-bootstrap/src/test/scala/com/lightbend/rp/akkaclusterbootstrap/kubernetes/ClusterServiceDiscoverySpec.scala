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

package com.lightbend.rp.akkaclusterbootstrap.kubernetes

import org.scalatest.{ Matchers, WordSpec }
import PodList._
import akka.discovery.ServiceDiscovery.ResolvedTarget

class ClusterServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val podList = PodList(
        List(
          Item(
            Spec(
              List(
                Container(
                  "akka-cluster-tooling-example",
                  List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
            Status("172.17.0.4")),

          Item(
            Spec(
              List(
                Container(
                  "akka-cluster-tooling-other-example",
                  List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
            Status("172.17.0.6")),

          Item(
            Spec(
              List(
                Container(
                  "akka-cluster-tooling-another-example",
                  List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
            Status("172.17.0.7"))))

      ClusterServiceDiscovery.targets(podList, "akka-cluster-tooling-example") shouldBe List(
        ResolvedTarget("172.17.0.4", Some(10001)))
    }
  }
}
