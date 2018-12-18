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
import akka.io.dns.{ DnsProtocol, ARecord, SRVRecord }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import com.lightbend.rp.common.{ Kubernetes, Mesos, Platform }
import com.typesafe.config.ConfigFactory
import java.net.{ InetAddress, URI }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ AsyncFunSuiteLike, BeforeAndAfterAll, Inside, DiagrammedAssertions }
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
          |    "elastic-search-ext" = ["_http._tcp.elasticsearch.test.svc.cluster.local"]
          |  }
          |
          |  external-service-address-limit = 2
          |}
          |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())
}

class ServiceLocatorSpec extends TestKit(ActorSystem("service-locator", ServiceLocatorSpec.config))
  with ImplicitSender
  with AsyncFunSuiteLike
  with DiagrammedAssertions
  with BeforeAndAfterAll
  with ScalaFutures
  with Inside {

  import ServiceLocatorLike.{ AddressSelectionFirst, AddressSelectionRandom }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val settings = Settings(system)

  test("AddressSelectionFirst should work for empty sequences") {
    assert(AddressSelectionFirst(Seq.empty) === None)
  }

  test("AddressSelectionFirst should work for non-empty sequences") {
    assert(AddressSelectionFirst(
      Seq(Service("one", new URI("http://127.0.0.1:9000")))).contains(Service("one", new URI("http://127.0.0.1:9000"))))

    assert(AddressSelectionFirst(
      Seq(
        Service("one1", new URI("http://127.0.0.1:9000")),
        Service("one2", new URI("http://127.0.0.1:9001")))).contains(Service("one1", new URI("http://127.0.0.1:9000"))))
  }

  test("AddressSelectionRandom should work for empty sequences") {
    assert(AddressSelectionRandom(Seq.empty) == None)
  }

  test("AddressSelectionRandom should work for non-empty sequences") {
    assert(AddressSelectionRandom(
      Seq(Service("test", new URI("http://127.0.0.1:9000")))).contains(Service("test", new URI("http://127.0.0.1:9000"))))

    assert(AddressSelectionRandom(
      Seq(Service("test1", new URI("http://127.0.0.1:9000")), Service("test2", new URI("http://127.0.0.1:9001")))).nonEmpty)
  }

  test("ServiceLocator should resolve external services correctly (one)") {
    ServiceLocator
      .lookup("has-one")
      .map(x =>
        assert(x.contains(Service("has-one", new URI("http://127.0.0.1:9000")))))
  }

  test("ServiceLocator should resolve external services correctly (many #1)") {
    ServiceLocator
      .lookupOne("has-two", "some-endpoint", _.headOption)
      .map(x =>
        assert(x.contains(Service("has-two", new URI("http://127.0.0.1:8000")))))
  }

  test("ServiceLocator should resolve external services correctly (many #2)") {
    ServiceLocator
      .lookupOne("has-two", "some-endpoint", _.lastOption)
      .map(x =>
        assert(x.contains(Service("has-two", new URI("http://127.0.0.1:8001")))))
  }

  test("ServiceLocator should recursively resolve (upto a limit) if scheme is missing") {
    ServiceLocator
      .lookupOne("pointer-one")
      .map(x =>
        assert(x.contains(Service("pointer-three", new URI("pointer-four")))))
  }

  test("ServiceLocator should recursively resolve name & endpoint") {
    ServiceLocator
      .lookupOne("test", "my-lookup", _.headOption)
      .map(x =>
        assert(x.contains(Service("has-two", new URI("http://127.0.0.1:8000")))))
  }

  test("In Kubernetes, ServiceLocator should perform DNS SRV resolution during lookup") {
    val mockDnsResolver = TestProbe()
    val serviceLocator = createServiceLocator(Kubernetes, mockDnsResolver = Some(mockDnsResolver.ref))
    val result = serviceLocator.lookup("chirper", "friendservice", "friendlookup")

    mockDnsResolver.expectMsg(DnsProtocol.resolve("_friendlookup._tcp.friendservice.chirper.svc.cluster.local", DnsProtocol.Srv))
    mockDnsResolver.reply(DnsProtocol.Resolved("_friendlookup._tcp.friendservice.chirper.svc.cluster.local", Seq(
      new SRVRecord("_friendlookup._tcp.friendservice.chirper.svc.cluster.local", 100, 1, 1, 4568, "host1.domain"))))

    mockDnsResolver.expectMsg(DnsProtocol.resolve("host1.domain", DnsProtocol.Ip(ipv4 = true, ipv6 = false)))
    mockDnsResolver.reply(DnsProtocol.Resolved("host1.domain1.", Seq(
      new ARecord("host1.domain1.", 86400, InetAddress.getByName("10.0.12.5")))))

    inside(result.futureValue) {
      case Vector(s: Service) =>
        assert(s.hostname === "host1.domain")
        assert(s.uri.getScheme === "tcp")
        assert(s.uri.getUserInfo === null)
        assert(s.uri.getHost === "10.0.12.5")
        assert(s.uri.getPort === 4568)
        assert(s.uri.getPath === "")
        assert(s.uri.getQuery === null)
        assert(s.uri.getFragment === null)
    }
  }

  test("In Kubernetes, ServiceLocator should perform A record resolution when the name doesn't start with underscore") {
    val mockDnsResolver = TestProbe()
    val serviceLocator = createServiceLocator(Kubernetes, mockDnsResolver = Some(mockDnsResolver.ref))
    val result = serviceLocator.lookup("host1.domain")

    mockDnsResolver.expectMsg(DnsProtocol.resolve("host1.domain", DnsProtocol.Ip(ipv4 = true, ipv6 = false)))
    mockDnsResolver.reply(DnsProtocol.Resolved("host1.domain1.", Seq(
      new ARecord("host1.domain1.", 86400, InetAddress.getByName("10.0.12.5")))))

    inside(result.futureValue) {
      case Vector(s: Service) =>
        assert(s.hostname === "host1.domain")
        assert(Option(s.uri.getScheme) === None)
        assert(Option(s.uri.getUserInfo) === None)
        assert(s.uri.getHost === "10.0.12.5")
        assert(s.uri.getPort === -1)
        assert(s.uri.getPath === "")
        assert(Option(s.uri.getQuery) === None)
        assert(Option(s.uri.getFragment) === None)
    }
  }

  test("In Kubernetes, ServiceLocator.translateName should translate name with namespace + name + endpoint") {
    val serviceLocator = createServiceLocator(Kubernetes)
    val result = serviceLocator.translateName(Some("chirper"), "friendservice", "api")
    assert(result === "_api._tcp.friendservice.chirper.svc.cluster.local")
  }

  test("In Kubernetes, ServiceLocator.translateName should translate name with namespace from env + name + endpoint") {
    val serviceLocator = createServiceLocator(Kubernetes, Map("RP_NAMESPACE" -> "cake"))
    val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
    assert(result === "_api._tcp.friendservice.cake.svc.cluster.local")
  }

  test("In Kubernetes, ServiceLocator.translateName should translate name with namespace from env + name + endpoint + suffix") {
    val serviceLocator = createServiceLocator(Kubernetes, Map("RP_NAMESPACE" -> "cake", "RP_KUBERNETES_CLUSTER_SUFFIX" -> "new.kube"))
    val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
    assert(result === "_api._tcp.friendservice.cake.svc.new.kube")
  }

  test("In Kubernetes, ServiceLocator.translateName should translate name with default namespace + name + endpoint") {
    val serviceLocator = createServiceLocator(Kubernetes)
    val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
    assert(result === "_api._tcp.friendservice.default.svc.cluster.local")
  }

  test("In Kubernetes, ServiceLocator.translateName should not translate name containing DNS character") {
    val serviceLocator = createServiceLocator(Kubernetes)
    val result = serviceLocator.translateName(namespace = None, "_native._tcp.cassandra.default.svc.cluster.local", "")
    assert(result === "_native._tcp.cassandra.default.svc.cluster.local")
  }

  test("In Mesos, ServiceLocator should perform DNS SRV resolution during lookup") {
    val mockDnsResolver = TestProbe()
    val serviceLocator = createServiceLocator(Mesos, mockDnsResolver = Some(mockDnsResolver.ref))
    val result = serviceLocator.lookup("chirper", "friendservice", "friendlookup")

    mockDnsResolver.expectMsg(DnsProtocol.resolve("_friendlookup._friendservice-chirper._tcp.marathon.mesos", DnsProtocol.Srv))
    mockDnsResolver.reply(DnsProtocol.Resolved("_friendlookup._friendlookup-chirper._tcp.marathon.mesos", Seq(
      new SRVRecord("_friendlookup._friendservice-chirper._tcp.marathon.mesos", 100, 1, 1, 4568, "host1.domain"))))

    mockDnsResolver.expectMsg(DnsProtocol.resolve("host1.domain", DnsProtocol.Ip(ipv4 = true, ipv6 = false)))
    mockDnsResolver.reply(DnsProtocol.Resolved("host1.domain1.", Seq(new ARecord("host1.domain1.", 86400, InetAddress.getByName("10.0.12.5")))))

    inside(result.futureValue) {
      case Vector(s: Service) =>
        assert(s.hostname === "host1.domain")
        assert(s.uri.getScheme === "tcp")
        assert(s.uri.getUserInfo === null)
        assert(s.uri.getHost === "10.0.12.5")
        assert(s.uri.getPort === 4568)
        assert(s.uri.getPath === "")
        assert(s.uri.getQuery === null)
        assert(s.uri.getFragment === null)
    }
  }

  test("In Mesos, ServiceLocator.translateName should translate name with namespace + name + endpoint") {
    val serviceLocator = createServiceLocator(Mesos)
    val result = serviceLocator.translateName(Some("chirper"), "friendservice", "api")
    assert(result === "_api._friendservice-chirper._tcp.marathon.mesos")
  }

  test("In Mesos, ServiceLocator.translateName should translate name with namespace from env + name + endpoint") {
    val serviceLocator = createServiceLocator(Mesos, Map("RP_NAMESPACE" -> "cake"))
    val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
    assert(result === "_api._friendservice-cake._tcp.marathon.mesos")
  }

  test("In Mesos, ServiceLocator.translateName should translate name with default namespace + name + endpoint") {
    val serviceLocator = createServiceLocator(Mesos)
    val result = serviceLocator.translateName(namespace = None, "friendservice", "api")
    assert(result === "_api._friendservice._tcp.marathon.mesos")
  }

  test("In Mesos, ServiceLocator.translateName should not translate name containing DNS character") {
    val serviceLocator = createServiceLocator(Mesos)
    val result = serviceLocator.translateName(namespace = None, "_native._tcp.cassandra.default.svc.cluster.local", "")
    assert(result === "_native._tcp.cassandra.default.svc.cluster.local")
  }

  test("In Kubernetes, ServiceLocator.lookupOne should cascade to DNS SRV resolution and make an http:// URI") {
    val mockDnsResolver = TestProbe()
    val serviceLocator = createServiceLocator(Kubernetes, mockDnsResolver = Some(mockDnsResolver.ref))
    val result = serviceLocator.lookupOne("elastic-search-ext")

    mockDnsResolver.expectMsg(DnsProtocol.resolve("_http._tcp.elasticsearch.test.svc.cluster.local", DnsProtocol.Srv))
    mockDnsResolver.reply(DnsProtocol.Resolved("_http._tcp.elasticsearch.default.test.cluster.local", Seq(
      new SRVRecord("_http._tcp.elasticsearch.default.test.cluster.local", 100, 1, 1, 4568, "host1.domain"))))

    mockDnsResolver.expectMsg(DnsProtocol.resolve("host1.domain", DnsProtocol.Ip(ipv4 = true, ipv6 = false)))
    mockDnsResolver.reply(DnsProtocol.Resolved("host1.domain1.", Seq(
      new ARecord("host1.domain1.", 86400, InetAddress.getByName("10.0.12.5")))))

    inside(result.futureValue) {
      case Some(s: Service) =>
        assert(s.hostname === "host1.domain")
        assert(s.uri.getScheme === "http")
        assert(s.uri.getUserInfo === null)
        assert(s.uri.getHost === "10.0.12.5")
        assert(s.uri.getPort === 4568)
        assert(s.uri.getPath === "")
        assert(s.uri.getQuery === null)
        assert(s.uri.getFragment === null)
    }
  }

  private def createServiceLocator(platform: Platform, testEnv: Map[String, String] = Map.empty, mockDnsResolver: Option[ActorRef] = None): ServiceLocatorLike =
    new ServiceLocatorLike {
      def dnsResolver(implicit as: ActorSystem): ActorRef =
        mockDnsResolver.getOrElse {
          throw new IllegalArgumentException("Mock DNS resolver should not be required in this test!!!")
        }

      def env: Map[String, String] = testEnv

      override def targetRuntime: Option[Platform] = Some(platform)
    }
}
