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

package com.lightbend.rp.servicediscovery.scaladsl

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.ConfigFactory
import java.net.URI
import org.scalatest.{ AsyncWordSpecLike, BeforeAndAfterAll, Matchers }
import scala.collection.immutable.Seq

object ServiceLocatorSpec {
  def config = ConfigFactory
    .parseString(
      s"""|rp.service-discovery {
          |  external-service-addresses {
          |    "has-one" = ["http://127.0.0.1:9000"]
          |    "has-two/some-endpoint" = ["http://127.0.0.1:8000", "http://127.0.0.1:8001"]
          |    "test/my-lookup" = ["has-two/some-endpoint"]
          |
          |    "pointer-one" = ["pointer-two"]
          |    "pointer-two" = ["pointer-three"]
          |    "pointer-three" = ["pointer-four"]
          |    "pointer-four" = ["pointer-five"]
          |
          |    "no-pointer-one" = ["tcp://hello"]
          |  }
          |
          |  external-service-address-limit = 2
          |}
          |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())
}

class ServiceLocatorSpec extends TestKit(ActorSystem("service-locator", ServiceLocatorSpec.config))
  with ImplicitSender
  with AsyncWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = Settings(system)

  "AddressSelectionFirst" should {
    "work for empty sequences" in {
      ServiceLocator.AddressSelectionFirst(Seq.empty) shouldBe None
    }

    "work for non-empty sequences" in {
      ServiceLocator.AddressSelectionFirst(
        Seq(Service("one", new URI("http://127.0.0.1:9000")))).contains(Service("one", new URI("http://127.0.0.1:9000"))) shouldBe true

      ServiceLocator.AddressSelectionFirst(
        Seq(
          Service("one1", new URI("http://127.0.0.1:9000")),
          Service("one2", new URI("http://127.0.0.1:9001")))).contains(Service("one1", new URI("http://127.0.0.1:9000"))) shouldBe true
    }
  }

  "AddressSelectionRandom" should {
    "work for empty sequences" in {
      ServiceLocator.AddressSelectionRandom(Seq.empty) shouldBe None
    }

    "work for non-empty sequences" in {
      ServiceLocator.AddressSelectionRandom(
        Seq(Service("test", new URI("http://127.0.0.1:9000")))).contains(Service("test", new URI("http://127.0.0.1:9000"))) shouldBe true

      ServiceLocator.AddressSelectionRandom(
        Seq(Service("test1", new URI("http://127.0.0.1:9000")), Service("test2", new URI("http://127.0.0.1:9001")))).nonEmpty shouldBe true
    }
  }

  "ServiceLocator" should {
    "resolve external services correctly (one)" in {
      ServiceLocator
        .lookup("has-one")
        .map(_.contains(Service("has-one", new URI("http://127.0.0.1:9000"))) shouldBe true)
    }

    "resolve external services correctly (many #1)" in {
      ServiceLocator
        .lookupOne("has-two", "some-endpoint", _.headOption)
        .map(_.contains(Service("has-two", new URI("http://127.0.0.1:8000"))) shouldBe true)
    }

    "resolve external services correctly (many #2)" in {
      ServiceLocator
        .lookupOne("has-two", "some-endpoint", _.lastOption)
        .map(_.contains(Service("has-two", new URI("http://127.0.0.1:8001"))) shouldBe true)
    }

    "recursively resolve (upto a limit) if scheme is missing" in {
      ServiceLocator
        .lookupOne("pointer-one")
        .map(_.contains(Service("pointer-three", new URI("pointer-four"))) shouldBe true)
    }

    "recursively resolve name & endpoint" in {
      ServiceLocator
        .lookupOne("test", "my-lookup", _.headOption).foreach(println)

      ServiceLocator
        .lookupOne("test", "my-lookup", _.headOption)
        .map(_.contains(Service("has-two", new URI("http://127.0.0.1:8000"))) shouldBe true)
    }
  }
}