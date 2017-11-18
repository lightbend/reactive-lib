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
import spray.json._

object JsonSupport extends DefaultJsonProtocol {
  implicit val uuidFormat: JsonFormat[UUID] =
    new JsonFormat[UUID] {
      def write(x: UUID): JsValue =
        JsString(x.toString)

      def read(value: JsValue): UUID =
        value match {
          case JsString(string) =>
            UUID.fromString(string)
          case v =>
            deserializationError(s"Unable to parse as UUID: $v")
        }
    }

  implicit val recordFormat: RootJsonFormat[Record] = jsonFormat5(Record.apply)

  implicit val registrationFormat: RootJsonFormat[Registration] = jsonFormat2(Registration.apply)

  implicit val refreshFormat: RootJsonFormat[RefreshResult] = jsonFormat4(RefreshResult.apply)
}
