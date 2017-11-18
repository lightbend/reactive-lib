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

package com.lightbend.rp.akkaclusterbootstrap

import java.util.UUID

import akka.actor.FSM.StateTimeout
import akka.actor.{ ActorSystem, Address }
import akka.cluster.ClusterEvent.{ CurrentClusterState, InitialStateAsSnapshot, MemberJoined, MemberUp }
import akka.cluster.{ MemberStatus, TestMember, UniqueAddress }
import akka.pattern.ask
import akka.testkit.{ ImplicitSender, TestFSMRef, TestKit, TestProbe }
import akka.util.Timeout
import com.lightbend.rp.akkaclusterbootstrap.registrar._
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.collection.immutable.Seq

object ClusterManagerSpec {
  def config = ConfigFactory
    .parseString(
      s"""|
          |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())
    .resolve()
}

class ClusterManagerSpec extends TestKit(ActorSystem("akka-cluster-bootstrap", ClusterManagerSpec.config))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(1.second)

  val id = UUID.randomUUID()

  "ClusterManager" must {
    "wait to retry when registrar is in a holding period" in {
      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = TestFSMRef(new ClusterManager(id, testClient, testCluster))

      fsm.stateName shouldBe ClusterManager.Registering

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(None)

      fsm.stateName shouldBe ClusterManager.RegisteringBackoff
      fsm.stateData shouldBe ClusterManager.RegisteringData(1)

      fsm.stop()
    }

    "join self when only member and start refresh cycle" in {
      val expireAfterMs = 60000
      val refreshIntervalMs = 100

      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = TestFSMRef(new ClusterManager(id, testClient, testCluster))

      clusterProbe.expectMsg(
        TestAkkaCluster.Call(
          "subscribe",
          fsm.actorRef,
          InitialStateAsSnapshot,
          classOf[MemberJoined],
          classOf[MemberUp]))

      fsm.stateName shouldBe ClusterManager.Registering

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(Some(Record(id, "tcp://my-system@localhost:1234", Seq("tcp://my-system@localhost:1234"), refreshIntervalMs, expireAfterMs)))

      fsm.stateName shouldBe ClusterManager.Joining

      clusterProbe.expectMsg(TestAkkaCluster.Call("join", Address("tcp", "my-system", "localhost", 1234)))

      fsm ! MemberUp(
        TestMember(
          new UniqueAddress(Address("tcp", "my-system", "localhost", 1234), 0L)).copy(MemberStatus.Up))

      fsm.stateName shouldBe ClusterManager.Refreshing

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      fsm.stateName shouldBe ClusterManager.Refreshing

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      fsm.stateName shouldBe ClusterManager.Refreshing

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      fsm.stateName shouldBe ClusterManager.Refreshing

      fsm.stop()
    }

    "join others when not only member and start refresh cycle" in {
      val expireAfterMs = 60000
      val refreshIntervalMs = 100

      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = TestFSMRef(new ClusterManager(id, testClient, testCluster))

      clusterProbe.expectMsg(
        TestAkkaCluster.Call(
          "subscribe",
          fsm.actorRef,
          InitialStateAsSnapshot,
          classOf[MemberJoined],
          classOf[MemberUp]))

      fsm.stateName shouldBe ClusterManager.Registering

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(Some(Record(id, "tcp://my-system@localhost:1234", Seq("tcp://my-system@localhost:1234", "tcp://my-system@localhost:9999"), refreshIntervalMs, expireAfterMs)))

      fsm.stateName shouldBe ClusterManager.Joining

      clusterProbe.expectMsg(TestAkkaCluster.Call("join", Address("tcp", "my-system", "localhost", 9999)))

      fsm ! MemberUp(
        TestMember(
          new UniqueAddress(Address("tcp", "my-system", "localhost", 1234), 0L)).copy(MemberStatus.Up))

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      fsm.stop()
    }

    "join when given cluster membership and start refresh cycle" in {
      val expireAfterMs = 60000
      val refreshIntervalMs = 100

      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = TestFSMRef(new ClusterManager(id, testClient, testCluster))

      clusterProbe.expectMsg(
        TestAkkaCluster.Call(
          "subscribe",
          fsm.actorRef,
          InitialStateAsSnapshot,
          classOf[MemberJoined],
          classOf[MemberUp]))

      fsm.stateName shouldBe ClusterManager.Registering

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(Some(Record(id, "tcp://my-system@localhost:1234", Seq("tcp://my-system@localhost:1234", "tcp://my-system@localhost:9999"), refreshIntervalMs, expireAfterMs)))

      fsm.stateName shouldBe ClusterManager.Joining

      clusterProbe.expectMsg(TestAkkaCluster.Call("join", Address("tcp", "my-system", "localhost", 9999)))

      fsm ! CurrentClusterState(
        scala.collection.immutable.SortedSet(
          TestMember(new UniqueAddress(Address("tcp", "my-system", "localhost", 1234), 0L)).copy(MemberStatus.Up),
          TestMember(new UniqueAddress(Address("tcp", "my-system", "localhost", 9999), 0L)).copy(MemberStatus.Up)),
        Set.empty,
        Set.empty,
        None,
        Map.empty)

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set(Registration(id, "tcp://my-system@localhost:1234")), Set.empty, refreshIntervalMs, expireAfterMs))

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      fsm.stop()
    }

    "retry when failed to register" in {
      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = TestFSMRef(new ClusterManager(id, testClient, testCluster))

      fsm.stateName shouldBe ClusterManager.Registering

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(new RuntimeException)

      fsm.stateName shouldBe ClusterManager.RegisteringBackoff
      fsm.stateData shouldBe ClusterManager.RegisteringData(2)

      fsm ! StateTimeout

      fsm.stateName shouldBe ClusterManager.Registering
    }

    "remove registration when stopped" in {
      val expireAfterMs = 60000
      val refreshIntervalMs = 100

      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = TestFSMRef(new ClusterManager(id, testClient, testCluster))

      watch(fsm)

      clusterProbe.expectMsg(
        TestAkkaCluster.Call(
          "subscribe",
          fsm.actorRef,
          InitialStateAsSnapshot,
          classOf[MemberJoined],
          classOf[MemberUp]))

      fsm.stateName shouldBe ClusterManager.Registering

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(Some(Record(id, "tcp://my-system@localhost:1234", Seq("tcp://my-system@localhost:1234"), refreshIntervalMs, expireAfterMs)))

      fsm.stateName shouldBe ClusterManager.Joining

      clusterProbe.expectMsg(TestAkkaCluster.Call("join", Address("tcp", "my-system", "localhost", 1234)))

      fsm ! MemberUp(
        TestMember(
          new UniqueAddress(Address("tcp", "my-system", "localhost", 1234), 0L)).copy(MemberStatus.Up))

      fsm.stateName shouldBe ClusterManager.Refreshing

      fsm.stop()

      expectTerminated(fsm)

      clientProbe.expectMsg(TestClient.Call("remove", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))
    }

    "exit when a refresh is rejected" in {
      val expireAfterMs = 60000
      val refreshIntervalMs = 100

      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = watch(TestFSMRef(new ClusterManager(id, testClient, testCluster), "cluster-manager"))

      clusterProbe.expectMsg(
        TestAkkaCluster.Call(
          "subscribe",
          fsm.actorRef,
          InitialStateAsSnapshot,
          classOf[MemberJoined],
          classOf[MemberUp]))

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(Some(Record(id, "tcp://my-system@localhost:1234", Seq("tcp://my-system@localhost:1234"), refreshIntervalMs, expireAfterMs)))

      clusterProbe.expectMsg(TestAkkaCluster.Call("join", Address("tcp", "my-system", "localhost", 1234)))

      fsm ! MemberUp(
        TestMember(
          new UniqueAddress(Address("tcp", "my-system", "localhost", 1234), 0L)).copy(MemberStatus.Up))

      clientProbe.expectMsg(TestClient.Call("refresh", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(RefreshResult(Set.empty, Set(Registration(id, "tcp://my-system@localhost:1234")), refreshIntervalMs, expireAfterMs))

      clientProbe.expectMsg(TestClient.Call("remove", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      expectTerminated(fsm)
    }

    "exit when expired" in {
      val expireAfterMs = 200
      val refreshIntervalMs = 60000

      val clientProbe = TestProbe()
      val clusterProbe = TestProbe()

      val testClient = new TestClient(clientProbe.ref)
      val testCluster = new TestAkkaCluster(clusterProbe.ref, Address("tcp", "my-system", "localhost", 1234))
      val fsm = watch(TestFSMRef(new ClusterManager(id, testClient, testCluster), "cluster-manager"))

      clusterProbe.expectMsg(
        TestAkkaCluster.Call(
          "subscribe",
          fsm.actorRef,
          InitialStateAsSnapshot,
          classOf[MemberJoined],
          classOf[MemberUp]))

      clientProbe.expectMsg(TestClient.Call("register", "akka-cluster[my-system]", id, "tcp://my-system@localhost:1234"))

      clientProbe.reply(Some(Record(id, "tcp://my-system@localhost:1234", Seq("tcp://my-system@localhost:1234"), refreshIntervalMs, expireAfterMs)))

      clusterProbe.expectMsg(TestAkkaCluster.Call("join", Address("tcp", "my-system", "localhost", 1234)))

      fsm ! MemberUp(
        TestMember(
          new UniqueAddress(Address("tcp", "my-system", "localhost", 1234), 0L)).copy(MemberStatus.Up))

      expectTerminated(fsm)

      clientProbe.expectNoMsg()
    }
  }
}