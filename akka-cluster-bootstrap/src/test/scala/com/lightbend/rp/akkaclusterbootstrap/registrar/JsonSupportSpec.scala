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

package com.lightbend.rp.akkaclusterbootstrap.registrar

import java.util.UUID

import com.lightbend.rp.akkaclusterbootstrap.registrar.JsonSupport._
import org.scalatest.{ Matchers, WordSpec }
import spray.json._

class JsonSupportSpec extends WordSpec
  with Matchers {

  val idOne = new UUID(0, 1)
  val idTwo = new UUID(0, 2)
  val idThree = new UUID(0, 3)

  "JsonSupportSpec" should {
    "Encode Record" in {
      Record(idOne, "hello", Vector("foo", "bar"), 10000L, 60000L).toJson.compactPrint shouldEqual """{"name":"hello","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000001","members":["foo","bar"]}"""

      Record(idTwo, "", Vector.empty, 10000L, 60000L).toJson.compactPrint shouldEqual """{"name":"","expireAfter":60000,"refreshInterval":10000,"id":"00000000-0000-0000-0000-000000000002","members":[]}"""

      assertThrows[IllegalArgumentException] {
        Record(idThree, null, Vector.empty, 10000L, 60000L).toJson.compactPrint
      }
    }

    "Encode RefreshResult" in {
      RefreshResult(Set(Registration(idOne, "one")), Set(Registration(idTwo, "two")), 100, 6000).toJson.compactPrint shouldEqual """{"accepted":[{"id":"00000000-0000-0000-0000-000000000001","name":"one"}],"rejected":[{"id":"00000000-0000-0000-0000-000000000002","name":"two"}],"refreshInterval":100,"expireAfter":6000}"""
    }

    "Encode Registration" in {
      Registration(idOne, "one").toJson.compactPrint shouldEqual """{"id":"00000000-0000-0000-0000-000000000001","name":"one"}"""
    }
  }
}
