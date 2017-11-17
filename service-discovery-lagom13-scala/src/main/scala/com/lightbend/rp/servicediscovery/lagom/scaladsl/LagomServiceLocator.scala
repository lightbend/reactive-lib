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

package com.lightbend.rp.servicediscovery.lagom.scaladsl

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.client.CircuitBreakers
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.client.CircuitBreakingServiceLocator
import java.net.{ URI => JavaURI }

import com.lightbend.rp.servicediscovery.scaladsl.ServiceLocator

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls

class LagomServiceLocator(circuitBreakers: CircuitBreakers)(implicit as: ActorSystem, ec: ExecutionContext) extends CircuitBreakingServiceLocator(circuitBreakers)(ec) {

  override def locate(name: String, serviceCall: Descriptor.Call[_, _]): Future[Option[JavaURI]] =
    ServiceLocator.lookup(name)
}