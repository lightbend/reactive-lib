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
import akka.testkit.{ImplicitSender, TestKit}
import com.lightbend.rp.servicediscovery.scaladsl.Settings
import com.typesafe.config.ConfigFactory
import java.net.URI
import java.util.Optional
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}
import scala.collection.JavaConversions._
import scala.collection.immutable.Seq

object ServiceLocatorSpec {
  def config = ConfigFactory
    .parseString(
      s"""|rp.service-discovery {
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
  with AsyncWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = Settings(system)

  "addressSelectionFirst" should {
    "work for empty lists" in {
      ServiceLocator.addressSelectionFirst.select(Seq()) shouldBe Optional.empty[URI]()
    }

    "work for non-empty lists" in {
      ServiceLocator.addressSelectionFirst.select(
        Seq(
          new URI("http://127.0.0.1:9000"),
          new URI("http://127.0.0.1:9001"))).get() shouldBe new URI("http://127.0.0.1:9000")
    }
  }

  "addressSelectionRandom" should {
    "work for empty lists" in {
      ServiceLocator.addressSelectionFirst.select(Seq()) shouldBe Optional.empty[URI]()
    }

    "work for non-empty lists" in {
      ServiceLocator.addressSelectionFirst.select(Seq(new URI("http://127.0.0.1:9000"))).isPresent shouldBe true
    }
  }
}