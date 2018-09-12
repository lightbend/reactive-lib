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

package com.lightbend.rp.servicediscovery.javadsl

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.lightbend.rp.servicediscovery.scaladsl.{ Service, Settings }
import com.typesafe.config.ConfigFactory
import java.net.URI
import java.util.Optional
import org.scalatest.{ AsyncFunSuiteLike, BeforeAndAfterAll, DiagrammedAssertions }
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

object ServiceLocatorSpec {
  def config = ConfigFactory
    .parseString(
      s"""|com.lightbend.platform-tooling.service-discovery {
          |  external-service-addresses {
          |    "has-one" = ["http://127.0.0.1:9000"]
          |    "has-two" = ["http://127.0.0.1:8000", "http://127.0.0.1:8001"]
          |  }
          |}
          |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())
}

class ServiceLocatorSpec extends TestKit(ActorSystem("service-locator", ServiceLocatorSpec.config))
  with ImplicitSender
  with AsyncFunSuiteLike
  with DiagrammedAssertions
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = Settings(system)

  test("addressSelectionFirst should work for empty lists") {
    assert(ServiceLocator.addressSelectionFirst.select(Seq().asJava) ===
      Optional.empty[URI]())
  }

  test("addressSelectionFirst should work for non-empty lists") {
    assert(ServiceLocator.addressSelectionFirst.select(
      Seq(
        Service("myservice.com", new URI("http://127.0.0.1:9000")),
        Service("myotherservice.com", new URI("http://127.0.0.1:9001"))).asJava).get() ===
      Service("myservice.com", new URI("http://127.0.0.1:9000")))
  }

  test("addressSelectionRandom should work for empty lists") {
    assert(ServiceLocator.addressSelectionFirst.select(Seq().asJava) === Optional.empty[URI]())
  }

  test("addressSelectionRandom should work for non-empty lists") {
    assert(ServiceLocator.addressSelectionFirst.select(Seq(Service("hello", new URI("http://127.0.0.1:9000"))).asJava).isPresent)
  }
}
