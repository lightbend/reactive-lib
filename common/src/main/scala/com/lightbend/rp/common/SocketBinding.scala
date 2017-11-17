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

import scala.collection.immutable.Seq
import scala.collection.JavaConverters._

object SocketBinding {
  private val reader = new EnvironmentReader(sys.env)

  def bindHost(name: String, default: String): String = reader.bindHost(name, default)

  def bindPort(name: String, default: Int): Int = reader.bindPort(name, default)

  def host(name: String, default: String): String = reader.host(name, default)

  def port(name: String, default: Int): Int = reader.port(name, default)

  private[common] class EnvironmentReader(environment: Map[String, String]) {
    private val VersionSeparator = "-v"

    private val ValidEndpointChars =
      (('0' to '9') ++ ('A' to 'Z') ++ Seq('_', '-')).toSet

    def bindHost(name: String, default: String): String =
      environment
        .getOrElse(
          assembleName(name, "BIND_HOST"),
          host(name, default))

    def bindPort(name: String, default: Int): Int =
      environment
        .get(assembleName(name, "BIND_PORT"))
        .map(_.toInt)
        .getOrElse(port(name, default))

    def host(name: String, default: String): String =
      environment.getOrElse(assembleName(name, "HOST"), default)

    def port(name: String, default: Int): Int =
      environment.get(assembleName(name, "PORT")).fold(default)(_.toInt)

    val all: Seq[String] =
      environment
        .getOrElse("RP_ENDPOINTS", "")
        .split(',')
        .toVector

    val namesAndVersions: Seq[(String, Option[String])] =
      all.map { name =>
        val parts = name
          .reverse
          .split(VersionSeparator.toUpperCase.reverse, 2)
          .map(_.reverse)

        if (parts.length == 2)
          parts(1) -> Some(parts(0))
        else
          parts(0) -> None
      }

    private def assembleName(name: String, suffix: String): String = {
      val normalized = normalizeEndpointName(name)

      val fullName =
        if (all.contains(normalized))
          normalized
        else
          namesAndVersions
            .find(_._1 == normalized)
            .map {
              case (n, Some(v)) => s"$n${VersionSeparator.toUpperCase}$v"
              case (n, None) => n
            }
            .getOrElse(normalized)

      s"RP_ENDPOINT_${fullName}_$suffix"
    }

    private def normalizeEndpointName(endpointName: String): String =
      endpointName
        .toUpperCase
        .map(c => if (ValidEndpointChars.contains(c)) c else '_')
        .mkString
  }
}
