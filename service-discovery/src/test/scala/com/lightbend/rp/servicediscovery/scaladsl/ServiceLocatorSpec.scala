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

import akka.actor.{ ActorRef, ActorSystem }
import akka.io.Dns
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import com.lightbend.rp.asyncdns.AsyncDnsResolver
import com.lightbend.rp.asyncdns.raw.SRVRecord
import com.lightbend.rp.common.{ Kubernetes, Platform }
import com.typesafe.config.ConfigFactory
import java.net.{ InetAddress, URI }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ AsyncWordSpecLike, BeforeAndAfterAll, Inside, Matchers }
import scala.collection.immutable.Seq

object ServiceLocatorSpec {
  def config = ConfigFactory
    .parseString(
      s"""|com.lightbend.platform-tooling.service-discovery {
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
  with BeforeAndAfterAll
  with ScalaFutures
  with Inside {

  import ServiceLocatorLike.{ AddressSelectionFirst, AddressSelectionRandom }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = Settings(system)

  "AddressSelectionFirst" should {
    "work for empty sequences" in {
      AddressSelectionFirst(Seq.empty) shouldBe None
    }

    "work for non-empty sequences" in {
      AddressSelectionFirst(
        Seq(Service("one", new URI("http://127.0.0.1:9000")))).contains(Service("one", new URI("http://127.0.0.1:9000"))) shouldBe true

      AddressSelectionFirst(
        Seq(
          Service("one1", new URI("http://127.0.0.1:9000")),
          Service("one2", new URI("http://127.0.0.1:9001")))).contains(Service("one1", new URI("http://127.0.0.1:9000"))) shouldBe true
    }
  }

  "AddressSelectionRandom" should {
    "work for empty sequences" in {
      AddressSelectionRandom(Seq.empty) shouldBe None
    }

    "work for non-empty sequences" in {
      AddressSelectionRandom(
        Seq(Service("test", new URI("http://127.0.0.1:9000")))).contains(Service("test", new URI("http://127.0.0.1:9000"))) shouldBe true

      AddressSelectionRandom(
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
        .lookupOne("test", "my-lookup", _.headOption)
        .map(_.contains(Service("has-two", new URI("http://127.0.0.1:8000"))) shouldBe true)
    }

    "perform DNS SRV resolution during lookup" in {
      val mockDnsResolver = TestProbe()
      val serviceLocator = createServiceLocator(mockDnsResolver = Some(mockDnsResolver.ref))
      val result = serviceLocator.lookup("chirper", "friendservice", "friendlookup")

      mockDnsResolver.expectMsg(Dns.Resolve("_friendlookup._tcp.friendservice.chirper.svc.cluster.local"))
      mockDnsResolver.reply(AsyncDnsResolver.SrvResolved("_friendlookup._tcp.friendservice.chirper.svc.cluster.local", Seq(
        SRVRecord("_friendlookup._tcp.friendservice.chirper.svc.cluster.local", 100, 1, 1, 4568, "host1.domain"))))

      mockDnsResolver.expectMsg(Dns.Resolve("host1.domain"))
      mockDnsResolver.reply(Dns.Resolved("host1.domain1.", Seq(InetAddress.getByName("10.0.12.5"))))

      inside(result.futureValue) {
        case Vector(s: Service) =>
          s.hostname shouldBe "host1.domain"
          s.uri.getScheme shouldBe "tcp"
          s.uri.getUserInfo shouldBe null
          s.uri.getHost shouldBe "10.0.12.5"
          s.uri.getPort shouldBe 4568
          s.uri.getPath shouldBe ""
          s.uri.getQuery shouldBe null
          s.uri.getFragment shouldBe null
      }
    }
  }

  "ServiceLocator.translateName" should {
    "translate name with namespace + name + endpoint" in {
      val serviceLocator = createServiceLocator()
      val result = serviceLocator.translateName(Some("chirper"), "friendservice", "api")
      result shouldBe "_api._tcp.friendservice.chirper.svc.cluster.local"
    }

    "translate name with namespace from env + name + endpoint" in {
      val serviceLocator = createServiceLocator(Map("RP_NAMESPACE" -> "cake"))
      val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
      result shouldBe "_api._tcp.friendservice.cake.svc.cluster.local"
    }

    "translate name with default namespace + name + endpoint" in {
      val serviceLocator = createServiceLocator()
      val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
      result shouldBe "_api._tcp.friendservice.default.svc.cluster.local"
    }

    "not translate name containing DNS character" in {
      val serviceLocator = createServiceLocator()
      val result = serviceLocator.translateName(namespace = None, "_native._tcp.cassandra.default.svc.cluster.local", "")
      result shouldBe "_native._tcp.cassandra.default.svc.cluster.local"

    }
  }

  private def createServiceLocator(testEnv: Map[String, String] = Map.empty, mockDnsResolver: Option[ActorRef] = None): ServiceLocatorLike =
    new ServiceLocatorLike {
      def dnsResolver(implicit as: ActorSystem): ActorRef =
        mockDnsResolver.getOrElse {
          throw new IllegalArgumentException("Mock DNS resolver should not be required in this test!!!")
        }

      def env: Map[String, String] = testEnv
      override def targetRuntime: Option[Platform] = Some(Kubernetes)
    }

}