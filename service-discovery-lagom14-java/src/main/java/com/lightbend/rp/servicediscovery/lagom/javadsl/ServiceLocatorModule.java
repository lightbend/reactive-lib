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

import com.lightbend.lagom.javadsl.api.ServiceLocator;
import play.api.*;
import play.api.inject.*;
import scala.collection.Seq;

public class ServiceLocatorModule extends Module {
    public ServiceLocatorModule() {}

    @Override
    public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
        return environment.mode().asJava() != play.Mode.PROD ? seq() : seq(
                bind(ServiceLocator.class).to(LagomServiceLocator.class)
        );
    }
}
