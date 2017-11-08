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

package com.lightbend.rp

sealed trait Platform

case object Kubernetes extends Platform

object Platform {
  lazy val active: Option[Platform] = decode(Option(System.getenv("RP_PLATFORM")))

  private[rp] def decode(platform: Option[String]) = platform match {
    case Some("kubernetes") => Some(Kubernetes)
    case _                  => None
  }
}
