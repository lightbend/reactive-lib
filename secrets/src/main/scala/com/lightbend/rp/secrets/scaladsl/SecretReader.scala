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

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import java.nio.file.{ Path, Paths }

import scala.concurrent._

object SecretReader {
  def get(name: String, key: String)(implicit as: ActorSystem, mat: ActorMaterializer): Future[Option[ByteString]] = {
    import as.dispatcher

    sys
      .env
      .get(envName(name, key))
      .map(data => Future.successful(Some(ByteString(data))))
      .getOrElse(
        FileIO.fromPath(filePath(name, key))
          .runFold(ByteString.empty)(_ ++ _)
          .map(Some(_))
          .recover { case _: Throwable => None })
  }

  private[secrets] def envName(namespace: String, name: String): String =
    s"RP_SECRETS_${namespace}_$name"
      .toUpperCase
      .map(c => if (c.isLetterOrDigit) c else '_')

  private[scaladsl] def filePath(name: String, key: String): Path =
    Paths
      .get("/rp")
      .resolve("secrets")
      .resolve(name)
      .resolve(key)
}
