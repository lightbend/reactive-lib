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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import PodList._

object JsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val portFormat: JsonFormat[Port] = jsonFormat2(Port)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container)
  implicit val specFormat: JsonFormat[Spec] = jsonFormat1(Spec)
  implicit val statusFormat: JsonFormat[Status] = jsonFormat1(Status)
  implicit val itemFormat: JsonFormat[Item] = jsonFormat2(Item)
  implicit val podListFormat: RootJsonFormat[PodList] = jsonFormat1(PodList.apply)
}
