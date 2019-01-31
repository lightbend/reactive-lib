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

package com.lightbend.rp.secrets.javadsl;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.util.ByteString;
import com.lightbend.rp.secrets.scaladsl.SecretReader$;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.OptionConverters;

public class SecretReader {
    /**
     * @deprecated  As of 1.7.0. Read from file /rp/secrets/%name%/%key% where %name% is transformed to lowercase, and '-' for non-alphanum.
     */
    @Deprecated
    public static CompletionStage<Optional<ByteString>> get(String name, String key, ActorSystem actorSystem, ActorMaterializer mat) {
        return FutureConverters.toJava(
                SecretReader$
                        .MODULE$
                        .get(name, key, actorSystem, mat)
        ).thenApply(OptionConverters::toJava);
    }
}
