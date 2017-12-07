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
import scala.io.Source
import spray.json._

import PodList._

class JsonFormatSpec extends WordSpec with Matchers {
  "JsonFormat" should {
    val data = resourceAsString("pods.json")

    "work" in {
      JsonFormat
        .podListFormat
        .read(data.parseJson) shouldBe PodList(
          List(
            Item(
              Spec(
                List(
                  Container(
                    "akka-cluster-tooling-example",
                    List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
              Status(Some("172.17.0.4"))),

            Item(
              Spec(
                List(
                  Container(
                    "akka-cluster-tooling-example",
                    List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
              Status(Some("172.17.0.6"))),

            Item(
              Spec(
                List(
                  Container(
                    "akka-cluster-tooling-example",
                    List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
              Status(Some("172.17.0.7")))))
    }
  }

  private def resourceAsString(name: String): String =
    Source
      .fromInputStream(getClass.getClassLoader.getResourceAsStream(name))
      .mkString
}
