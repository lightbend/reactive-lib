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

package com.lightbend.rp.servicediscovery.javadsl;

import java.net.URI;
import java.util.Optional;

import akka.actor.ActorSystem;
import com.lightbend.rp.servicediscovery.scaladsl.ServiceLocator$;
import scala.runtime.AbstractFunction1;
import scala.Option;
import scala.concurrent.Future;

public final class ServiceLocator {
    public static Future<Optional<URI>> lookup(String name, ActorSystem actorSystem) {
        return
                ServiceLocator$
                        .MODULE$
                        .lookup(name, actorSystem)
                        .map(
                                new AbstractFunction1<Option<URI>, Optional<URI>>() {
                                    @Override
                                    public Optional<URI> apply(Option<URI> value) {
                                        return value.isDefined() ? Optional.of(value.get()) : Optional.empty();
                                    }
                                },

                                actorSystem.dispatcher()
                        );

    }
}
