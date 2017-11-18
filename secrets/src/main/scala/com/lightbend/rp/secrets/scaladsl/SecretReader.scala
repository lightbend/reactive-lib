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

package com.lightbend.rp.secrets.scaladsl

object SecretReader {
  def apply(namespace: String, name: String): Option[String] =
    Option(System.getenv(envName(namespace, name)))

  private[secrets] def envName(namespace: String, name: String): String =
    s"RP_SECRETS_${namespace}_$name"
      .toUpperCase
      .map(c => if (c.isLetterOrDigit) c else '_')
}
