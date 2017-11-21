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

  def protocol(name: String): Option[SocketProtocol] = reader.protocol(name)

  private[common] class EnvironmentReader(environment: Map[String, String]) {
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

    def protocol(name: String): Option[SocketProtocol] =
      environment
        .get(assembleName(name, "PROTOCOL"))
        .flatMap {
          case "http" => Some(HttpSocketProtocol)
          case "tcp" => Some(TcpSocketProtocol)
          case "udp" => Some(UdpSocketProtocol)
          case _ => None
        }

    val all: Seq[String] =
      environment
        .getOrElse("RP_ENDPOINTS", "")
        .split(',')
        .toVector

    private def assembleName(name: String, suffix: String): String =
      s"RP_ENDPOINT_${normalizeEndpointName(name)}_$suffix"

    private def normalizeEndpointName(endpointName: String): String =
      endpointName
        .toUpperCase
        .map(c => if (ValidEndpointChars.contains(c)) c else '_')
        .mkString
  }
}
