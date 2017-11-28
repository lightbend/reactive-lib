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

package com.lightbend.rp.servicediscovery.lagom.javadsl;

import akka.actor.ActorSystem;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel;
import com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator;
import com.lightbend.rp.servicediscovery.scaladsl.Service;
import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class LagomServiceLocator extends CircuitBreakingServiceLocator {
    private final ActorSystem actorSystem;

    @Inject
    public LagomServiceLocator(ActorSystem actorSystem, CircuitBreakersPanel circuitBreakersPanel) {
        super(circuitBreakersPanel);

        this.actorSystem = actorSystem;
    }

    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
        return com.lightbend.rp.servicediscovery.javadsl.ServiceLocator
                .lookupOne(name, "lagom-http-api", actorSystem)
                .thenCompose(service -> service.isPresent() ? CompletableFuture.completedFuture(service) : com.lightbend.rp.servicediscovery.javadsl.ServiceLocator.lookupOne(name, actorSystem))
                .thenApply(service -> service.map(Service::uri));
    }
}
